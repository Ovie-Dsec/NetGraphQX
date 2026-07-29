package com.netgraphqx.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netgraphqx.theme.*

/**
 * SECTION 3: Integrated Custom QWERTY Keypad.
 *
 * A fully self-contained key matrix that routes key presses into an
 * expression string builder state. Designed to prevent the native OS
 * soft keyboard from appearing ("adjustNothing" mode).
 *
 * Key layout: QWERTY rows + numeric row + symbol row + action keys.
 * Supports collapse/expand via a drawer handle.
 */
@Composable
fun InAppKeypad(
    expression: String,
    onExpressionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "keypadArrow"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // ── Drawer handle ────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Keypad",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp).rotate(rotationAngle)
            )
        }

        // ── Collapsible key matrix ───────────────────────────────
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KeypadBackground)
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Row 1: Numbers 0-9
                NumberRow { key -> onExpressionChange(expression + key) }

                // Row 2: QWERTY top row
                KeyRow(
                    keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                    onKeyPress = { onExpressionChange(expression + it) }
                )

                // Row 3: QWERTY middle row
                KeyRow(
                    keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                    onKeyPress = { onExpressionChange(expression + it) }
                )

                // Row 4: QWERTY bottom row + special keys
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Shift / uppercase toggle
                    KeyButton(
                        label = "⇧",
                        width = 40.dp,
                        action = true,
                        onClick = {
                            // Toggle case — simplified: insert uppercase version
                            onExpressionChange(expression + "")
                        }
                    )
                    KeyRow(
                        keys = listOf("z", "x", "c", "v", "b", "n", "m"),
                        modifier = Modifier.weight(1f),
                        onKeyPress = { onExpressionChange(expression + it) }
                    )
                    // Backspace
                    IconKeyButton(
                        icon = Icons.Default.Backspace,
                        width = 48.dp,
                        action = true,
                        onClick = {
                            if (expression.isNotEmpty()) {
                                onExpressionChange(expression.dropLast(1))
                            }
                        }
                    )
                }

                // Row 5: Symbol keys + action keys
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val symbols = listOf("(", ")", "[", "]", ",", ".", "=")
                    for (sym in symbols) {
                        KeyButton(
                            label = sym,
                            modifier = Modifier.weight(1f),
                            onClick = { onExpressionChange(expression + sym) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val ops = listOf("+", "-", "*", "/", "%", "@", ":", ";")
                    for (op in ops) {
                        KeyButton(
                            label = op,
                            modifier = Modifier.weight(1f),
                            onClick = { onExpressionChange(expression + op) }
                        )
                    }
                }

                // Row 6: Action bar — Clear + Space + Submit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Clear all
                    ActionKey(
                        label = "CLR",
                        color = TelemetryRed.copy(alpha = 0.3f),
                        textColor = TelemetryRed,
                        modifier = Modifier.weight(0.4f),
                        onClick = { onExpressionChange("") }
                    )

                    // Space
                    KeyButton(
                        label = "Space",
                        width = null,
                        modifier = Modifier.weight(1f),
                        action = false,
                        onClick = { onExpressionChange(expression + " ") }
                    )

                    // Submit / Enter
                    ActionKey(
                        label = "↵",
                        color = AccentCyan.copy(alpha = 0.25f),
                        textColor = AccentCyan,
                        modifier = Modifier.weight(0.4f),
                        onClick = onSubmit
                    )
                }
            }
        }
    }
}

// ── Sub-composables ─────────────────────────────────────────────────

@Composable
private fun NumberRow(onKeyPress: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (i in 0..9) {
            KeyButton(
                label = i.toString(),
                modifier = Modifier.weight(1f),
                onClick = { onKeyPress(i.toString()) }
            )
        }
    }
}

@Composable
private fun KeyRow(
    keys: List<String>,
    modifier: Modifier = Modifier,
    onKeyPress: (String) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (key in keys) {
            KeyButton(
                label = key,
                modifier = Modifier.weight(1f),
                onClick = { onKeyPress(key) }
            )
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    width: androidx.compose.ui.unit.Dp? = null,
    action: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = if (action) DarkSurfaceVariant else KeyButtonBackground
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = KeyButtonText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

@Composable
private fun IconKeyButton(
    icon: ImageVector,
    width: androidx.compose.ui.unit.Dp,
    action: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = if (action) DarkSurfaceVariant else KeyButtonBackground
    Box(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "backspace",
            tint = KeyButtonText,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ActionKey(
    label: String,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}


