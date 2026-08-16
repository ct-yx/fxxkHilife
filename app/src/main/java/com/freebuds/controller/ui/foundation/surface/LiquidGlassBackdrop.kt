package com.freebuds.controller.ui.foundation.surface

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A static, low-cost colour field used as the glass input when no user wallpaper is selected.
 * A uniform Material background cannot show refraction; this gives the real Haze surface a
 * meaningful source without requiring an image picker or adding animated work to every frame.
 */
@Composable
fun LiquidGlassBackdrop(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier.fillMaxSize()) {
        drawRect(color = scheme.background)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    scheme.primary.copy(alpha = 0.34f),
                    scheme.secondary.copy(alpha = 0.20f),
                    scheme.tertiary.copy(alpha = 0.32f),
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(scheme.primary.copy(alpha = 0.44f), Color.Transparent),
                center = Offset(size.width * 0.16f, size.height * 0.16f),
                radius = size.minDimension * 0.62f,
            ),
            radius = size.minDimension * 0.62f,
            center = Offset(size.width * 0.16f, size.height * 0.16f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(scheme.tertiary.copy(alpha = 0.40f), Color.Transparent),
                center = Offset(size.width * 0.90f, size.height * 0.30f),
                radius = size.minDimension * 0.56f,
            ),
            radius = size.minDimension * 0.56f,
            center = Offset(size.width * 0.90f, size.height * 0.30f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(scheme.secondary.copy(alpha = 0.34f), Color.Transparent),
                center = Offset(size.width * 0.58f, size.height * 0.94f),
                radius = size.minDimension * 0.64f,
            ),
            radius = size.minDimension * 0.64f,
            center = Offset(size.width * 0.58f, size.height * 0.94f),
        )
    }
}
