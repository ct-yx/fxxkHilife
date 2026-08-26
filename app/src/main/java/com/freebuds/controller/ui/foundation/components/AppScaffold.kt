package com.freebuds.controller.ui.foundation.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import androidx.core.graphics.drawable.toBitmap
import com.freebuds.controller.i18n.i18n
import com.freebuds.controller.ui.UiDisplayMode
import com.freebuds.controller.ui.WallpaperScope
import com.freebuds.controller.ui.foundation.surface.LocalWallpaperGlass
import com.freebuds.controller.ui.foundation.surface.WallpaperGlassContext
import com.freebuds.controller.ui.foundation.surface.GlassProfile
import com.freebuds.controller.ui.foundation.surface.LightFieldState
import com.freebuds.controller.ui.foundation.surface.calculateWallpaperTransform
import com.freebuds.controller.ui.foundation.surface.rememberWallpaperTexture
import com.freebuds.controller.ui.foundation.surface.wallpaperGlassModifier
import com.freebuds.controller.util.LogBuffer

/** App-level shell for the conditional wallpaper-glass / Material 3 surface tree. */
@Composable
fun AppScaffold(
    displayMode: UiDisplayMode = UiDisplayMode.MATERIAL3,
    wallpaperUri: String? = null,
    wallpaperScope: WallpaperScope = WallpaperScope.ALL,
    route: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val hasWallpaperUri = !wallpaperUri.isNullOrBlank()
    // Wallpaper is an app-wide visual layer. Keep the legacy scope arguments in the API for
    // settings migration, but do not let an old persisted scope hide it on another route.
    val showWallpaper = hasWallpaperUri
    val wallpaperPainter = rememberAsyncImagePainter(
        model = if (showWallpaper) Uri.parse(wallpaperUri) else null,
    )
    val wallpaperError = wallpaperPainter.state as? AsyncImagePainter.State.Error
    LaunchedEffect(showWallpaper, wallpaperUri, wallpaperError) {
        if (showWallpaper && wallpaperError != null) {
            val sourceScheme = wallpaperUri?.let(Uri::parse)?.scheme ?: "unknown"
            LogBuffer.w(
                "Wallpaper",
                "load failed scheme=$sourceScheme error=${wallpaperError.result.throwable.javaClass.simpleName}",
            )
        }
    }
    val wallpaperResult = (wallpaperPainter.state as? AsyncImagePainter.State.Success)?.result
    val sourceBitmap = remember(wallpaperResult) {
        // Coil may decode the wallpaper as a hardware bitmap. The shared texture builder reads
        // pixels on Dispatchers.Default, so force a software bitmap before CPU sampling.
        wallpaperResult?.drawable?.toBitmap(config = android.graphics.Bitmap.Config.ARGB_8888)
    }
    val wallpaperReady = showWallpaper && sourceBitmap != null
    val contentBackground = if (showWallpaper && wallpaperError == null) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.background
    }
    val wallpaperColorFilter = if (showWallpaper && MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        ColorFilter.colorMatrix(
            ColorMatrix().apply {
                setToScale(0.75f, 0.75f, 0.75f, 1f)
            },
        )
    } else {
        null
    }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var rootOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val lightField = remember { LightFieldState() }
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val wallpaperTexture = rememberWallpaperTexture(sourceBitmap, isDarkTheme)
    val wallpaperTransform = wallpaperTexture?.let {
        calculateWallpaperTransform(it.sourceWidth, it.sourceHeight, viewportSize)
    }
    val glassContext = if (
        wallpaperReady &&
            wallpaperTexture != null &&
            wallpaperTransform != null &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    ) {
        remember(wallpaperTexture, wallpaperTransform, rootOrigin, isDarkTheme) {
            WallpaperGlassContext(
                texture = wallpaperTexture,
                transform = wallpaperTransform,
                rootOrigin = rootOrigin,
                isDark = isDarkTheme,
                lightField = lightField,
            )
        }
    } else {
        null
    }

    CompositionLocalProvider(LocalWallpaperGlass provides glassContext) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .onGloballyPositioned { rootOrigin = it.positionInRoot() }
                .pointerInput(lightField, rootOrigin, viewportSize) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: continue
                            when {
                                change.pressed -> lightField.updateFromPointer(
                                    positionInRoot = rootOrigin + change.position,
                                    viewport = viewportSize,
                                    isPressed = true,
                                )
                                event.type == PointerEventType.Move -> lightField.updateFromPointer(
                                    positionInRoot = rootOrigin + change.position,
                                    viewport = viewportSize,
                                    isPressed = false,
                                )
                                event.type == PointerEventType.Release -> lightField.release()
                            }
                        }
                    }
                }
                .then(Modifier.background(contentBackground)),
        ) {
            if (showWallpaper) {
                Image(
                    painter = wallpaperPainter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = wallpaperColorFilter,
                )
            }
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    displayMode: UiDisplayMode = UiDisplayMode.MATERIAL3,
    onBack: (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    val wallpaperContext = LocalWallpaperGlass.current
    val topBarGlass = wallpaperGlassModifier(GlassProfile.TopBar)
    val topBarShape = RoundedCornerShape(26.dp)
    val hasGlass = wallpaperContext != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    androidx.compose.material3.TopAppBar(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(topBarShape)
            .then(topBarGlass),
        title = { androidx.compose.material3.Text(title) },
        navigationIcon = {
            onBack?.let {
                androidx.compose.material3.IconButton(onClick = it) {
                    androidx.compose.material3.Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = i18n("common.back"),
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (hasGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
            scrolledContainerColor = if (hasGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}
