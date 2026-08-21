package com.freebuds.controller.ui.foundation.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.freebuds.controller.ui.foundation.surface.calculateWallpaperTransform
import com.freebuds.controller.ui.foundation.surface.rememberWallpaperTexture
import com.freebuds.controller.ui.foundation.surface.wallpaperGlassModifier

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
    val showWallpaper = hasWallpaperUri && when (wallpaperScope) {
        WallpaperScope.ALL -> true
        WallpaperScope.HOME -> route == "home"
        WallpaperScope.SETTINGS -> route == "settings"
    }
    val wallpaperPainter = rememberAsyncImagePainter(
        model = if (showWallpaper) Uri.parse(wallpaperUri) else null,
    )
    val wallpaperResult = (wallpaperPainter.state as? AsyncImagePainter.State.Success)?.result
    val sourceBitmap = remember(wallpaperResult) {
        wallpaperResult?.drawable?.toBitmap()
    }
    val wallpaperReady = showWallpaper && sourceBitmap != null
    val contentBackground = if (wallpaperReady) Color.Transparent else MaterialTheme.colorScheme.background
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
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val wallpaperTexture = rememberWallpaperTexture(sourceBitmap, viewportSize, isDarkTheme)
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
                .then(Modifier.background(contentBackground)),
        ) {
            if (wallpaperReady) {
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
    val topBarShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = 22.dp,
        bottomEnd = 22.dp,
    )
    val hasGlass = wallpaperContext != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    androidx.compose.material3.TopAppBar(
        modifier = topBarGlass.clip(topBarShape),
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
