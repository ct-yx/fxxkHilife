package com.freebuds.controller.ui.foundation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.freebuds.controller.ui.UiDisplayMode
import com.freebuds.controller.ui.foundation.surface.SurfaceRenderer
import com.freebuds.controller.ui.foundation.surface.SurfaceRole
import com.freebuds.controller.ui.foundation.surface.SurfaceRenderMode
import com.freebuds.controller.ui.foundation.surface.SurfaceSpec

@Composable
fun Material3Card(
    displayMode: UiDisplayMode = UiDisplayMode.MATERIAL3,
    modifier: Modifier = Modifier,
    role: SurfaceRole = SurfaceRole.StandardCard,
    tint: Color? = null,
    cornerRadius: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SurfaceRenderer.Card(
        spec = SurfaceSpec(
            role = role,
            renderMode = SurfaceRenderMode.WallpaperGlass,
            tint = tint,
            cornerRadius = cornerRadius,
        ),
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

/** One tonal 40dp leading container shared by list-card icons. */
@Composable
fun CardIconContainer(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
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
