package com.netgraphqx.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Operational mode for the dual-mode architecture.
 */
enum class AppMode {
    /** Mathematical function graphing mode */
    MATHEMATICS,

    /** Network telemetry mode — access point status */
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
 * Whether the access point is reachable.
 */
enum class ApStatus(val label: String) {
    UP("UP"),
    DOWN("DOWN"),
    UNSTABLE("UNSTABLE")
}

/**
 * A single telemetry snapshot focused on the access point the device
 * is connected to.
 *
 * @property ssid Network name (SSID) of the access point.
 * @property bssid BSSID / MAC address of the AP radio.
 * @property ipAddress Gateway IP address.
 * @property signalStrength Signal level 0..100 (100 = best).
 * @property latencyMs Round-trip ping time to the gateway in ms.
 * @property reachable Whether the gateway responded to the last ping.
 * @property status Derived UP / DOWN / UNSTABLE status.
 * @property timestamp Epoch millis when this tick was captured.
 */
data class ApTelemetryTick(
    val ssid: String,
    val bssid: String,
    val ipAddress: String,
    val signalStrength: Float,   // 0..100
    val latencyMs: Float,         // ms
    val reachable: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    val status: ApStatus
        get() = when {
            !reachable -> ApStatus.DOWN
            latencyMs > 500f || signalStrength < 15f -> ApStatus.UNSTABLE
            else -> ApStatus.UP
        }

    val statusColor: Color
        get() = when (status) {
            ApStatus.UP -> Color(0xFF00E676)       // green
            ApStatus.UNSTABLE -> Color(0xFFFFD600) // yellow
            ApStatus.DOWN -> Color(0xFFFF1744)     // red
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
 * User-facing telemetry errors that should be shown as a dialog.
 *
 * Each variant carries a human-readable title and body message.
 * Persistent errors (no hardware, no permission, no WiFi) are shown
 * once and remain until dismissed. Transient errors are handled
 * silently through the tick's status/color instead.
 */
sealed class TelemetryError(
    open val title: String,
    open val message: String
) {
    /** Device lacks WiFi hardware (tablet, emulator, etc.). */
    data object NoWifiHardware : TelemetryError(
        title = "No WiFi Hardware",
        message = "This device does not have WiFi hardware. Telemetry mode requires an active WiFi connection to display live data."
    )

    /** Required runtime permission not granted. */
    data object PermissionDenied : TelemetryError(
        title = "Permission Required",
        message = "NetGraph QX needs the \"Nearby Devices\" permission (Android 13+) or location permission (Android 12 and below) to read the WiFi name and signal strength.\n\nGo to Settings → Apps → NetGraph QX → Permissions and grant the required permission, then restart telemetry."
    )

    /** Device is not connected to any WiFi network. */
    data object NotConnected : TelemetryError(
        title = "Not Connected to WiFi",
        message = "Connect to a WiFi network first, then switch to Telemetry mode to see live AP data."
    )

    /** Catch-all for unexpected errors. */
    data class Generic(
        override val message: String
    ) : TelemetryError(
        title = "Telemetry Error",
        message = message
    )
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
