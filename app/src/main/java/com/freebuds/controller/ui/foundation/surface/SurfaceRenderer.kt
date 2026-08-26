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

/** Semantic surface roles shared by every route. */
enum class SurfaceRole { Hero, FeatureCard, StandardCard, CompactRow, Dialog, AppBar }

enum class SurfaceRenderMode {
    Material3,
    WallpaperGlass,
}

@Immutable
data class SurfaceSpec(
    val role: SurfaceRole = SurfaceRole.StandardCard,
    val renderMode: SurfaceRenderMode = SurfaceRenderMode.Material3,
    val tint: Color? = null,
    val cornerRadius: Dp? = null,
)

internal fun SurfaceRole.defaultCornerRadius(): Dp = when (this) {
    SurfaceRole.Hero -> 28.dp
    SurfaceRole.FeatureCard -> 26.dp
    SurfaceRole.StandardCard -> 22.dp
    SurfaceRole.CompactRow -> 20.dp
    SurfaceRole.Dialog, SurfaceRole.AppBar -> 22.dp
}

internal fun SurfaceRole.glassProfile(): GlassProfile = when (this) {
    SurfaceRole.Hero -> GlassProfile.Hero
    SurfaceRole.FeatureCard -> GlassProfile.Feature
    SurfaceRole.StandardCard -> GlassProfile.Standard
    SurfaceRole.CompactRow -> GlassProfile.Compact
    SurfaceRole.Dialog -> GlassProfile.Feature
    SurfaceRole.AppBar -> GlassProfile.TopBar
}

/** The only production surface adapter; wallpaper glass is selected only when it is available. */
object SurfaceRenderer {
    @Composable
    fun Card(
        spec: SurfaceSpec,
        displayMode: UiDisplayMode = UiDisplayMode.MATERIAL3,
        modifier: Modifier = Modifier,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        @Suppress("UNUSED_VARIABLE")
        val ignoredDisplayMode = displayMode
        val host = LocalWallpaperGlass.current
        val resolvedRadius = spec.cornerRadius ?: spec.role.defaultCornerRadius()
        val shape = RoundedCornerShape(resolvedRadius)
        val useGlass = spec.renderMode == SurfaceRenderMode.WallpaperGlass &&
            host != null &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU

        if (useGlass) {
            WallpaperGlassSurface(
                modifier = modifier,
                shape = shape,
                profile = spec.role.glassProfile(),
                tint = spec.tint,
                content = content,
            )
            return
        }

        val containerColor = spec.tint ?: MaterialTheme.colorScheme.surfaceContainerHigh
        val elevation = when (spec.role) {
            SurfaceRole.Hero -> 2.dp
            SurfaceRole.FeatureCard -> 1.dp
            SurfaceRole.StandardCard, SurfaceRole.CompactRow -> 0.dp
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
