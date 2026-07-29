package com.netgraphqx.engine

import com.netgraphqx.model.TelemetryTick
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * Real-time network & hardware telemetry engine.
 *
 * Emits a continuous [Flow] of [TelemetryTick] data at a configurable
 * interval, simulating ICMP ping latency, packet loss, CPU load, and
 * memory pressure. Designed for live waveform rendering.
 */
class TelemetryEngine {

    /** Sampling interval in milliseconds. */
    var sampleIntervalMs: Long = 250L

    /** Maximum number of ticks retained in history. */
    var maxHistory: Int = 300

    private var baselineLatencyMs = 25f       // simulated baseline
    private var baselineCpuLoad = 35f
    private var baselineMemoryLoad = 45f

    /** Ring buffer of recent ticks for back-referencing. */
    private val _history = ArrayDeque<TelemetryTick>(maxHistory)
    val history: List<TelemetryTick> get() = _history.toList()

    /**
     * Infinite flow of telemetry ticks at [sampleIntervalMs] rate.
     * Automatically introduces realistic jitter and occasional spikes.
     */
    fun observeTelemetry(): Flow<TelemetryTick> = flow {
        while (true) {
            val tick = generateTick()
            _history.addLast(tick)
            if (_history.size > maxHistory) {
                _history.removeFirst()
            }
            emit(tick)
            delay(sampleIntervalMs)
        }
    }

    /**
     * Single-shot ICMP ping emulation.
     * Returns simulated round-trip time in milliseconds.
     */
    suspend fun pingOnce(): Float {
        delay(50L + Random.nextLong(150L))
        return baselineLatencyMs + Random.nextFloat() * 15f +
                spikeNoise(0.05f) // 5% chance of a latency spike
    }

    /**
     * Flush accumulated history.
     */
    fun reset() {
        _history.clear()
        // Re-randomize baselines for a fresh session feel
        baselineLatencyMs = 15f + Random.nextFloat() * 30f
        baselineCpuLoad = 20f + Random.nextFloat() * 30f
        baselineMemoryLoad = 30f + Random.nextFloat() * 40f
    }

    // ── Private helpers ─────────────────────────────────────────────

    private fun generateTick(): TelemetryTick {
        val ping = baselineLatencyMs + Random.nextFloat() * 12f + spikeNoise(0.08f)
        val loss = if (Random.nextFloat() < 0.03f) Random.nextFloat() * 8f else 0f
        val cpu = baselineCpuLoad + Random.nextFloat() * 20f - 10f + spikeNoise(0.03f)
        val mem = baselineMemoryLoad + Random.nextFloat() * 15f - 7f + spikeNoise(0.02f)

        return TelemetryTick(
            pingLatencyMs = ping.coerceIn(0f, 999f),
            packetLossPct = loss.coerceIn(0f, 100f),
            cpuLoadPct = cpu.coerceIn(0f, 100f),
            memoryLoadPct = mem.coerceIn(0f, 100f)
        )
    }

    /** Generate spike noise with given probability [prob]. */
    private fun spikeNoise(prob: Float): Float {
        return if (Random.nextFloat() < prob) {
            Random.nextFloat() * 120f + 30f // significant spike
        } else 0f
    }

    companion object {
        /** Status threshold constants. */
        const val PING_OPTIMAL_MAX = 80f      // ms
        const val PING_DEGRADED_MAX = 200f    // ms
        const val LOSS_OPTIMAL_MAX = 1f       // %
        const val LOSS_DEGRADED_MAX = 5f      // %
        const val CPU_OPTIMAL_MAX = 50f       // %
        const val CPU_DEGRADED_MAX = 80f      // %
    }
}
