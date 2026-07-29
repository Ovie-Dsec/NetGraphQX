package com.netgraphqx.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netgraphqx.engine.MathEngine
import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.log10
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
    apTick: ApTelemetryTick?,
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
            AppMode.TELEMETRY -> { /* No grid background for AP view */ }
        }

        when (mode) {
            AppMode.MATHEMATICS -> drawCurve(w, h, viewport, samples)
            AppMode.TELEMETRY -> drawApVisualization(w, h, apTick, textMeasurer)
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
 * Draws the AP telemetry visualisation:
 *   - Device icon (bottom centre)
 *   - Upward arrow to the access point
 *   - AP name + status badge
 *   - Signal strength bars + info panel
 */
private fun DrawScope.drawApVisualization(
    w: Float, h: Float,
    tick: ApTelemetryTick?,
    measurer: TextMeasurer
) {
    if (tick == null) {
        val msg = measurer.measure(
            AnnotatedString("Waiting for AP data..."),
            style = TextStyle(color = TextSecondary, fontSize = 14.sp)
        )
        drawText(msg, topLeft = Offset(w / 2f - msg.size.width / 2f, h / 2f - msg.size.height / 2f))
        return
    }

    val cx = w / 2f                    // horizontal centre
    val deviceY = h * 0.78f            // device position (near bottom)
    val apY = h * 0.15f                // AP position (near top)
    val arrowTopY = h * 0.32f
    val arrowBotY = h * 0.68f

    val statusColor = tick.statusColor

    // ── 1. Signal bars (left side) ──────────────────────────────
    val barX = 24f
    val barCount = 5
    val filledBars = (tick.signalStrength / 20f).roundToInt().coerceIn(0, barCount)
    val barH = 14f
    val barSpacing = 6f
    for (i in 0 until barCount) {
        val filled = i < filledBars
        val bh = barH * (i + 1) * 0.6f
        val by = arrowBotY - (barCount - 1 - i) * (barH * 0.6f + barSpacing)
        drawRoundRect(
            color = if (filled) statusColor.copy(alpha = 0.8f) else DarkSurfaceVariant,
            topLeft = Offset(barX, by - bh),
            size = androidx.compose.ui.geometry.Size(10f, bh),
            cornerRadius = CornerRadius(2f, 2f)
        )
    }
    val sigLabel = measurer.measure(
        AnnotatedString("${tick.signalStrength.roundToInt()}%"),
        style = TextStyle(color = TextSecondary, fontSize = 9.sp)
    )
    drawText(sigLabel, topLeft = Offset(barX - 2f, arrowBotY + 4f))

    // ── 2. Device representation (bottom centre) ────────────────
    val devW = 60f
    val devH = 36f
    val devX = cx - devW / 2f
    drawRoundRect(
        color = AccentCyan.copy(alpha = 0.2f),
        topLeft = Offset(devX, deviceY),
        size = androidx.compose.ui.geometry.Size(devW, devH),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawRoundRect(
        color = AccentCyan.copy(alpha = 0.5f),
        topLeft = Offset(devX, deviceY),
        size = androidx.compose.ui.geometry.Size(devW, devH),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = 1.5f)
    )
    // Screen area inside phone
    drawRoundRect(
        color = AccentCyan.copy(alpha = 0.15f),
        topLeft = Offset(devX + 10f, deviceY + 6f),
        size = androidx.compose.ui.geometry.Size(devW - 20f, devH - 12f),
        cornerRadius = CornerRadius(2f, 2f)
    )
    // Small home button dot
    drawCircle(AccentCyan.copy(alpha = 0.4f), radius = 2f, center = Offset(cx, deviceY + devH - 5f))

    // "Device" label
    val devLabel = measurer.measure(
        AnnotatedString("DEVICE"),
        style = TextStyle(color = AccentCyan, fontSize = 8.sp)
    )
    drawText(devLabel, topLeft = Offset(cx - devLabel.size.width / 2f, deviceY + devH + 4f))

    // ── 3. Upward arrow ─────────────────────────────────────────
    val arrowPath = Path()
    val arrowMidX = cx

    // Arrow shaft
    arrowPath.moveTo(arrowMidX, arrowBotY)
    arrowPath.lineTo(arrowMidX, arrowTopY + 20f)

    // Arrow head
    val headSize = 24f
    arrowPath.moveTo(arrowMidX, arrowTopY)
    arrowPath.lineTo(arrowMidX - headSize / 2f, arrowTopY + headSize * 0.7f)
    arrowPath.moveTo(arrowMidX, arrowTopY)
    arrowPath.lineTo(arrowMidX + headSize / 2f, arrowTopY + headSize * 0.7f)

    drawPath(arrowPath, color = statusColor, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Wavy signal rings around the arrow middle
    for (i in 0..2) {
        val ringRadius = 40f + i * 18f
        val ringAlpha = 0.25f - i * 0.07f
        if (ringAlpha > 0f && tick.reachable) {
            drawCircle(
                color = statusColor.copy(alpha = ringAlpha),
                radius = ringRadius,
                center = Offset(cx, (arrowTopY + arrowBotY) / 2f),
                style = Stroke(width = 1.5f)
            )
        }
    }

    // ── 4. Access point representation (top centre) ────────────
    val apW = 80f
    val apH = 44f
    val apX = cx - apW / 2f
    drawRoundRect(
        color = statusColor.copy(alpha = 0.2f),
        topLeft = Offset(apX, apY),
        size = androidx.compose.ui.geometry.Size(apW, apH),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = statusColor.copy(alpha = 0.6f),
        topLeft = Offset(apX, apY),
        size = androidx.compose.ui.geometry.Size(apW, apH),
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = 1.5f)
    )
    // Antenna lines on top of AP
    drawLine(statusColor.copy(alpha = 0.5f), Offset(cx - 12f, apY), Offset(cx - 12f, apY - 10f), strokeWidth = 1.5f)
    drawLine(statusColor.copy(alpha = 0.5f), Offset(cx + 12f, apY), Offset(cx + 12f, apY - 8f), strokeWidth = 1.5f)
    // Small circles at antenna tips
    drawCircle(statusColor.copy(alpha = 0.5f), radius = 2f, center = Offset(cx - 12f, apY - 10f))
    drawCircle(statusColor.copy(alpha = 0.5f), radius = 2f, center = Offset(cx + 12f, apY - 8f))

    // AP label (SSID)
    val ssidLabel = measurer.measure(
        AnnotatedString(tick.ssid),
        style = TextStyle(color = TextPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    )
    drawText(ssidLabel, topLeft = Offset(cx - ssidLabel.size.width / 2f, apY + apH + 4f))

    // ── 5. Status badge (right side) ────────────────────────────
    val badgeText = tick.status.label
    val badgeResult = measurer.measure(
        AnnotatedString(badgeText),
        style = TextStyle(
            color = statusColor,
            fontSize = 22.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    )
    val badgeX = w - badgeResult.size.width - 16f
    val badgeY = apY + apH / 2f - badgeResult.size.height / 2f
    drawText(badgeResult, topLeft = Offset(badgeX, badgeY))

    // Small status indicator dot
    val dotRadius = 6f
    drawCircle(
        color = statusColor,
        radius = dotRadius,
        center = Offset(badgeX - dotRadius - 6f, badgeY + badgeResult.size.height / 2f)
    )

    // ── 6. Info panel (left, below signal bars) ─────────────────
    val infoLines = listOf(
        "IP : ${tick.ipAddress}",
        "BSSID : ${tick.bssid}",
        "Latency : ${tick.latencyMs.roundToInt()} ms",
        "Status : ${tick.status.label}"
    )
    var infoY = deviceY + devH + 24f
    for (line in infoLines) {
        val lineResult = measurer.measure(
            AnnotatedString(line),
            style = TextStyle(color = TextSecondary, fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        )
        drawText(lineResult, topLeft = Offset(8f, infoY))
        infoY += lineResult.size.height + 3f
    }
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
