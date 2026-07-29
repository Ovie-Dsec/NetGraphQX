package com.netgraphqx.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.netgraphqx.model.ApTelemetryTick
import com.netgraphqx.model.ApStatus
import com.netgraphqx.model.TelemetryError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress

/**
 * Real WiFi telemetry engine.
 *
 * Reads live connection info from [WifiManager] and performs real ICMP pings
 * against the default gateway. Emits [ApTelemetryTick] at roughly 1-second
 * intervals.
 *
 * **Null-safe**: If the device has no WiFi hardware or permissions are missing,
 * the engine degrades gracefully without crashing.
 *
 * Permission notes (API 33+):
 * - [android.Manifest.permission.NEARBY_WIFI_DEVICES] is required for SSID/BSSID
 *   and is auto-granted at install time on API 33+.
 * - On API 32 and below the app must hold
 *   [android.Manifest.permission.ACCESS_FINE_LOCATION].
 * - If required permissions are missing the engine returns "No Permission" /
 *   [ApStatus.DOWN] for the affected fields.
 */
class TelemetryEngine(private val context: Context) {

    // WifiManager is null on devices without WiFi hardware (some tablets, emulator)
    @Suppress("DEPRECATION")
    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /** Emitted whenever a permanent error is detected that warrants a dialog. */
    private val _errorEvent = MutableStateFlow<TelemetryError?>(null)

    /**
     * Observable stream of user-facing errors.
     *
     * Emits [TelemetryError] when a permanent issue is detected (no WiFi
     * hardware, no permission, no connection) and `null` after the error
     * has been acknowledged via [dismissError].
     */
    val errorEvent: StateFlow<TelemetryError?> = _errorEvent.asStateFlow()

    /** Clear the current error (call when the user taps OK on the dialog). */
    fun dismissError() {
        _errorEvent.value = null
    }

    /**
     * Infinite flow of AP telemetry ticks.
     *
     * Each tick reads WiFi connection info (instant) and performs a real ICMP
     * ping to the default gateway. Every exception is caught so the flow never
     * terminates — on error it emits a DOWN tick with a descriptive message.
     *
     * Permanent errors (no hardware, no permission, disconnected) are also
     * emitted to [errorEvent] so the UI can show a dialog.
     */
    fun observeTelemetry(): Flow<ApTelemetryTick> = flow {
        // Emit initial error state on first tick
        checkAndEmitError()

        while (true) {
            try {
                val tick = measureTick()
                emit(tick)
                // Update error state based on current conditions
                checkAndEmitError()
            } catch (e: Exception) {
                _errorEvent.value = TelemetryError.Generic(
                    "An unexpected error occurred:\n${e.message?.take(120) ?: "Unknown error"}"
                )
                emit(
                    ApTelemetryTick(
                        ssid = "<error>",
                        bssid = "",
                        ipAddress = "",
                        signalStrength = 0f,
                        latencyMs = 0f,
                        reachable = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            delay(1000L)
        }
    }

    /**
     * Returns the SSID of the currently connected WiFi, or a fallback string.
     */
    fun getCurrentSsid(): String {
        val wm = wifiManager ?: return "<no WiFi hardware>"
        return try {
            val info = wm.connectionInfo ?: return "<no WiFi>"
            val raw = info.ssid ?: return "<unknown>"
            if (raw == "<unknown ssid>" || raw == "0x") return "<permission required>"
            raw.removeSurrounding("\"")
        } catch (_: Exception) {
            "<error>"
        }
    }

    /**
     * Returns the gateway IP as a dotted string, or null if not available.
     */
    @Suppress("DEPRECATION")
    fun getGatewayIp(): String? {
        val wm = wifiManager ?: return null
        return try {
            val dhcpInfo = wm.dhcpInfo ?: return null
            if (dhcpInfo.gateway == 0) return null
            ipIntToString(dhcpInfo.gateway)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Force a one-shot ping check returning (reachable, latencyMs).
     */
    suspend fun pingGateway(): Pair<Boolean, Float> = withContext(Dispatchers.IO) {
        val gateway = getGatewayIp() ?: return@withContext false to 0f
        try {
            executePingOnce(gateway, timeoutSec = 3)
        } catch (_: Exception) {
            false to 0f
        }
    }

    /**
     * No-op: switching simulated APs no longer applies.
     */
    fun switchAp(index: Int) { /* real WiFi — no-op */ }

    /**
     * No-op: simulation state no longer applies.
     */
    fun reset() { /* real WiFi — no-op */ }

    // ── Error detection ──────────────────────────────────────────────

    /**
     * Check current conditions and emit an error event if a permanent
     * problem exists. If the condition recovers, existing errors are
     * NOT auto-cleared — the user must tap OK — because the initial
     * error happened during this session and remains informative.
     *
     * Priority: NoWiFiHardware > PermissionDenied > NotConnected
     */
    @Suppress("DEPRECATION")
    private fun checkAndEmitError() {
        // Don't overwrite an existing error that the user hasn't dismissed
        if (_errorEvent.value != null) return

        val error = when {
            wifiManager == null -> TelemetryError.NoWifiHardware
            else -> {
                val info = try {
                    wifiManager?.connectionInfo
                } catch (_: SecurityException) { null }
                when {
                    info == null -> TelemetryError.NotConnected
                    info.ssid == null || info.ssid == "<unknown ssid>" || info.ssid == "0x" ->
                        TelemetryError.PermissionDenied
                    info.rssi == Int.MAX_VALUE -> TelemetryError.NotConnected
                    else -> null
                }
            }
        }
        if (error != null) _errorEvent.value = error
    }

    // ── Private helpers ─────────────────────────────────────────────

    /**
     * Build a single tick from live WiFi info + ping result.
     *
     * Every individual read is wrapped in try-catch — a failure in one field
     * never poisons the whole tick.
     */
    @Suppress("DEPRECATION")
    private suspend fun measureTick(): ApTelemetryTick = withContext(Dispatchers.IO) {
        val wm = wifiManager
        val cm = connectivityManager

        // ── 1. Read WiFi connection info ──────────────────────────
        val (ssid, bssid, rssi, ipAddress) = readWifiInfo(wm)

        // ── 2. Derive signal strength from RSSI ───────────────────
        val signalStrength = when {
            rssi == Int.MAX_VALUE -> 0f
            rssi < -100 -> 5f
            rssi > -40 -> 100f
            else -> ((rssi + 100) * 1.6667f).coerceIn(0f, 100f)
        }

        // ── 3. ICMP ping to gateway ───────────────────────────────
        val gateway = getGatewayIp()
        val (reachable, latencyMs) = if (gateway != null) {
            try {
                executePingOnce(gateway, timeoutSec = 2)
            } catch (_: Exception) {
                false to 0f
            }
        } else {
            false to 0f
        }

        // ── 4. Internet capability check ──────────────────────────
        val hasInternet = if (cm != null) {
            try {
                val network = cm.activeNetwork
                if (network != null) {
                    val caps = cm.getNetworkCapabilities(network)
                    caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                } else false
            } catch (_: Exception) {
                false
            }
        } else false

        ApTelemetryTick(
            ssid = ssid,
            bssid = bssid,
            ipAddress = ipAddress,
            signalStrength = signalStrength,
            latencyMs = latencyMs,
            reachable = reachable && hasInternet,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Read all WiFi connection info in one shot, returning defaults
     * on any failure so the caller never has to handle exceptions.
     */
    @Suppress("DEPRECATION")
    private fun readWifiInfo(wm: WifiManager?): WifiReadResult {
        if (wm == null) return WifiReadResult()
        return try {
            val info = wm.connectionInfo
            if (info == null) return WifiReadResult()
            WifiReadResult(
                ssid = info.ssid?.removeSurrounding("\"")
                    ?.takeUnless { it == "<unknown ssid>" || it == "0x" }
                    ?: "<no permission>",
                bssid = info.bssid
                    ?.takeUnless { it == "02:00:00:00:00:00" || it == "00:00:00:00:00:00" }
                    ?: "<no permission>",
                rssi = info.rssi,
                ipAddress = ipIntToString(info.ipAddress) ?: "<unknown>"
            )
        } catch (_: SecurityException) {
            WifiReadResult(ssid = "<no permission>", bssid = "<no permission>")
        } catch (_: Exception) {
            WifiReadResult(ssid = "<read error>")
        }
    }

    /** Holder for the result of a live WiFi info read. */
    private data class WifiReadResult(
        val ssid: String = "<no WiFi>",
        val bssid: String = "<no WiFi>",
        val rssi: Int = Int.MAX_VALUE,
        val ipAddress: String = "<none>"
    )

    /**
     * Execute a single ICMP ping and parse the result.
     *
     * Reads both stdout and stderr to avoid process deadlocks on devices
     * where ping writes diagnostic output to the error stream.
     */
    private fun executePingOnce(
        host: String,
        timeoutSec: Int = 2
    ): Pair<Boolean, Float> {
        val process = Runtime.getRuntime().exec(
            arrayOf("ping", "-c", "1", "-W", timeoutSec.toString(), host)
        )

        // Read both streams to prevent buffer deadlock
        val stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        val stderr = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
        val output = stdout + stderr

        val exitCode = process.waitFor()

        return if (exitCode == 0) {
            // Extract "time=XX.X ms" (handle "time=12.3 ms", "time 12.3ms", etc.)
            val timeMatch = Regex("""time[= ]+(\d+\.?\d*)\s*ms""").find(output)
            val latency = timeMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            true to latency
        } else {
            // Extract packet loss percentage
            val lossMatch = Regex("""(\d+)% packet loss""").find(output)
            // Return loss info even when unreachable
            false to 0f
        }
    }

    /**
     * Convert an IP address stored as a 32-bit integer to dotted-decimal.
     */
    private fun ipIntToString(raw: Int): String {
        return try {
            InetAddress.getByAddress(
                byteArrayOf(
                    (raw and 0xff).toByte(),
                    (raw shr 8 and 0xff).toByte(),
                    (raw shr 16 and 0xff).toByte(),
                    (raw shr 24 and 0xff).toByte()
                )
            ).hostAddress ?: "0.0.0.0"
        } catch (_: Exception) {
            "0.0.0.0"
        }
    }
}
