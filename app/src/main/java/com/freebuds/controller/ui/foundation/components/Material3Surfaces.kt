package com.freebuds.controller.ui.foundation.components

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

@Composable
fun Material3Card(
    displayMode: UiDisplayMode = UiDisplayMode.MATERIAL3,
    modifier: Modifier = Modifier,
    role: SurfaceRole = SurfaceRole.StandardCard,
    tint: Color? = null,
    cornerRadius: Dp = 28.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    SurfaceRenderer.Card(
        spec = SurfaceSpec(role = role, tint = tint, cornerRadius = cornerRadius),
        displayMode = displayMode,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun Material3Panel(
    displayMode: UiDisplayMode = UiDisplayMode.MATERIAL3,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Material3Card(
        displayMode = displayMode,
        role = SurfaceRole.Hero,
        modifier = modifier,
    ) {
        Box(content = content)
    }
}

enum class Material3BannerTone { Info, Success, Error }

@Composable
fun Material3Banner(
    displayMode: UiDisplayMode = UiDisplayMode.MATERIAL3,
    modifier: Modifier = Modifier,
    tone: Material3BannerTone = Material3BannerTone.Info,
    content: @Composable RowScope.() -> Unit,
) {
    val containerColor = when (tone) {
        Material3BannerTone.Info -> MaterialTheme.colorScheme.primaryContainer
        Material3BannerTone.Success -> MaterialTheme.colorScheme.tertiaryContainer
        Material3BannerTone.Error -> MaterialTheme.colorScheme.errorContainer
    }
    Material3Card(
        displayMode = displayMode,
        role = SurfaceRole.FeatureCard,
        tint = containerColor,
        cornerRadius = 20.dp,
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
fun Material3SurfacePreviewCard(
    displayMode: UiDisplayMode = UiDisplayMode.MATERIAL3,
    modifier: Modifier = Modifier,
) {
    Material3Card(displayMode, modifier) {
        Text("Material 3 surface", style = MaterialTheme.typography.titleMedium)
        Text("Opaque theme surface with standard tonal elevation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
