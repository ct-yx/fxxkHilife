package com.freebuds.controller.ui.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.freebuds.controller.ui.UiDisplayMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@Composable
fun AdaptiveCard(
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (displayMode == UiDisplayMode.LIQUID_GLASS && hazeState != null) {
        LiquidGlassCard(
            hazeState = hazeState,
            modifier = modifier,
            content = content,
        )
    } else {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content,
            )
        }
    }
}

@Composable
fun LiquidGlassCard(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(30.dp)
    val primaryTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    Surface(
        modifier = modifier
            .clip(shape)
            .hazeEffect(state = hazeState) {
                blurRadius = 26.dp
                noiseFactor = 0.08f
                tints = listOf(
                    HazeTint(Color.White.copy(alpha = 0.11f)),
                    HazeTint(primaryTint),
                )
            }
            .liquidGlassBorder(shapeRadiusPx = 30f),
        shape = shape,
        color = Color.White.copy(alpha = 0.065f),
        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.18f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.095f),
                            Color.White.copy(alpha = 0.035f),
                        )
                    ),
                    shape = RoundedCornerShape(24.dp),
                )
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
fun LiquidGlassPanel(
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    if (displayMode == UiDisplayMode.LIQUID_GLASS && hazeState != null) {
        Box(
            modifier = modifier
                .clip(shape)
                .hazeEffect(state = hazeState) {
                    blurRadius = 22.dp
                    noiseFactor = 0.06f
                    tints = listOf(HazeTint(Color.White.copy(alpha = 0.10f)))
                }
                .liquidGlassBorder(shapeRadiusPx = 28f)
                .background(Color.White.copy(alpha = 0.07f), shape)
                .padding(4.dp),
            content = content,
        )
    } else {
        Box(modifier = modifier, content = content)
    }
}

private fun Modifier.liquidGlassBorder(shapeRadiusPx: Float): Modifier = this.drawWithCache {
    val stroke = 1.25.dp.toPx()
    val radius = CornerRadius(shapeRadiusPx.dp.toPx(), shapeRadiusPx.dp.toPx())
    val rainbow = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFF7AB6).copy(alpha = 0.55f),
            Color(0xFF7AE7FF).copy(alpha = 0.42f),
            Color(0xFFB388FF).copy(alpha = 0.50f),
            Color.White.copy(alpha = 0.30f),
        ),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    onDrawWithContent {
        drawContent()
        drawRoundRect(
            brush = rainbow,
            cornerRadius = radius,
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.16f),
            cornerRadius = radius,
            style = Stroke(width = 0.6.dp.toPx()),
        )
    }
}
