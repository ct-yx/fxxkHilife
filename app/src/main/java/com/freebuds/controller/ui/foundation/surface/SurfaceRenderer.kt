package com.freebuds.controller.ui.foundation.surface

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.freebuds.controller.ui.UiDisplayMode
import com.freebuds.controller.ui.foundation.tokens.UiTokens
import com.freebuds.controller.ui.glass.LocalLiquidGlassConfig
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

enum class SurfaceRole { Hero, FeatureCard, StandardCard, CompactRow, Dialog, AppBar }
enum class SurfaceRenderMode { Material3, HazeGlass, HazeBlur, TintOnly, Opaque }

@Immutable
data class SurfaceSpec(
    val role: SurfaceRole = SurfaceRole.StandardCard,
    val renderMode: SurfaceRenderMode? = null,
    val tint: Color? = null,
    val blurRadius: Dp = 22.dp,
    val cornerRadius: Dp = UiTokens.ref.radiusLarge,
    val depth: Float = 0.42f,
)

@Immutable
data class GlassRuntimeDiagnostics(
    val mode: UiDisplayMode,
    val requestedRenderer: SurfaceRenderMode,
    val renderer: SurfaceRenderMode,
    val backgroundAvailable: Boolean,
    val sourceAttached: Boolean,
    val effectSurfaceCount: Int,
    val apiLevel: Int,
    val hardwareAccelerated: Boolean,
    val fallbackReason: String? = null,
)

@Stable
class GlassHostState internal constructor(val hazeState: HazeState) {
    var sourceAttached by mutableStateOf(false)
        internal set
    var lastDiagnostics by mutableStateOf<GlassRuntimeDiagnostics?>(null)
        internal set
    var activeEffectSurfaceCount by mutableStateOf(0)
        internal set
}

val LocalGlassHost = staticCompositionLocalOf<GlassHostState?> { null }

@Composable
fun rememberGlassHostState(): GlassHostState {
    val hazeState = rememberHazeState()
    return remember(hazeState) { GlassHostState(hazeState) }
}

/** Owns the only source for a window. Background is drawn before the content tree. */
@Composable
fun GlassHost(
    displayMode: UiDisplayMode,
    backgroundAvailable: Boolean = false,
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val host = rememberGlassHostState()
    val sourceAttached = displayMode == UiDisplayMode.LIQUID_GLASS && backgroundAvailable
    SideEffect {
        host.sourceAttached = sourceAttached
    }
    val sourceModifier = if (sourceAttached) {
        Modifier.hazeSource(host.hazeState)
    } else {
        Modifier
    }
    CompositionLocalProvider(LocalGlassHost provides host) {
        Box(modifier = modifier) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(sourceModifier),
            ) { BackgroundLayer(content = background) }
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}

@Composable
fun BackgroundLayer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.background), content = content)
}

/**
 * The only production surface adapter. All renderers use the same content tree and shape; only
 * the material modifier changes. This makes an unavailable effect visible as tint/opaque rather
 * than leaving a transparent card with no background.
 */
@OptIn(ExperimentalHazeApi::class)
object SurfaceRenderer {
    @Composable
    fun Card(
        spec: SurfaceSpec,
        displayMode: UiDisplayMode,
        modifier: Modifier = Modifier,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        val host = LocalGlassHost.current
        val glassConfig = LocalLiquidGlassConfig.current
        val configuredSpec = spec.copy(
            cornerRadius = if (displayMode == UiDisplayMode.LIQUID_GLASS && spec.role in setOf(
                    SurfaceRole.Hero,
                    SurfaceRole.FeatureCard,
                    SurfaceRole.StandardCard,
                )
            ) glassConfig.cornerRadiusDp.dp else spec.cornerRadius,
            depth = (spec.depth * (glassConfig.depth / 0.42f) * (glassConfig.refractionStrength / 0.72f))
                .coerceIn(0.1f, 0.95f),
        )
        val view = LocalView.current
        // Keep the requested renderer independent from source availability. Diagnostics must show
        // that Liquid was requested but resolved to Material 3 when the wallpaper is still
        // loading or invalid; otherwise a fallback would look like a normal Material 3 choice.
        val requested = configuredSpec.renderMode ?: resolveMode(displayMode, configuredSpec.role)
        val actual = resolveActualMode(
            requested = requested,
            displayMode = displayMode,
            host = host,
            hardwareAccelerated = view.isHardwareAccelerated,
        )
        val shape = RoundedCornerShape(configuredSpec.cornerRadius)
        val tintBase = spec.tint ?: MaterialTheme.colorScheme.surfaceVariant
        val tintAlpha = if (spec.tint == null) glassConfig.tintAlpha else tintBase.alpha
        val tint = tintBase.copy(alpha = tintAlpha.coerceIn(0.04f, 0.92f))
        val hazeInput = host?.let {
            remember(it.hazeState) { HazeInput.Sources(state = it.hazeState) }
        }
        val glassStyle = if (actual == SurfaceRenderMode.HazeGlass) {
            remember(configuredSpec, glassConfig, tint, shape) {
                GlassStyle {
                    shape(shape)
                    backgroundColor(tint.copy(alpha = (tint.alpha * 0.62f).coerceIn(0.04f, 0.34f)))
                    tint(tint.copy(alpha = (tint.alpha * 0.72f).coerceIn(0.04f, 0.26f)))
                    optics(
                        GlassOptics.Fixed(
                            refractionStrength = glassConfig.refractionStrength.coerceIn(0f, 1f),
                            refractionHeightFraction = 0.28f,
                            refractionDisplacement = (8f + configuredSpec.depth * 12f).dp,
                            depth = configuredSpec.depth.coerceIn(0f, 1f),
                            blurRadius = configuredSpec.blurRadius,
                        ),
                    )
                    specularIntensity((0.30f + configuredSpec.depth * 0.34f).coerceIn(0f, 1f))
                    ambientResponse((0.28f + configuredSpec.depth * 0.36f).coerceIn(0f, 1f))
                    edgeSoftness(1.5.dp)
                    chromaticAberrationStrength((glassConfig.refractionStrength * 0.08f).coerceIn(0f, 1f))
                }
            }
        } else {
            null
        }
        val blurStyle = if (actual == SurfaceRenderMode.HazeBlur) {
            remember(configuredSpec, tint) {
                HazeBlurStyle {
                    blurRadius(configuredSpec.blurRadius)
                    noiseFactor(0.035f)
                    colorEffects(
                        listOf(
                            HazeColorEffect.tint(tint.copy(alpha = 0.14f)),
                        ),
                    )
                }
            }
        } else {
            null
        }
        val effectModifier = when {
            actual == SurfaceRenderMode.HazeGlass && hazeInput != null -> Modifier.hazeGlass(
                input = hazeInput,
                style = glassStyle ?: GlassStyle,
                performanceMode = HazePerformanceMode.Adaptive,
                expandLayerBounds = false,
            )
            actual == SurfaceRenderMode.HazeBlur && hazeInput != null -> Modifier.hazeBlur(
                input = hazeInput,
                style = blurStyle ?: HazeBlurStyle,
                performanceMode = HazePerformanceMode.Adaptive,
                expandLayerBounds = false,
            )
            else -> Modifier
        }
        val visibleColor = when (actual) {
            SurfaceRenderMode.Material3 -> MaterialTheme.colorScheme.surfaceVariant
            // Keep a visible tint below the effect. If the platform renderer cannot initialize,
            // the user still gets a readable surface instead of a transparent hole.
            SurfaceRenderMode.HazeGlass, SurfaceRenderMode.HazeBlur -> tint.copy(alpha = maxOf(tint.alpha, 0.12f))
            SurfaceRenderMode.TintOnly -> tint
            SurfaceRenderMode.Opaque -> MaterialTheme.colorScheme.surface
        }
        if (host != null) {
            DisposableEffect(host, actual) {
                if (actual.isEffect()) host.activeEffectSurfaceCount += 1
                onDispose {
                    if (actual.isEffect()) host.activeEffectSurfaceCount =
                        (host.activeEffectSurfaceCount - 1).coerceAtLeast(0)
                }
            }
            SideEffect {
                host.lastDiagnostics = GlassRuntimeDiagnostics(
                    mode = displayMode,
                    requestedRenderer = requested,
                    renderer = actual,
                    backgroundAvailable = host.sourceAttached,
                    sourceAttached = host.sourceAttached,
                    effectSurfaceCount = host.activeEffectSurfaceCount,
                    apiLevel = Build.VERSION.SDK_INT,
                    hardwareAccelerated = view.isHardwareAccelerated,
                    fallbackReason = fallbackReason(
                        requested = requested,
                        actual = actual,
                        displayMode = displayMode,
                        host = host,
                        hardwareAccelerated = view.isHardwareAccelerated,
                    ),
                )
            }
        }
        if (actual == SurfaceRenderMode.Material3) {
            androidx.compose.material3.Card(
                modifier = modifier,
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = visibleColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(UiTokens.ref.space4), content = content)
            }
        } else {
            Surface(
                modifier = modifier.clip(shape).then(effectModifier),
                shape = shape,
                color = visibleColor,
                border = if (actual.isEffect()) {
                    BorderStroke(
                        width = 0.7.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f + configuredSpec.depth * 0.10f),
                    )
                } else null,
                tonalElevation = if (actual == SurfaceRenderMode.Opaque) 1.dp else 0.dp,
            ) {
                Column(Modifier.padding(UiTokens.ref.space4), content = content)
            }
        }
    }

    private fun resolveMode(displayMode: UiDisplayMode, role: SurfaceRole): SurfaceRenderMode {
        if (displayMode == UiDisplayMode.CLASSIC) return SurfaceRenderMode.Material3
        if (role in setOf(
                SurfaceRole.StandardCard,
                SurfaceRole.CompactRow,
                SurfaceRole.Dialog,
                SurfaceRole.AppBar,
            )
        ) {
            // Long lists must not create one captured-input effect per row. Hero and the small
            // set of feature surfaces are the only automatic Glass candidates.
            return SurfaceRenderMode.TintOnly
        }
        return if (Build.VERSION.SDK_INT >= 33) SurfaceRenderMode.HazeGlass
        else if (Build.VERSION.SDK_INT >= 31) SurfaceRenderMode.HazeBlur
        else SurfaceRenderMode.TintOnly
    }

    private fun resolveActualMode(
        requested: SurfaceRenderMode,
        displayMode: UiDisplayMode,
        host: GlassHostState?,
        hardwareAccelerated: Boolean,
    ): SurfaceRenderMode {
        if (displayMode == UiDisplayMode.CLASSIC && requested.isEffect()) {
            return SurfaceRenderMode.Material3
        }
        if (displayMode == UiDisplayMode.LIQUID_GLASS && host?.sourceAttached != true) {
            return SurfaceRenderMode.Material3
        }
        if (host == null && requested.isEffect()) {
            return SurfaceRenderMode.TintOnly
        }
        if (!hardwareAccelerated && requested.isEffect()) {
            return SurfaceRenderMode.Opaque
        }
        if (requested == SurfaceRenderMode.HazeGlass && Build.VERSION.SDK_INT < 33) {
            return if (Build.VERSION.SDK_INT >= 31) SurfaceRenderMode.HazeBlur else SurfaceRenderMode.TintOnly
        }
        return requested
    }

    private fun fallbackReason(
        requested: SurfaceRenderMode,
        actual: SurfaceRenderMode,
        displayMode: UiDisplayMode,
        host: GlassHostState?,
        hardwareAccelerated: Boolean,
    ): String? = when {
        requested == actual -> null
        displayMode == UiDisplayMode.CLASSIC -> "display_mode"
        host?.sourceAttached != true -> "source_unavailable"
        !hardwareAccelerated && requested.isEffect() -> "hardware_acceleration"
        requested == SurfaceRenderMode.HazeGlass && Build.VERSION.SDK_INT < 33 -> "platform_capability"
        else -> "renderer_selection"
    }

    private fun SurfaceRenderMode.isEffect(): Boolean =
        this == SurfaceRenderMode.HazeGlass || this == SurfaceRenderMode.HazeBlur
}
