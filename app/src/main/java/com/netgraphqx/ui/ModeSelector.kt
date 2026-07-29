package com.netgraphqx.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netgraphqx.model.AppMode
import com.netgraphqx.theme.*

/**
 * Top mode-toggle selector pill.
 * Switches between MATHEMATICS and TELEMETRY modes.
 */
@Composable
fun ModeSelector(
    currentMode: AppMode,
    onModeChange: (AppMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModePill(
            label = "f(x) Math",
            icon = Icons.Default.Functions,
            selected = currentMode == AppMode.MATHEMATICS,
            onClick = { onModeChange(AppMode.MATHEMATICS) },
            modifier = Modifier.weight(1f)
        )

        ModePill(
            label = "Network Telemetry",
            icon = Icons.Default.Sensors,
            selected = currentMode == AppMode.TELEMETRY,
            onClick = { onModeChange(AppMode.TELEMETRY) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) AccentCyan.copy(alpha = 0.15f) else DarkSurfaceVariant,
        animationSpec = tween(200),
        label = "pillBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) AccentCyan else TextSecondary,
        animationSpec = tween(200),
        label = "pillText"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = textColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
