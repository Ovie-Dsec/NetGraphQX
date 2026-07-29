package com.netgraphqx.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netgraphqx.engine.MathEngine
import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.log10
import com.netgraphqx.engine.TelemetryEngine
import com.netgraphqx.model.*
import com.netgraphqx.theme.*
import kotlin.math.roundToInt

/**
 * Core graph canvas composable — renders at ~60 FPS via Compose Canvas.
 *
 * Supports two rendering modes:
 * - MATHEMATICS: 2D Cartesian grid with f(x) curve
 * - TELEMETRY:   Streaming waveform with color-coded thresholds
 *
 * Built-in gestures: pan (drag), pinch-to-zoom, coordinate tracing (tap).
 */
@Composable
fun GraphCanvas(
    mode: AppMode,
    expression: String,
    samples: List<MathEngine.GraphSample>,
    telemetryTicks: List<TelemetryTick>,
    viewport: Viewport,
    onViewportChange: (Viewport) -> Unit,
    traceX: Float?,
    onTraceXChange: (Float?) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    var touchState by remember { mutableStateOf(TouchState()) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (zoom != 1f) {
                        // Pinch-to-zoom around centroid
                        val density = this@pointerInput.density
                        val size = this@pointerInput.size
                        val focusX = viewport.xMin + (centroid.x / size.width) * viewport.xRange
                        val focusY = viewport.yMax - (centroid.y / size.height) * viewport.yRange
                        onViewportChange(viewport.zoom(1f / zoom, focusX, focusY))
                    } else {
                        // Pan
                        val dx = -(pan.x / size.width) * viewport.xRange
                        val dy = (pan.y / size.height) * viewport.yRange
                        onViewportChange(viewport.pan(dx, dy))
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val size = this@pointerInput.size
                    val x = viewport.xMin + (offset.x / size.width) * viewport.xRange
                    onTraceXChange(x)
                }
            }
    ) {
        val w = size.width
        val h = size.height

        when (mode) {
            AppMode.MATHEMATICS -> drawMathGrid(w, h, viewport, textMeasurer)
            AppMode.TELEMETRY -> drawTelemetryGrid(w, h, viewport, textMeasurer)
        }

        when (mode) {
            AppMode.MATHEMATICS -> drawCurve(w, h, viewport, samples)
            AppMode.TELEMETRY -> drawTelemetryWaveform(w, h, viewport, telemetryTicks)
        }

        // Coordinate tracing crosshair
        if (traceX != null) {
            drawTraceCrosshair(w, h, viewport, traceX, samples, textMeasurer)
        }
    }
}

// ── Drawing helpers ─────────────────────────────────────────────────

private fun DrawScope.drawMathGrid(
    w: Float, h: Float,
    vp: Viewport,
    measurer: TextMeasurer
) {
    val gridColor = GraphGrid
    val axisColor = GraphAxis

    // Calculate grid step (powers of 10 * 1,2,5)
    val rawStep = niceStep(vp.xRange / 6f)
    val xStart = (vp.xMin / rawStep).toInt() * rawStep
    val xEnd = (vp.xMax / rawStep).toInt() * rawStep

    // Vertical grid lines
    var x = xStart
    while (x <= xEnd) {
        val px = mapX(x, w, vp)
        if (x == 0f) {
            drawLine(axisColor, Offset(px, 0f), Offset(px, h), strokeWidth = 1.5f)
            // Axis label
            val label = formatNumber(x)
            val result = measurer.measure(
                AnnotatedString(label),
                style = TextStyle(color = TextSecondary, fontSize = 10.sp)
            )
            drawText(result, topLeft = Offset(px + 4f, h / 2f + 4f))
        } else {
            drawLine(gridColor, Offset(px, 0f), Offset(px, h), strokeWidth = 0.5f)
            val result = measurer.measure(
                AnnotatedString(formatNumber(x)),
                style = TextStyle(color = TextSecondary, fontSize = 9.sp)
            )
            drawText(result, topLeft = Offset(px + 3f, h / 2f + 3f))
        }
        x += rawStep
    }

    // Horizontal grid lines
    val yStep = niceStep(vp.yRange / 6f)
    val yStart = (vp.yMin / yStep).toInt() * yStep
    val yEnd = (vp.yMax / yStep).toInt() * yStep
    var y = yStart
    while (y <= yEnd) {
        val py = mapY(y, h, vp)
        if (y == 0f) {
            drawLine(axisColor, Offset(0f, py), Offset(w, py), strokeWidth = 1.5f)
        } else {
            drawLine(gridColor, Offset(0f, py), Offset(w, py), strokeWidth = 0.5f)
            val result = measurer.measure(
                AnnotatedString(formatNumber(y)),
                style = TextStyle(color = TextSecondary, fontSize = 9.sp)
            )
            drawText(result, topLeft = Offset(w / 2f + 4f, py - 10f))
        }
        y += yStep
    }
}

private fun DrawScope.drawTelemetryGrid(
    w: Float, h: Float,
    vp: Viewport,
    measurer: TextMeasurer
) {
    val gridColor = GraphGrid

    // Draw threshold zone backgrounds
    // Red zone (top ~20%)
    drawRect(
        color = TelemetryRed.copy(alpha = 0.08f),
        topLeft = Offset(0f, 0f),
        size = androidx.compose.ui.geometry.Size(w, h * 0.2f)
    )

    // Draw horizontal threshold lines
    val optimalLine = mapY(0.4f, h, vp)  // 40% threshold
    val criticalLine = mapY(0.8f, h, vp) // 80% threshold
    drawLine(TelemetryYellow.copy(alpha = 0.4f), Offset(0f, optimalLine), Offset(w, optimalLine), strokeWidth = 1f)
    drawLine(TelemetryRed.copy(alpha = 0.4f), Offset(0f, criticalLine), Offset(w, criticalLine), strokeWidth = 1f)

    // Vertical time grid (every 5 seconds worth of ticks)
    val timeStep = niceStep(vp.xRange / 6f)
    var tx = (vp.xMin / timeStep).toInt() * timeStep
    while (tx <= vp.xMax) {
        val px = mapX(tx, w, vp)
        drawLine(gridColor, Offset(px, 0f), Offset(px, h), strokeWidth = 0.5f)
        tx += timeStep
    }

    // Draw left axis labels
    val labels = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    for (label in labels) {
        val py = mapY(label, h, vp)
        val pct = "${(label * 100).roundToInt()}%"
        val result = measurer.measure(
            AnnotatedString(pct),
            style = TextStyle(color = TextSecondary, fontSize = 9.sp)
        )
        drawText(result, topLeft = Offset(4f, py - result.size.height / 2f))
    }
}

/**
 * Draws the mathematical f(x) curve as a connected line path.
 */
private fun DrawScope.drawCurve(
    w: Float, h: Float,
    vp: Viewport,
    samples: List<MathEngine.GraphSample>
) {
    if (samples.isEmpty()) return

    val path = Path()
    var started = false

    for (sample in samples) {
        if (!sample.valid) {
            started = false
            continue
        }
        val px = mapX(sample.x, w, vp)
        val py = mapY(sample.y, h, vp)

        if (!started) {
            path.moveTo(px, py)
            started = true
        } else {
            path.lineTo(px, py)
        }
    }

    drawPath(path, color = GraphCurve, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Draws telemetry data as a streaming waveform with color-coded fill.
 */
private fun DrawScope.drawTelemetryWaveform(
    w: Float, h: Float,
    vp: Viewport,
    ticks: List<TelemetryTick>
) {
    if (ticks.size < 2) return

    val path = Path()
    val fillPath = Path()
    val displayTicks = if (ticks.size > 1) ticks else ticks

    // Map ticks to normalized values (0..1) then to screen coords
    val normalized = displayTicks.map { tick ->
        tick.cpuLoadPct / 100f
    }

    // Build paths
    val stepX = w / (normalized.size - 1).coerceAtLeast(1)
    var first = true

    for (i in normalized.indices) {
        val px = i * stepX
        val py = mapY(normalized[i].coerceIn(0f, 1f), h, vp)

        if (first) {
            path.moveTo(px, py)
            fillPath.moveTo(px, h)
            fillPath.lineTo(px, py)
            first = false
        } else {
            path.lineTo(px, py)
            fillPath.lineTo(px, py)
        }
    }

    // Close fill path
    fillPath.lineTo((normalized.size - 1) * stepX, h)
    fillPath.close()

    // Draw fill gradient
    drawPath(fillPath, brush = Brush.verticalGradient(
        colors = listOf(GraphCurve.copy(alpha = 0.3f), GraphCurve.copy(alpha = 0.05f)),
        startY = 0f,
        endY = h
    ))

    // Draw waveform line
    drawPath(path, color = GraphCurve, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Coordinate tracing crosshair at the tapped x position.
 */
private fun DrawScope.drawTraceCrosshair(
    w: Float, h: Float,
    vp: Viewport,
    traceX: Float,
    samples: List<MathEngine.GraphSample>,
    measurer: TextMeasurer
) {
    val px = mapX(traceX, w, vp)
    val nearest = samples.minByOrNull { kotlin.math.abs(it.x - traceX) }

    // Vertical line
    drawLine(
        AccentCyan.copy(alpha = 0.6f),
        Offset(px, 0f),
        Offset(px, h),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
    )

    if (nearest != null && nearest.valid) {
        val py = mapY(nearest.y, h, vp)
        // Crosshair dot
        drawCircle(AccentCyan, radius = 4f, center = Offset(px, py))
        // Tooltip label
        val label = "x=${formatNumber(nearest.x)}  y=${formatNumber(nearest.y)}"
        val result = measurer.measure(
            AnnotatedString(label),
            style = TextStyle(color = AccentCyan, fontSize = 11.sp, background = DarkBackground.copy(alpha = 0.85f))
        )
        val labelX = (px - result.size.width / 2f).coerceIn(4f, w - result.size.width - 4f)
        val labelY = (py - result.size.height - 12f).coerceIn(4f, h - result.size.height - 4f)
        drawText(result, topLeft = Offset(labelX, labelY))
    }
}

// ── Coordinate mapping ──────────────────────────────────────────────

private fun mapX(x: Float, w: Float, vp: Viewport): Float {
    return ((x - vp.xMin) / vp.xRange) * w
}

private fun mapY(y: Float, h: Float, vp: Viewport): Float {
    return h - ((y - vp.yMin) / vp.yRange) * h
}

// ── Utilities ───────────────────────────────────────────────────────

private fun niceStep(range: Float): Float {
    val exponent = log10(range.toDouble()).toInt().toFloat()
    val raw = 10f.pow(exponent)
    val ratio = range / raw
    return when {
        ratio >= 5f -> raw * 5f
        ratio >= 2f -> raw * 2f
        else -> raw
    }
}

private fun formatNumber(v: Float): String {
    return when {
        v == 0f -> "0"
        abs(v) >= 1000 -> "${(v / 1000f).roundToInt()}k"
        abs(v) >= 1 -> String.format("%.1f", v)
        abs(v) >= 0.01f -> String.format("%.2f", v)
        else -> String.format("%.3f", v)
    }
}
