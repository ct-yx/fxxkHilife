package com.freebuds.controller.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BatteryCard(
    batteryLeft: Int?,
    batteryRight: Int?,
    batteryCase: Int?,
    batteryChargingLeft: Boolean = false,
    batteryChargingRight: Boolean = false,
    batteryChargingCase: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Battery",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BatteryDot("L", batteryLeft, batteryChargingLeft, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                BatteryDot("R", batteryRight, batteryChargingRight, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                BatteryDot("Case", batteryCase, batteryChargingCase, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BatteryDot(label: String, level: Int?, charging: Boolean = false, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        targetValue = when {
            charging -> Color(0xFF4CAF50)
            level == null -> MaterialTheme.colorScheme.surfaceVariant
            level < 20 -> Color(0xFFE53935)
            level < 50 -> Color(0xFFFB8C00)
            else -> MaterialTheme.colorScheme.primary
        },
        label = "batteryColor"
    )
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            if (level != null) {
                val prefix = if (charging) "⚡" else ""
                Text("$prefix$level%", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = color)
            } else {
                Text("--", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
