package com.freebuds.controller.ui.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.freebuds.controller.ui.UiDisplayMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.min

/** Surface profile used by [LiquidGlassCard]. */
enum class GlassSurfaceProfile {
    Rounded,
    Squircle,
    Circle,
}

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

/**
 * fxxkHilife liquid-glass card.
 *
 * Parent usage:
 * ```kotlin
 * val hazeState = rememberHazeState()
 * Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
 *     BackgroundWallpaperOrGradient()
 *     LiquidGlassCard(hazeState = hazeState) { ... }
 * }
 * ```
 *
 * Haze 2.0 is used as the blur pipeline, while the official `haze-liquidglass`
 * artifact is still unpublished. The latest renderer therefore keeps
 * `hazeSource` + `hazeEffect { blurEffect { ... } }` and layers Flowmix-inspired
 * edge optics on top: normal-like edge highlights, ambient rims, subtle chromatic
 * edges, and no full-card white overlay.
 *
 * Performance notes:
 * - Android 12+ has the best blur/render-effect path.
 * - Avoid stacking many overlapping `hazeEffect` surfaces in the same viewport.
 * - Prefer `AdaptiveCard`: classic mode remains a cheap Material3 card.
 * - On low-end devices, reduce [refractionStrength], [depth], or card count.
 *
 * @param tint translucent glass tint. Recommended alpha: 0.08f..0.15f.
 * @param refractionStrength visual bending/highlight intensity. Recommended: 0.6f..0.85f.
 * @param depth perceived glass thickness. Recommended: 0.3f..0.5f.
 * @param shape clipping shape for the glass body.
 * @param surfaceProfile controls edge radius/highlight profile used by the custom renderer.
 */
@Composable
fun LiquidGlassCard(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    refractionStrength: Float? = null,
    depth: Float? = null,
    shape: Shape? = null,
    cornerRadius: Dp? = null,
    surfaceProfile: GlassSurfaceProfile? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val config = LocalLiquidGlassConfig.current
    val requestedTint = tint ?: Color.White.copy(alpha = config.tintAlpha)
    val effectiveRefraction = refractionStrength ?: config.refractionStrength
    val effectiveDepth = depth ?: config.depth
    val effectiveCornerRadius = cornerRadius ?: config.cornerRadiusDp.dp
    val effectiveShape = shape ?: RoundedCornerShape(effectiveCornerRadius)
    val effectiveSurfaceProfile = surfaceProfile ?: config.surfaceProfile
    val safeRefraction = effectiveRefraction.coerceIn(0f, 1f)
    val safeDepth = effectiveDepth.coerceIn(0f, 1f)
    val effectiveTint = requestedTint.copy(alpha = min(requestedTint.alpha, 0.020f + 0.030f * safeRefraction))
    val primaryTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.018f * safeRefraction)
    val legacyOptics = config.rendererMode == GlassRendererMode.LEGACY_COMPAT

    Surface(
        modifier = modifier
            .clip(effectiveShape)
            .hazeEffect(state = hazeState) {
                blurEffect {
                    blurRadius = (20 + 18 * safeRefraction).dp
                    noiseFactor = 0.035f + 0.075f * safeDepth
                    colorEffects = listOf(
                        HazeColorEffect.tint(effectiveTint),
                        HazeColorEffect.tint(primaryTint),
                    )
                }
            }
            .liquidGlassOptics(
                cornerRadius = effectiveCornerRadius,
                refractionStrength = safeRefraction,
                depth = safeDepth,
                surfaceProfile = effectiveSurfaceProfile,
                drawInteriorAccents = legacyOptics,
                flowmixInspired = !legacyOptics,
            ),
        shape = effectiveShape,
        color = Color.White.copy(alpha = 0.004f + 0.010f * safeDepth),
        border = BorderStroke(0.45.dp, Color.White.copy(alpha = 0.10f + 0.10f * safeDepth)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
    val config = LocalLiquidGlassConfig.current
    val legacyOptics = config.rendererMode == GlassRendererMode.LEGACY_COMPAT
    if (displayMode == UiDisplayMode.LIQUID_GLASS && hazeState != null) {
        Box(
            modifier = modifier
                .clip(shape)
                .hazeEffect(state = hazeState) {
                    blurEffect {
                        blurRadius = 26.dp
                        noiseFactor = 0.052f
                        colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(alpha = if (legacyOptics) 0.035f else 0.018f)))
                    }
                }
                .liquidGlassOptics(
                    cornerRadius = 28.dp,
                    refractionStrength = 0.66f,
                    depth = 0.34f,
                    surfaceProfile = GlassSurfaceProfile.Rounded,
                    drawInteriorAccents = legacyOptics,
                    flowmixInspired = !legacyOptics,
                )
                .background(Color.White.copy(alpha = if (legacyOptics) 0.008f else 0.003f), shape)
                .padding(4.dp),
            content = content,
        )
    } else {
        Box(modifier = modifier, content = content)
    }
}

private fun Modifier.liquidGlassOptics(
    cornerRadius: Dp,
    refractionStrength: Float,
    depth: Float,
    surfaceProfile: GlassSurfaceProfile,
    drawInteriorAccents: Boolean = true,
    flowmixInspired: Boolean = false,
): Modifier = this.drawWithCache {
    val minSide = min(size.width, size.height)
    val radiusPx = when (surfaceProfile) {
        GlassSurfaceProfile.Circle -> minSide / 2f
        GlassSurfaceProfile.Squircle -> cornerRadius.toPx() * 1.18f
        GlassSurfaceProfile.Rounded -> cornerRadius.toPx()
    }
    val radius = CornerRadius(radiusPx, radiusPx)
    val mainStroke = (0.85.dp + 0.65.dp * depth).toPx()
    val hairline = 0.55.dp.toPx()

    val chromaticEdge = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.40f + 0.22f * refractionStrength),
            Color(0xFF8DEBFF).copy(alpha = 0.24f + 0.16f * refractionStrength),
            Color(0xFFFF8BD1).copy(alpha = 0.18f + 0.18f * refractionStrength),
            Color.White.copy(alpha = 0.20f),
        ),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    val specularHighlight = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.42f * refractionStrength),
            Color.White.copy(alpha = 0.10f * refractionStrength),
            Color.Transparent,
        ),
        start = Offset.Zero,
        end = Offset(size.width * 0.72f, size.height * 0.38f),
    )
    val refractedRibbon = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(alpha = 0.11f * refractionStrength),
            Color(0xFFB5F3FF).copy(alpha = 0.07f * refractionStrength),
            Color.Transparent,
        ),
        start = Offset(size.width * 0.08f, size.height),
        end = Offset(size.width, size.height * 0.18f),
    )
    val darkFresnelEdge = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.10f * depth),
        ),
        start = Offset(size.width * 0.25f, size.height * 0.25f),
        end = Offset(size.width, size.height),
    )
    val flowmixNormalHighlight = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.50f * refractionStrength),
            Color.White.copy(alpha = 0.18f * refractionStrength),
            Color.Transparent,
            Color.Black.copy(alpha = 0.05f * depth),
        ),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    val flowmixAmbientEdge = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.26f * depth),
            Color.Transparent,
            Color.Black.copy(alpha = 0.08f * depth),
        ),
        start = Offset(size.width * 0.12f, size.height * 0.02f),
        end = Offset(size.width * 0.92f, size.height * 0.96f),
    )
    val flowmixChromaticEdge = Brush.linearGradient(
        colors = listOf(
            Color(0xFFC8FBFF).copy(alpha = 0.22f * refractionStrength),
            Color.White.copy(alpha = 0.18f * refractionStrength),
            Color(0xFFFF9AD8).copy(alpha = 0.16f * refractionStrength),
            Color.Transparent,
        ),
        start = Offset(size.width * 0.04f, size.height * 0.02f),
        end = Offset(size.width * 0.98f, size.height * 0.92f),
    )

    onDrawWithContent {
        drawContent()
        // Draw optical accents only. Do not fill the whole card with white:
        // the background seen through haze must remain the main material.
        if (drawInteriorAccents) {
            drawLine(
                brush = specularHighlight,
                start = Offset(size.width * 0.08f, size.height * 0.10f),
                end = Offset(size.width * 0.72f, size.height * 0.18f),
                strokeWidth = mainStroke * 1.8f,
                cap = StrokeCap.Round,
            )
            drawLine(
                brush = refractedRibbon,
                start = Offset(size.width * 0.10f, size.height * 0.92f),
                end = Offset(size.width * 0.96f, size.height * 0.26f),
                strokeWidth = mainStroke * 1.15f,
                cap = StrokeCap.Round,
            )
        }
        if (flowmixInspired) {
            drawRoundRect(
                brush = flowmixAmbientEdge,
                cornerRadius = radius,
                style = Stroke(width = mainStroke * 2.15f),
            )
            drawRoundRect(
                brush = flowmixNormalHighlight,
                cornerRadius = radius,
                style = Stroke(width = mainStroke * 1.45f),
            )
            drawRoundRect(
                brush = flowmixChromaticEdge,
                cornerRadius = radius,
                style = Stroke(width = hairline * 1.35f),
            )
        }
        drawRoundRect(
            brush = darkFresnelEdge,
            cornerRadius = radius,
            style = Stroke(width = mainStroke * 1.25f),
        )
        drawRoundRect(
            brush = chromaticEdge,
            cornerRadius = radius,
            style = Stroke(width = mainStroke),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.16f + 0.10f * depth),
            cornerRadius = radius,
            style = Stroke(width = hairline),
        )
    }
}

/**
 * Preview showing the required parent structure:
 * 1. `rememberHazeState()` in the parent.
 * 2. `.hazeSource(hazeState)` on the background container.
 * 3. Multiple `LiquidGlassCard` surfaces reading from the same haze state.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 720)
@Composable
private fun LiquidGlassCardPreview() {
    val hazeState = rememberHazeState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF101828),
                        Color(0xFF315EFB),
                        Color(0xFFFF7AB6),
                        Color(0xFFFFD166),
                    )
                )
            )
            .padding(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            LiquidGlassCard(
                hazeState = hazeState,
                tint = Color.White.copy(alpha = 0.13f),
                refractionStrength = 0.82f,
                depth = 0.46f,
            ) {
                Text("FreeBuds Pro", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("降噪 · 动态 / 低延迟已开启")
            }
            LiquidGlassCard(
                hazeState = hazeState,
                tint = Color(0xFFDDF8FF).copy(alpha = 0.11f),
                refractionStrength = 0.68f,
                depth = 0.34f,
                shape = RoundedCornerShape(36.dp),
                cornerRadius = 36.dp,
            ) {
                Text("液态玻璃卡片", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("边缘高光、Fresnel 暗边、内部折射光带")
            }
            LiquidGlassCard(
                hazeState = hazeState,
                tint = Color.White.copy(alpha = 0.10f),
                refractionStrength = 0.74f,
                depth = 0.40f,
                shape = CircleShape,
                cornerRadius = 96.dp,
                surfaceProfile = GlassSurfaceProfile.Circle,
                modifier = Modifier.width(180.dp),
            ) {
                Text("Circle")
                Text("Profile")
            }
        }
    }
}
