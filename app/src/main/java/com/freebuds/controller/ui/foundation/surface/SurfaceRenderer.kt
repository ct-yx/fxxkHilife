package com.freebuds.controller.ui.foundation.surface

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.freebuds.controller.ui.UiDisplayMode
import com.freebuds.controller.ui.foundation.tokens.UiTokens

/** Semantic surface roles. All roles use the same standard Material 3 card contract. */
enum class SurfaceRole { Hero, FeatureCard, StandardCard, CompactRow, Dialog, AppBar }

/** Kept as a typed seam for callers; Material 3 is the only production renderer. */
enum class SurfaceRenderMode { Material3 }

@Immutable
data class SurfaceSpec(
    val role: SurfaceRole = SurfaceRole.StandardCard,
    val renderMode: SurfaceRenderMode = SurfaceRenderMode.Material3,
    val tint: Color? = null,
    val cornerRadius: Dp = UiTokens.ref.radiusLarge,
)

/**
 * The single production surface adapter.
 *
 * There is deliberately no captured-background, blur, refraction or translucent fallback here.
 * Every page receives an opaque, theme-aware Material 3 Card with the same shape, padding and
 * elevation rules, so the visual result is predictable on every Android version and device.
 */
object SurfaceRenderer {
    @Composable
    @Suppress("UNUSED_PARAMETER")
    fun Card(
        spec: SurfaceSpec,
        displayMode: UiDisplayMode = UiDisplayMode.MATERIAL3,
        modifier: Modifier = Modifier,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        val shape = RoundedCornerShape(spec.cornerRadius)
        val containerColor = (spec.tint ?: MaterialTheme.colorScheme.surfaceVariant).copy(alpha = 1f)
        val elevation = when (spec.role) {
            SurfaceRole.Hero -> 3.dp
            SurfaceRole.FeatureCard -> 2.dp
            SurfaceRole.StandardCard, SurfaceRole.CompactRow -> 1.dp
            SurfaceRole.Dialog, SurfaceRole.AppBar -> 2.dp
        }

        MaterialCard(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        ) {
            Column(Modifier.padding(UiTokens.ref.space4), content = content)
        }
    }
}
