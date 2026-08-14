package com.freebuds.controller.ui.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.freebuds.controller.ui.UiDisplayMode
import com.freebuds.controller.ui.foundation.surface.SurfaceRenderer
import com.freebuds.controller.ui.foundation.surface.SurfaceRole
import com.freebuds.controller.ui.foundation.surface.SurfaceSpec

/** Kept as a short migration name; rendering is now centralized in SurfaceRenderer. */
enum class GlassSurfaceProfile { Rounded, Squircle, Circle }

@Composable
fun AdaptiveCard(
    displayMode: UiDisplayMode,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SurfaceRenderer.Card(
        spec = SurfaceSpec(role = SurfaceRole.StandardCard),
        displayMode = displayMode,
        modifier = modifier,
        content = content,
    )
}
@Composable
fun LiquidGlassCard(
    displayMode: UiDisplayMode = UiDisplayMode.LIQUID_GLASS,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    refractionStrength: Float? = null,
    depth: Float? = null,
    shape: androidx.compose.ui.graphics.Shape? = null,
    cornerRadius: Dp? = null,
    surfaceProfile: GlassSurfaceProfile? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val radius = cornerRadius ?: when (surfaceProfile) {
        GlassSurfaceProfile.Circle -> 96.dp
        GlassSurfaceProfile.Rounded -> 20.dp
        else -> 28.dp
    }
    SurfaceRenderer.Card(
        spec = SurfaceSpec(
            role = SurfaceRole.FeatureCard,
            tint = tint,
            cornerRadius = radius,
            depth = depth ?: refractionStrength ?: 0.42f,
        ),
        displayMode = displayMode,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun LiquidGlassPanel(
    displayMode: UiDisplayMode,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    SurfaceRenderer.Card(
        spec = SurfaceSpec(role = SurfaceRole.Hero, cornerRadius = 28.dp, depth = 0.34f),
        displayMode = displayMode,
        modifier = modifier,
    ) {
        Box(content = content)
    }
}

enum class GlassBannerTone { Info, Success, Error }

@Composable
fun AdaptiveGlassBanner(
    displayMode: UiDisplayMode,
    modifier: Modifier = Modifier,
    tone: GlassBannerTone = GlassBannerTone.Info,
    content: @Composable RowScope.() -> Unit,
) {
    val tint = when (tone) {
        GlassBannerTone.Info -> MaterialTheme.colorScheme.primaryContainer
        GlassBannerTone.Success -> MaterialTheme.colorScheme.tertiaryContainer
        GlassBannerTone.Error -> MaterialTheme.colorScheme.errorContainer
    }
    SurfaceRenderer.Card(
        spec = SurfaceSpec(
            role = SurfaceRole.FeatureCard,
            tint = tint.copy(alpha = 0.86f),
            cornerRadius = 20.dp,
            depth = 0.30f,
        ),
        displayMode = displayMode,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun SurfaceSpecPreviewCard(displayMode: UiDisplayMode, modifier: Modifier = Modifier) {
    AdaptiveCard(displayMode, modifier) {
        Text("Surface preview", style = MaterialTheme.typography.titleMedium)
        Text("Shared source, typed renderer and visible fallback.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
