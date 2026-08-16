package com.freebuds.controller.ui.foundation.surface

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListState
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
import com.freebuds.controller.ui.glass.GlassSurfaceProfile
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
import dev.chrisbanes.haze.glass.SurfaceProfile
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
    var isScrolling by mutableStateOf(false)
        private set
    var lastDiagnostics by mutableStateOf<GlassRuntimeDiagnostics?>(null)
        internal set
    var activeEffectSurfaceCount by mutableStateOf(0)
        internal set

    internal fun setScrolling(value: Boolean) {
        if (isScrolling != value) isScrolling = value
    }
}

val LocalGlassHost = staticCompositionLocalOf<GlassHostState?> { null }

@Composable
fun rememberGlassHostState(): GlassHostState {
    val hazeState = rememberHazeState()
    return remember(hazeState) { GlassHostState(hazeState) }
}

/**
 * Disables expensive captured-content effects while a lazy list is being dragged. The list keeps
 * the same translucent glass skin during the gesture and restores the real effect after the
 * gesture, so scrolling does not repeatedly re-render the full background source.
 */
@Composable
fun GlassScrollPerformance(listState: LazyListState) {
    val host = LocalGlassHost.current
    val isScrolling = listState.isScrollInProgress
    SideEffect { host?.setScrolling(isScrolling) }
    DisposableEffect(host) {
        onDispose { host?.setScrolling(false) }
    }
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
    val sourceModifier = if (sourceAttached && !host.isScrolling) {
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
        // when a caller outside the app shell has no source; the app shell itself supplies a
        // built-in colour field even when the optional user wallpaper is absent or invalid.
        val requested = configuredSpec.renderMode ?: resolveMode(displayMode, configuredSpec.role)
        val resolved = resolveActualMode(
            requested = requested,
            displayMode = displayMode,
            host = host,
            hardwareAccelerated = view.isHardwareAccelerated,
        )
        // A full captured-content effect is useful while still, but is the wrong trade-off for a
        // moving LazyColumn. TintOnly keeps the same surface geometry and contrast without a GPU
        // readback/blur pass on every frame.
        val actual = if (resolved.isEffect() && host?.isScrolling == true) {
            SurfaceRenderMode.TintOnly
        } else {
            resolved
        }
        val shape = RoundedCornerShape(configuredSpec.cornerRadius)
        val tintBase = spec.tint ?: MaterialTheme.colorScheme.surfaceVariant
        val minimumGlassAlpha = if (displayMode == UiDisplayMode.LIQUID_GLASS) {
            (0.18f + glassConfig.readabilityStrength * 0.20f + configuredSpec.depth * 0.04f)
                .coerceIn(0.18f, 0.42f)
        } else {
            0f
        }
        val tintAlpha = if (spec.tint == null) {
            maxOf(glassConfig.tintAlpha, minimumGlassAlpha)
        } else {
            tintBase.alpha
        }
        val tint = tintBase.copy(alpha = tintAlpha.coerceIn(0.04f, 0.92f))
        val glassBodyColor = tintBase.copy(alpha = minimumGlassAlpha.coerceAtLeast(0.20f))
        val glassTintColor = tintBase.copy(
            alpha = (0.10f + configuredSpec.depth * 0.10f).coerceIn(0.10f, 0.24f),
        )
        // Tint-only rows must not even allocate a source input. The old code created one for
        // every card, which added work during list composition without producing a renderer.
        val hazeInput = if (actual.isEffect()) {
            host?.let {
                remember(it.hazeState) { HazeInput.Sources(state = it.hazeState) }
            }
        } else {
            null
        }
        val glassStyle = if (actual == SurfaceRenderMode.HazeGlass) {
            remember(configuredSpec, glassConfig, tint, shape) {
                GlassStyle {
                    shape(shape)
                    backgroundColor(glassBodyColor)
                    tint(glassTintColor)
                    optics(
                        GlassOptics.Fixed(
                            refractionStrength = glassConfig.refractionStrength.coerceIn(0f, 1f),
                            refractionHeightFraction = 0.32f,
                            refractionDisplacement = (14f + configuredSpec.depth * 18f).dp,
                            depth = configuredSpec.depth.coerceIn(0f, 1f),
                            blurRadius = configuredSpec.blurRadius,
                        ),
                    )
                    surfaceProfile(
                        when (glassConfig.surfaceProfile) {
                            GlassSurfaceProfile.Circle -> SurfaceProfile.Circle
                            GlassSurfaceProfile.Rounded -> SurfaceProfile.Lip
                            GlassSurfaceProfile.Squircle -> SurfaceProfile.Squircle
                        },
                    )
                    specularIntensity((0.30f + configuredSpec.depth * 0.34f).coerceIn(0f, 1f))
                    ambientResponse((0.28f + configuredSpec.depth * 0.36f).coerceIn(0f, 1f))
                    edgeSoftness(1.5.dp)
                    chromaticAberrationStrength((glassConfig.refractionStrength * 0.16f).coerceIn(0f, 1f))
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
            // Keep an explicit translucent body below the effect. This is also the cheap glass
            // skin used during scrolling, so the visual hierarchy does not disappear mid-drag.
            SurfaceRenderMode.HazeGlass, SurfaceRenderMode.HazeBlur -> glassBodyColor
            SurfaceRenderMode.TintOnly -> if (displayMode == UiDisplayMode.LIQUID_GLASS) {
                glassBodyColor
            } else {
                tint
            }
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
                        resolved = resolved,
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
                border = if (actual.isEffect() || (displayMode == UiDisplayMode.LIQUID_GLASS && actual == SurfaceRenderMode.TintOnly)) {
                    BorderStroke(
                        width = 0.7.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (actual.isEffect()) {
                                0.16f + configuredSpec.depth * 0.12f
                            } else {
                                0.10f + configuredSpec.depth * 0.06f
                            },
                        ),
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
        resolved: SurfaceRenderMode,
        displayMode: UiDisplayMode,
        host: GlassHostState?,
        hardwareAccelerated: Boolean,
    ): String? = when {
        requested == actual -> null
        resolved.isEffect() && host?.isScrolling == true -> "scrolling_performance_mode"
        displayMode == UiDisplayMode.CLASSIC -> "display_mode"
        host?.sourceAttached != true -> "source_unavailable"
        !hardwareAccelerated && requested.isEffect() -> "hardware_acceleration"
        requested == SurfaceRenderMode.HazeGlass && Build.VERSION.SDK_INT < 33 -> "platform_capability"
        else -> "renderer_selection"
    }

    private fun SurfaceRenderMode.isEffect(): Boolean =
        this == SurfaceRenderMode.HazeGlass || this == SurfaceRenderMode.HazeBlur
}
