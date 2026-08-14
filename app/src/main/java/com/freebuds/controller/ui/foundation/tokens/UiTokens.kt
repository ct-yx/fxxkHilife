package com.freebuds.controller.ui.foundation.tokens

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * UI tokens are deliberately grouped as ref (raw design rhythm), sys (theme semantics) and comp
 * (component contracts). Screens should use these tokens instead of inventing local spacing or
 * near-duplicate colors.
 */
object UiTokens {
    object ref {
        val space1: Dp = 4.dp
        val space2: Dp = 8.dp
        val space3: Dp = 12.dp
        val space4: Dp = 16.dp
        val space5: Dp = 24.dp
        val space6: Dp = 32.dp
        val space7: Dp = 40.dp
        val radiusSmall: Dp = 12.dp
        val radiusMedium: Dp = 20.dp
        val radiusLarge: Dp = 28.dp
        val controlMinSize: Dp = 44.dp
    }

    object sys {
        @Composable
        fun pageBackground(): Color = MaterialTheme.colorScheme.background

        @Composable
        fun content(): Color = MaterialTheme.colorScheme.onSurface

        @Composable
        fun secondaryContent(): Color = MaterialTheme.colorScheme.onSurfaceVariant

        @Composable
        fun divider(): Color = MaterialTheme.colorScheme.outlineVariant

        @Composable
        fun danger(): Color = MaterialTheme.colorScheme.error

        @Composable
        fun success(): Color = MaterialTheme.colorScheme.tertiary
    }

    object comp {
        val cardShape = RoundedCornerShape(ref.radiusLarge)
        val rowShape = RoundedCornerShape(ref.radiusMedium)
        val controlShape = RoundedCornerShape(16.dp)
        val titleSize = 22.sp
        val actionAnimation: FiniteAnimationSpec<Float> = tween(durationMillis = 220)
    }
}

@Immutable
data class SurfaceSemanticTokens(
    val containerAlpha: Float,
    val borderAlpha: Float,
    val elevationDp: Dp,
)
