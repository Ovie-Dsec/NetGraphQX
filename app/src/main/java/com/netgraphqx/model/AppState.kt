package com.netgraphqx.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Operational mode for the dual-mode architecture.
 */
enum class AppMode {
    /** Mathematical function graphing mode */
    MATHEMATICS,

    /** Network & hardware telemetry mode */
    TELEMETRY
}

/**
 * A single data point plotted on the graph canvas.
 */
data class GraphPoint(
    val x: Float,
    val y: Float,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Telemetry status thresholds mapped to color coding.
 */
enum class TelemetryStatus(val label: String, val threshold: Float) {
    OPTIMAL("Optimal", 0.0f),   // Green  — 0-40%负载/延迟
    DEGRADED("Degraded", 0.4f), // Yellow — 40-80%
    CRITICAL("Critical", 0.8f)  // Red    — 80%+
}

/**
 * A single telemetry metric snapshot.
 */
data class TelemetryTick(
    val pingLatencyMs: Float,
    val packetLossPct: Float,
    val cpuLoadPct: Float,
    val memoryLoadPct: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    /** Derive composite status from the worst metric. */
    val compositeStatus: TelemetryStatus
        get() {
            val worst = maxOf(pingLatencyMs / 500f, packetLossPct / 100f, cpuLoadPct / 100f, memoryLoadPct / 100f)
            return when {
                worst >= TelemetryStatus.CRITICAL.threshold -> TelemetryStatus.CRITICAL
                worst >= TelemetryStatus.DEGRADED.threshold -> TelemetryStatus.DEGRADED
                else -> TelemetryStatus.OPTIMAL
            }
        }

    val statusColor: Color
        get() = when (compositeStatus) {
            TelemetryStatus.OPTIMAL -> Color(0xFF00E676)
            TelemetryStatus.DEGRADED -> Color(0xFFFFD600)
            TelemetryStatus.CRITICAL -> Color(0xFFFF1744)
        }
}

/**
 * Graph viewport state — controls visible coordinate range.
 */
data class Viewport(
    val xMin: Float = -10f,
    val xMax: Float = 10f,
    val yMin: Float = -10f,
    val yMax: Float = 10f
) {
    val xRange: Float get() = xMax - xMin
    val yRange: Float get() = yMax - yMin

    fun pan(dx: Float, dy: Float): Viewport = copy(
        xMin = xMin + dx, xMax = xMax + dx,
        yMin = yMin + dy, yMax = yMax + dy
    )

    fun zoom(scale: Float, focusX: Float, focusY: Float): Viewport {
        val newXRange = xRange * scale
        val newYRange = yRange * scale
        val fx = (focusX - xMin) / xRange
        val fy = (focusY - yMin) / yRange
        return copy(
            xMin = focusX - fx * newXRange,
            xMax = focusX + (1f - fx) * newXRange,
            yMin = focusY - fy * newYRange,
            yMax = focusY + (1f - fy) * newYRange
        )
    }
}

/**
 * Canvas interaction state for touch gestures.
 */
data class TouchState(
    val isPanning: Boolean = false,
    val panStart: Offset = Offset.Zero,
    val lastZoomDistance: Float = 0f,
    val isTracing: Boolean = false,
    val traceX: Float = 0f
)
