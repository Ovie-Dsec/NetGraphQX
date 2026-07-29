package com.netgraphqx.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netgraphqx.model.AppMode
import com.netgraphqx.theme.*

/**
 * SECTION 2: Calculator & Telemetry Function Pad.
 *
 * Provides quick-access buttons for math functions (sin, cos, tan, etc.)
 * and telemetry macros (PING, TRACE, CPU, MEM).
 *
 * Supports collapse/expand via a drawer handle to maximize canvas space.
 */
@Composable
fun FunctionPad(
    mode: AppMode,
    onFunctionInsert: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "expandArrow"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // ── Drawer handle ────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (mode == AppMode.MATHEMATICS) "Functions" else "Network Macros",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp).rotate(rotationAngle)
            )
        }

        // ── Collapsible button grid ──────────────────────────────
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                when (mode) {
                    AppMode.MATHEMATICS -> MathFunctionGrid(onFunctionInsert)
                    AppMode.TELEMETRY -> TelemetryMacroGrid(onFunctionInsert)
                }
            }
        }
    }
}

@Composable
private fun MathFunctionGrid(onInsert: (String) -> Unit) {
    val functions = listOf(
        listOf("sin", "cos", "tan"),
        listOf("sqrt(", "^", "log("),
        listOf("ln(", "abs(", "pi")
    )

    for (row in functions) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (fn in row) {
                FunctionButton(
                    label = fn,
                    insert = fn,
                    modifier = Modifier.weight(1f),
                    onClick = onInsert
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun TelemetryMacroGrid(onInsert: (String) -> Unit) {
    val macros = listOf(
        listOf("PING", "TRACERT", "NSLOOKUP"),
        listOf("CPU", "MEM", "NETSTAT")
    )

    for (row in macros) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (macro in row) {
                FunctionButton(
                    label = macro,
                    insert = macro,
                    modifier = Modifier.weight(1f),
                    telemetry = true,
                    onClick = onInsert
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun FunctionButton(
    label: String,
    insert: String,
    telemetry: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (telemetry) DarkSurfaceVariant else KeyButtonBackground)
            .clickable { onClick(insert) }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (telemetry) AccentCyan else KeyButtonText,
            fontSize = if (telemetry) 11.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}
