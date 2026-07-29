package com.netgraphqx.engine

import com.netgraphqx.model.ApTelemetryTick
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * Access Point telemetry engine.
 *
 * Emits a continuous [Flow] of [ApTelemetryTick] at a configurable interval,
 * simulating the status of the WiFi access point the device is connected to.
 *
 * Each tick carries:
 * - SSID / BSSID of the AP
 * - Gateway IP address
 * - Signal strength (0..100)
 * - Ping latency to the gateway
 * - Reachability (UP / DOWN / UNSTABLE)
 *
 * Designed to drive the AP arrow visualisation in telemetry mode.
 */
class TelemetryEngine {

    /** Sampling interval in milliseconds. */
    var sampleIntervalMs: Long = 1000L

    /** Maximum number of ticks retained in history. */
    var maxHistory: Int = 60

    // Simulated AP identifiers
    private val apNames = listOf(
        "Home-Fi-5G", "Office-Net", "Guest_WiFi",
        "ISP-Gateway", "Mesh-Node-1", "FiberBox-2.4G"
    )
    private val apBssids = listOf(
        "A4:5E:60:D1:23:4F", "00:1A:2B:3C:4D:5E",
        "FC:EC:DA:12:34:56", "78:8A:20:AB:CD:EF",
        "E4:5F:01:67:89:AB", "C0:FF:EE:DD:CC:BB"
    )
    private val gatewayIps = listOf(
        "192.168.1.1", "10.0.0.1", "192.168.0.1",
        "172.16.0.1", "192.168.86.1", "10.0.1.1"
    )

    // Current simulated AP identity (stable across a session)
    private var currentApIndex = 0
    private var baselineSignal = 75f
    private var baselineLatency = 12f

    /** Ring buffer of recent ticks. */
    private val _history = ArrayDeque<ApTelemetryTick>(maxHistory)
    val history: List<ApTelemetryTick> get() = _history.toList()

    /** Latest tick (for instant UI reads). */
    var latestTick: ApTelemetryTick? = null
        private set

    /**
     * Infinite flow of AP telemetry ticks at [sampleIntervalMs] rate.
     */
    fun observeTelemetry(): Flow<ApTelemetryTick> = flow {
        while (true) {
            val tick = generateTick()
            _history.addLast(tick)
            if (_history.size > maxHistory) {
                _history.removeFirst()
            }
            latestTick = tick
            emit(tick)
            delay(sampleIntervalMs)
        }
    }

    /**
     * Force a connectivity check (simulated).
     */
    suspend fun pingGateway(): Boolean {
        delay(80L + Random.nextLong(200L))
        return Random.nextFloat() > 0.08f   // 92% reachable
    }

    /**
     * Switch to a different simulated AP.
     */
    fun switchAp(index: Int) {
        currentApIndex = index.coerceIn(0, apNames.lastIndex)
        baselineSignal = 50f + Random.nextFloat() * 45f
        baselineLatency = 5f + Random.nextFloat() * 30f
    }

    /**
     * Flush accumulated history.
     */
    fun reset() {
        _history.clear()
        currentApIndex = Random.nextInt(apNames.size)
        baselineSignal = 50f + Random.nextFloat() * 45f
        baselineLatency = 5f + Random.nextFloat() * 30f
    }

    // ── Private helpers ─────────────────────────────────────────────

    private fun generateTick(): ApTelemetryTick {
        // Occasionally wobble the signal and inject a dropout
        val signal = (baselineSignal + Random.nextFloat() * 12f - 6f
                + if (Random.nextFloat() < 0.06f) -30f else 0f)
            .coerceIn(0f, 100f)

        val latency = (baselineLatency + Random.nextFloat() * 8f
                + if (Random.nextFloat() < 0.04f) 200f + Random.nextFloat() * 400f else 0f)
            .coerceIn(0f, 999f)

        val reachable = when {
            signal < 5f -> false
            latency > 600f -> Random.nextFloat() > 0.5f
            else -> Random.nextFloat() > 0.02f    // 98% reachable normally
        }

        return ApTelemetryTick(
            ssid = apNames[currentApIndex],
            bssid = apBssids[currentApIndex],
            ipAddress = gatewayIps[currentApIndex],
            signalStrength = signal,
            latencyMs = latency,
            reachable = reachable
        )
    }
}
