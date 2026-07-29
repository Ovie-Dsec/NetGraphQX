package com.netgraphqx.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.netgraphqx.model.ApTelemetryTick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
 * Permission notes (API 33+):
 * - [android.Manifest.permission.NEARBY_WIFI_DEVICES] is required for SSID/BSSID
 *   and is auto-granted at install time on API 33+.
 * - On API 32 and below the app must hold
 *   [android.Manifest.permission.ACCESS_FINE_LOCATION].
 * - If required permissions are missing the engine degrades gracefully and
 *   returns "No Permission" / [ApStatus.DOWN] for the affected fields.
 */
@Suppress("DEPRECATION")
class TelemetryEngine(private val context: Context) {

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Cached ping result (updated from IO thread)
    private var cachedLatencyMs = 0f
    private var cachedReachable = false
    private var cachedPacketLoss = 0f

    /**
     * Infinite flow of AP telemetry ticks.
     *
     * Each tick reads WiFi connection info instantly and performs a real ICMP
     * ping to the default gateway. The interval between ticks is 1 second
     * during normal operation; it may stretch slightly when pings time out.
     */
    fun observeTelemetry(): Flow<ApTelemetryTick> = flow {
        while (true) {
            val tick = measureTick()
            emit(tick)
            delay(1000L)
        }
    }

    /**
     * Returns the SSID of the currently connected WiFi, or a fallback string.
     */
    fun getCurrentSsid(): String {
        val info = wifiManager.connectionInfo ?: return "<no WiFi>"
        val raw = info.ssid ?: return "<unknown>"
        // On API 33+ without NEARBY_WIFI_DEVICES, getSSID() returns "<unknown ssid>"
        if (raw == "<unknown ssid>" || raw == "0x") return "<permission required>"
        return raw.removeSurrounding("\"")
    }

    /**
     * Returns the gateway IP as a dotted string, or null if not available.
     */
    fun getGatewayIp(): String? {
        val dhcpInfo = wifiManager.dhcpInfo ?: return null
        val raw = dhcpInfo.gateway
        if (raw == 0) return null
        return ipIntToString(raw)
    }

    /**
     * Force a one-shot ping check returning (reachable, latencyMs).
     */
    suspend fun pingGateway(): Pair<Boolean, Float> = withContext(Dispatchers.IO) {
        val gateway = getGatewayIp() ?: return@withContext false to 0f
        val result = executePingOnce(gateway, timeoutSec = 3)
        result
    }

    /**
     * No-op: switching simulated APs no longer applies.
     */
    fun switchAp(index: Int) { /* real WiFi — no-op */ }

    /**
     * No-op: simulation state no longer applies.
     */
    fun reset() { /* real WiFi — no-op */ }

    // ── Private helpers ─────────────────────────────────────────────

    /**
     * Build a single tick from live WiFi info + cached ping result.
     */
    private suspend fun measureTick(): ApTelemetryTick = withContext(Dispatchers.IO) {
        val info = wifiManager.connectionInfo

        val ssid: String
        val bssid: String
        val rssi: Int
        val ipAddress: String
        val linkSpeed: Int
        val frequency: Int

        if (info != null) {
            val rawSsid = info.ssid ?: "<unknown>"
            ssid = if (rawSsid == "<unknown ssid>" || rawSsid == "0x")
                "<no permission>" else rawSsid.removeSurrounding("\"")

            val rawBssid = info.bssid ?: "<unknown>"
            bssid = if (rawBssid == "02:00:00:00:00:00" || rawBssid == "00:00:00:00:00:00")
                "<no permission>" else rawBssid

            rssi = info.rssi
            ipAddress = ipIntToString(info.ipAddress) ?: "<unknown>"
            linkSpeed = info.linkSpeed
            frequency = info.frequency
        } else {
            ssid = "<no WiFi>"
            bssid = "<no WiFi>"
            rssi = Int.MAX_VALUE
            ipAddress = "<none>"
            linkSpeed = 0
            frequency = 0
        }

        // Derive signal strength percentage from RSSI
        // Typical range: -50 dBm (excellent) → -100 dBm (dead)
        val signalStrength = when {
            rssi == Int.MAX_VALUE -> 0f          // no reading
            rssi < -100 -> 5f
            rssi > -40 -> 100f
            else -> ((rssi + 100) * 1.6667f).coerceIn(0f, 100f)
        }

        // Perform ICMP ping to gateway
        val gateway = getGatewayIp()
        val (reachable, latencyMs) = if (gateway != null) {
            executePingOnce(gateway, timeoutSec = 2)
        } else {
            false to 0f
        }

        // Check for actual network connectivity
        val hasInternet = hasInternetCapability()

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
     * Execute a single ICMP ping and parse the result.
     */
    private fun executePingOnce(
        host: String,
        timeoutSec: Int = 2
    ): Pair<Boolean, Float> {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("ping", "-c", "1", "-W", timeoutSec.toString(), host)
            )
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                // Extract "time=XX.X ms" from output
                val timeMatch = Regex("""time[= ]+(\d+\.?\d*)\s*ms""").find(output)
                val latency = timeMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                true to latency
            } else {
                // Extract packet loss percentage
                val lossMatch = Regex("""(\d+)% packet loss""").find(output)
                val lossPct = lossMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 100f
                false to 0f
            }
        } catch (e: Exception) {
            false to 0f
        }
    }

    /**
     * Check whether the device currently has internet access.
     */
    private fun hasInternetCapability(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
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
