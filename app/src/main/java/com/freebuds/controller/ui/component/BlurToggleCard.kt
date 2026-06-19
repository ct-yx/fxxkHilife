package com.freebuds.controller.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.freebuds.controller.FreeBudsApp

/**
 * Glass effect wrapper — applies frosted glass / liquid glass / none
 * based on user preference from DataStore.
 *
 * Uses Compose built-in blur (API 31+) for frosted glass;
 * liquid glass uses a chromatic gradient overlay + slight blur.
 *
 * Future: replace with Haze library for better performance / wider API support.
 */
@Composable
fun BlurToggleCard(
    blurStyle: String = FreeBudsApp.instance.preferences.blurStyle.collectAsState(initial = "frosted").value,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    when (blurStyle) {
        "frosted" -> {
            Box(modifier = modifier.clip(RoundedCornerShape(20.dp))) {
                content()
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                        .blur(radius = 12.dp)
                        .alpha(0.85f)
                )
            }
        }
        "liquid" -> {
            Box(modifier = modifier.clip(RoundedCornerShape(20.dp))) {
                content()
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0x40FF6B9D),
                                    Color(0x40C084FC),
                                    Color(0x4038BDF8)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(1000f, 1000f)
                            )
                        )
                        .blur(radius = 16.dp)
                        .alpha(0.7f)
                )
            }
        }
        else -> content()
    }
}
