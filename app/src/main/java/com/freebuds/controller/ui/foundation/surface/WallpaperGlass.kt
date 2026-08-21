package com.freebuds.controller.ui.foundation.surface

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.composed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.freebuds.controller.ui.foundation.tokens.UiTokens
import com.freebuds.controller.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

internal enum class GlassProfile {
    Compact,
    Standard,
    Feature,
    Hero,
    TopBar,
}

internal data class WallpaperCropTransform(
    val scale: Float,
    val offset: Offset,
)

internal class WallpaperTexture(
    val bitmap: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

internal class WallpaperGlassContext(
    val texture: WallpaperTexture,
    val transform: WallpaperCropTransform,
    val rootOrigin: Offset,
    val isDark: Boolean,
)

internal val LocalWallpaperGlass = androidx.compose.runtime.staticCompositionLocalOf<WallpaperGlassContext?> { null }

internal fun calculateWallpaperTransform(
    sourceWidth: Int,
    sourceHeight: Int,
    viewport: IntSize,
): WallpaperCropTransform? {
    if (sourceWidth <= 0 || sourceHeight <= 0 || viewport.width <= 0 || viewport.height <= 0) return null
    val scale = max(
        viewport.width.toFloat() / sourceWidth,
        viewport.height.toFloat() / sourceHeight,
    )
    return WallpaperCropTransform(
        scale = scale,
        offset = Offset(
            (viewport.width - sourceWidth * scale) / 2f,
            (viewport.height - sourceHeight * scale) / 2f,
        ),
    )
}

internal fun calculateWallpaperTextureSize(sourceWidth: Int, sourceHeight: Int): IntSize {
    val safeWidth = sourceWidth.coerceAtLeast(1)
    val safeHeight = sourceHeight.coerceAtLeast(1)
    val longEdge = max(safeWidth, safeHeight)
    val targetLongEdge = (longEdge / 8f).coerceIn(128f, 320f)
    val ratio = targetLongEdge / longEdge
    return IntSize(
        width = (safeWidth * ratio).roundToInt().coerceAtLeast(1),
        height = (safeHeight * ratio).roundToInt().coerceAtLeast(1),
    )
}

internal fun buildWallpaperTexture(source: Bitmap, darkTheme: Boolean): WallpaperTexture {
    val textureSize = calculateWallpaperTextureSize(source.width, source.height)
    val targetWidth = textureSize.width
    val targetHeight = textureSize.height
    val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    val pixels = IntArray(targetWidth * targetHeight)
    scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

    if (darkTheme) {
        for (index in pixels.indices) {
            val color = pixels[index]
            pixels[index] = android.graphics.Color.argb(
                android.graphics.Color.alpha(color),
                (android.graphics.Color.red(color) * 0.75f).roundToInt(),
                (android.graphics.Color.green(color) * 0.75f).roundToInt(),
                (android.graphics.Color.blue(color) * 0.75f).roundToInt(),
            )
        }
    }

    repeat(3) {
        boxBlurPass(pixels, targetWidth, targetHeight, radius = 2)
    }

    val blurred = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    blurred.setPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
    if (scaled !== source && !scaled.isRecycled) scaled.recycle()
    return WallpaperTexture(blurred, source.width, source.height)
}

private fun boxBlurPass(pixels: IntArray, width: Int, height: Int, radius: Int) {
    val source = pixels.copyOf()
    for (y in 0 until height) {
        for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            var alpha = 0
            var count = 0
            for (sampleY in (y - radius).coerceAtLeast(0)..(y + radius).coerceAtMost(height - 1)) {
                for (sampleX in (x - radius).coerceAtLeast(0)..(x + radius).coerceAtMost(width - 1)) {
                    val color = source[sampleY * width + sampleX]
                    red += android.graphics.Color.red(color)
                    green += android.graphics.Color.green(color)
                    blue += android.graphics.Color.blue(color)
                    alpha += android.graphics.Color.alpha(color)
                    count++
                }
            }
            pixels[y * width + x] = android.graphics.Color.argb(
                alpha / count,
                red / count,
                green / count,
                blue / count,
            )
        }
    }
}

@Composable
internal fun rememberWallpaperTexture(
    source: Bitmap?,
    viewport: IntSize,
    darkTheme: Boolean,
): WallpaperTexture? {
    var texture by remember(source, darkTheme) { mutableStateOf<WallpaperTexture?>(null) }
    LaunchedEffect(source, darkTheme, viewport) {
        texture = if (source == null || viewport.width <= 0 || viewport.height <= 0) {
            null
        } else {
            withContext(Dispatchers.Default) {
                buildWallpaperTexture(source, darkTheme)
            }
        }
    }
    DisposableEffect(texture) {
        onDispose {
            texture?.bitmap?.let { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }
    return texture
}

internal data class GlassVisual(
    val cornerRadius: Dp,
    val edgeBand: Dp,
    val displacement: Dp,
    val bodyAlpha: Float,
    val tintStrength: Float,
    val specular: Float,
    val distortion: Float,
    val topCornerRadius: Dp = cornerRadius,
    val bottomCornerRadius: Dp = cornerRadius,
)

private class RuntimeShaderBrush(private val shader: Shader) : ShaderBrush() {
    override fun createShader(size: Size): Shader = shader
}

internal fun GlassProfile.visual(): GlassVisual = when (this) {
    GlassProfile.Compact -> GlassVisual(20.dp, 9.dp, 5.dp, 0.70f, 0.20f, 0.34f, 0f)
    GlassProfile.Standard -> GlassVisual(22.dp, 10.dp, 6.dp, 0.72f, 0.22f, 0.40f, 0f)
    GlassProfile.Feature -> GlassVisual(26.dp, 12.dp, 7.dp, 0.76f, 0.24f, 0.46f, 0f)
    GlassProfile.Hero -> GlassVisual(28.dp, 12.dp, 9.dp, 0.78f, 0.26f, 0.52f, 0f)
    GlassProfile.TopBar -> GlassVisual(
        cornerRadius = 22.dp,
        edgeBand = 16.dp,
        displacement = 9.dp,
        bodyAlpha = 0.82f,
        tintStrength = 0.30f,
        specular = 0.58f,
        distortion = 2.5f,
        topCornerRadius = 0.dp,
        bottomCornerRadius = 22.dp,
    )
}

@Composable
internal fun WallpaperGlassSurface(
    modifier: Modifier,
    shape: RoundedCornerShape,
    profile: GlassProfile,
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalWallpaperGlass.current
    val visual = profile.visual()
    val density = LocalDensity.current
    val canUseAgsl = host != null && Build.VERSION.SDK_INT >= 33
    val resolvedShape = if (profile == GlassProfile.TopBar) {
        RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomStart = visual.cornerRadius,
            bottomEnd = visual.cornerRadius,
        )
    } else {
        shape
    }

    val effectModifier = if (canUseAgsl) {
        val fallbackTint = if (host!!.isDark) Color.Black else Color.White
        Modifier.wallpaperGlassEffect(host, visual, density, tint ?: fallbackTint)
    } else Modifier

    Box(
        modifier = modifier
            .clip(resolvedShape)
            .then(effectModifier)
            .border(
                width = if (canUseAgsl) 1.dp else 1.dp,
                color = if (canUseAgsl) {
                    Color.White.copy(alpha = if (host!!.isDark) 0.18f else 0.34f)
                } else {
                    androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
                },
                shape = resolvedShape,
            ),
    ) {
        Column(Modifier.padding(UiTokens.ref.space4), content = content)
    }
}

@Composable
internal fun wallpaperGlassModifier(profile: GlassProfile): Modifier {
    val host = LocalWallpaperGlass.current
    return if (host != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val tint = if (host.isDark) Color.Black else Color.White
        Modifier.wallpaperGlassEffect(host, profile.visual(), LocalDensity.current, tint)
    } else {
        Modifier
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun Modifier.wallpaperGlassEffect(
    host: WallpaperGlassContext,
    visual: GlassVisual,
    density: androidx.compose.ui.unit.Density,
    tintColor: Color,
): Modifier = WallpaperGlassRenderer.render(this, host, visual, density, tintColor)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object WallpaperGlassRenderer {
    fun render(
        base: Modifier,
        host: WallpaperGlassContext,
        visual: GlassVisual,
        renderDensity: androidx.compose.ui.unit.Density,
        tintColor: Color,
    ): Modifier = base.composed {
        val context = LocalContext.current
        val shaderSource = remember(context) {
            context.resources.openRawResource(R.raw.wallpaper_glass).bufferedReader().use { it.readText() }
        }
        var bounds by remember { mutableStateOf(Rect.Zero) }
        base
            .onGloballyPositioned { coordinates: LayoutCoordinates ->
                bounds = coordinates.boundsInRoot()
            }
            .drawWithCache {
                val shader = RuntimeShader(shaderSource)
            val bitmapShader = BitmapShader(
                host.texture.bitmap,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP,
            )
            shader.setInputShader("wallpaper", bitmapShader)
            val brush = RuntimeShaderBrush(shader)
            val matrix = Matrix()
            val topCornerRadiusPx = with(renderDensity) { visual.topCornerRadius.toPx() }
            val bottomCornerRadiusPx = with(renderDensity) { visual.bottomCornerRadius.toPx() }
            val edgeBandPx = with(renderDensity) { visual.edgeBand.toPx() }
            val displacementPx = with(renderDensity) { visual.displacement.toPx() }

            onDrawBehind {
                val rootX = bounds.left - host.rootOrigin.x
                val rootY = bounds.top - host.rootOrigin.y
                val transform = host.transform
                val scaleX = host.texture.bitmap.width / (host.texture.sourceWidth * transform.scale)
                val scaleY = host.texture.bitmap.height / (host.texture.sourceHeight * transform.scale)
                matrix.setScale(scaleX, scaleY)
                matrix.postTranslate(
                    (rootX - transform.offset.x) * scaleX,
                    (rootY - transform.offset.y) * scaleY,
                )
                bitmapShader.setLocalMatrix(matrix)

                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setFloatUniform(
                    "cornerRadii",
                    topCornerRadiusPx,
                    topCornerRadiusPx,
                    bottomCornerRadiusPx,
                    bottomCornerRadiusPx,
                )
                shader.setFloatUniform("edgeBand", edgeBandPx)
                shader.setFloatUniform("displacement", displacementPx)
                shader.setFloatUniform("bodyAlpha", visual.bodyAlpha)
                shader.setFloatUniform("tintStrength", visual.tintStrength)
                shader.setFloatUniform("specular", visual.specular)
                shader.setFloatUniform("distortion", with(renderDensity) { visual.distortion.toPx() })
                shader.setFloatUniform(
                    "tintColor",
                    floatArrayOf(tintColor.red, tintColor.green, tintColor.blue, 1f),
                )
                val glassPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(Offset.Zero, size),
                            topLeft = CornerRadius(topCornerRadiusPx, topCornerRadiusPx),
                            topRight = CornerRadius(topCornerRadiusPx, topCornerRadiusPx),
                            bottomRight = CornerRadius(bottomCornerRadiusPx, bottomCornerRadiusPx),
                            bottomLeft = CornerRadius(bottomCornerRadiusPx, bottomCornerRadiusPx),
                        ),
                    )
                }
                drawPath(
                    path = glassPath,
                    brush = brush,
                )
            }
            }
    }
}
