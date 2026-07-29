package com.netgraphqx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.netgraphqx.BuildConfig
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netgraphqx.engine.MathEngine
import com.netgraphqx.engine.TelemetryEngine
import com.netgraphqx.model.*
import com.netgraphqx.theme.*
import com.netgraphqx.ui.*
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * NetGraph QX — Main Activity
 *
 * Hybrid graphing calculator and real-time network/hardware telemetry terminal.
 *
 * The UI is vertically partitioned into three sections:
 *   1. Graph Canvas (40% base)
 *   2. Function/Macro Pad (25% base, collapsible)
 *   3. In-App Keypad (35% base, collapsible)
 *
 * Uses `android:windowSoftInputMode="adjustNothing"` in the manifest
 * to suppress the native OS soft keyboard entirely.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetGraphQXTheme {
                NetGraphQXApp()
            }
        }
    }
}

/**
 * Describes the result of evaluating the current expression.
 */
private sealed class CalculationResult {
    /** Pure arithmetic evaluated to a number (no 'x' in expression). */
    data class Value(val number: Float) : CalculationResult()
    /** Expression contains 'x' — shown as a function, not a fixed number. */
    data object Function : CalculationResult()
    /** Evaluation produced an error. */
    data class Error(val message: String) : CalculationResult()
    /** No expression entered, or not in math mode. */
    data object None : CalculationResult()
}

/**
 * Root composable — owns all application state and launches
 * side-effect coroutines for telemetry streaming.
 */
@Composable
private fun NetGraphQXApp() {
    // ── Core state ────────────────────────────────────────────────
    var currentMode by remember { mutableStateOf(AppMode.MATHEMATICS) }
    var expression by remember { mutableStateOf("sin(x)") }
    var viewport by remember { mutableStateOf(Viewport()) }
    var traceX by remember { mutableStateOf<Float?>(null) }

    // Section expand states (Sections 2 & 3 are collapsible)
    var functionPadExpanded by remember { mutableStateOf(true) }
    var keypadExpanded by remember { mutableStateOf(true) }

    // ── Engine instances ──────────────────────────────────────────
    val mathEngine = remember { MathEngine() }
    val telemetryEngine = remember { TelemetryEngine() }

    // ── Derived: math samples ─────────────────────────────────────
    val samples by remember(expression, viewport) {
        derivedStateOf {
            if (currentMode == AppMode.MATHEMATICS && expression.isNotBlank()) {
                mathEngine.sample(
                    expression = expression,
                    xMin = viewport.xMin,
                    xMax = viewport.xMax,
                    step = viewport.xRange / 500f // adaptive resolution
                )
            } else emptyList()
        }
    }

    // ── Derived: calculation result for pure arithmetic (no 'x') ──
    val calcResult by remember(expression, currentMode) {
        derivedStateOf {
            if (currentMode != AppMode.MATHEMATICS || expression.isBlank()) {
                CalculationResult.None
            } else if (!expression.contains('x')) {
                // Pure arithmetic — evaluate once (no variable dependency)
                val value = mathEngine.evaluate(expression, 0.0)
                if (value != null && value.isFinite()) {
                    CalculationResult.Value(value.toFloat())
                } else {
                    CalculationResult.Error("undefined")
                }
            } else {
                CalculationResult.Function
            }
        }
    }

    // ── AP telemetry state ────────────────────────────────────────
    var apTick by remember { mutableStateOf<ApTelemetryTick?>(null) }

    // Collect AP telemetry ticks when in TELEMETRY mode
    LaunchedEffect(currentMode) {
        if (currentMode == AppMode.TELEMETRY) {
            telemetryEngine.reset()
            telemetryEngine.observeTelemetry().collect { tick ->
                apTick = tick
            }
        }
    }

    // Reset state when switching modes
    LaunchedEffect(currentMode) {
        if (currentMode == AppMode.MATHEMATICS) {
            apTick = null
            viewport = Viewport()
            traceX = null
        }
    }

    // ── Layout ────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .systemBarsPadding()
            .navigationBarsPadding()
    ) {
        // ── Top bar: title + mode selector ────────────────────────
        TopBar(
            mode = currentMode,
            onModeChange = { currentMode = it }
        )

        // ── Compute dynamic weights ───────────────────────────────
        // Canvas weight = base 8 / Pad weight = base 5 / Keypad weight = base 7
        val showPad = functionPadExpanded
        val showKeypad = keypadExpanded
        val canvasBase = 8
        val padBase = 5
        val keypadBase = 7

        val canvasWeight: Float
        val padWeight: Float
        val keypadWeight: Float

        if (showPad && showKeypad) {
            canvasWeight = canvasBase.toFloat()
            padWeight = padBase.toFloat()
            keypadWeight = keypadBase.toFloat()
        } else if (showPad) {
            canvasWeight = (canvasBase + keypadBase).toFloat()
            padWeight = padBase.toFloat()
            keypadWeight = 0f
        } else if (showKeypad) {
            canvasWeight = (canvasBase + padBase).toFloat()
            padWeight = 0f
            keypadWeight = keypadBase.toFloat()
        } else {
            canvasWeight = (canvasBase + padBase + keypadBase).toFloat()
            padWeight = 0f
            keypadWeight = 0f
        }

        // ── SECTION 1: Graph Canvas ───────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(canvasWeight)
        ) {
            GraphCanvas(
                mode = currentMode,
                expression = expression,
                samples = samples,
                apTick = apTick,
                viewport = viewport,
                onViewportChange = { viewport = it },
                traceX = traceX,
                onTraceXChange = { traceX = it }
            )

            // Status overlay for telemetry mode
            if (currentMode == AppMode.TELEMETRY && apTick != null) {
                TelemetryStatusBar(apTick!!)
            }
        }

        // ── SECTION 2: Function / Macro Pad ───────────────────────
        if (showPad) {
            FunctionPad(
                mode = currentMode,
                onFunctionInsert = { fn ->
                    expression = if (expression.endsWith("(") || expression.isEmpty()) {
                        expression + fn
                    } else {
                        expression + fn
                    }
                }
            )
        } else {
            // Collapsed handle — just a thin strip to re-expand
            CollapsedSectionHandle(
                label = if (currentMode == AppMode.MATHEMATICS) "Functions (tap to expand)" else "Macros (tap to expand)",
                onExpand = { functionPadExpanded = true }
            )
        }

        // ── SECTION 3: In-App Keypad ──────────────────────────────
        if (showKeypad) {
            InAppKeypad(
                expression = expression,
                onExpressionChange = { expression = it },
                onSubmit = {
                    // Re-evaluate expression with current viewport
                    if (currentMode == AppMode.MATHEMATICS && expression.isNotBlank()) {
                        // Expression evaluation is handled via derivedStateOf, just trigger refresh
                    }
                }
            )
        } else {
            CollapsedSectionHandle(
                label = "Keypad (tap to expand)",
                onExpand = { keypadExpanded = true }
            )
        }

        // ── Expression input bar (always visible) ─────────────────
        ExpressionBar(
            expression = expression,
            result = calcResult,
            onExpressionChange = { expression = it },
            onClear = { expression = ""; traceX = null }
        )
    }
}

// ── Sub-composables ─────────────────────────────────────────────────

/**
 * Top bar with app title and mode selector.
 */
@Composable
private fun TopBar(
    mode: AppMode,
    onModeChange: (AppMode) -> Unit
) {
    Column {
        // Title bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkBackground)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NetGraph QX",
                color = AccentCyan,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Mode selector
        ModeSelector(
            currentMode = mode,
            onModeChange = onModeChange
        )
    }
}

/**
 * Expression input bar pinned at the bottom.
 */
@Composable
private fun ExpressionBar(
    expression: String,
    result: CalculationResult,
    onExpressionChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label + expression display (takes remaining space via weight)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (result) {
                    is CalculationResult.Value -> {
                        // Pure arithmetic: show "expression = result"
                        Text(
                            text = "$expression =",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatResult(result.number),
                            color = AccentCyan,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    is CalculationResult.Function -> {
                        // Has 'x': show "f(x) = expression"
                        Text(
                            text = "f(x) =",
                            color = AccentCyan,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = expression.ifEmpty { "enter expression..." },
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    is CalculationResult.Error -> {
                        Text(
                            text = "$expression =",
                            color = TelemetryRed,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Error: ${result.message}",
                            color = TelemetryRed,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                    is CalculationResult.None -> {
                        Text(
                            text = expression.ifEmpty { "enter expression..." },
                            color = if (expression.isNotEmpty()) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Active / graph indicator badge
        if (result is CalculationResult.Function || expression.isNotBlank()) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .background(
                        color = if (result is CalculationResult.Error) TelemetryRed.copy(alpha = 0.15f)
                                else AccentCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = when (result) {
                        is CalculationResult.Value -> "= ${formatResult(result.number)}" as String
                        is CalculationResult.Function -> "graph" as String
                        is CalculationResult.Error -> "err" as String
                        else -> "live" as String
                    },
                    color = when (result) {
                        is CalculationResult.Error -> TelemetryRed
                        else -> AccentCyan
                    },
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** Format a float nicely for display: drop trailing zeros, use int when whole. */
private fun formatResult(v: Float): String {
    return when {
        v.isNaN() -> "NaN"
        v.isInfinite() -> "∞"
        v == v.toLong().toFloat() && abs(v) < 1e12 -> v.toLong().toString()
        abs(v) >= 1000 -> String.format("%,.2f", v)
        abs(v) >= 1 -> String.format("%.4f", v).trimEnd('0').trimEnd('.')
        abs(v) >= 0.0001f -> String.format("%.6f", v).trimEnd('0').trimEnd('.')
        v == 0f -> "0"
        else -> String.format("%.1e", v)
    }
}

/**
 * Telemetry status overlay displayed on the canvas in telemetry mode.
 */
@Composable
private fun TelemetryStatusBar(tick: ApTelemetryTick) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ApMetricChip("SSID", tick.ssid, tick.statusColor)
        ApMetricChip("SIGNAL", "${tick.signalStrength.roundToInt()}%", tick.statusColor)
        ApMetricChip("LATENCY", "${tick.latencyMs.roundToInt()}ms", tick.statusColor)
        ApMetricChip("STATUS", tick.status.label, tick.statusColor)
    }
}

@Composable
private fun ApMetricChip(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Thin collapsed section strip — tap to re-expand.
 */
@Composable
private fun CollapsedSectionHandle(
    label: String,
    onExpand: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .clickable(onClick = onExpand)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = TextSecondary.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
