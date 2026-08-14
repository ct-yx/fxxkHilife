package com.freebuds.controller.ui.foundation.components

import android.net.Uri
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.filled.ArrowBack
import com.freebuds.controller.ui.UiDisplayMode
import com.freebuds.controller.ui.WallpaperScope
import com.freebuds.controller.i18n.i18n
import com.freebuds.controller.ui.foundation.surface.GlassHost

/** App-level shell: one background/source owner and one content layer. */
@Composable
fun AppScaffold(
    displayMode: UiDisplayMode,
    wallpaperUri: String? = null,
    wallpaperScope: WallpaperScope = WallpaperScope.ALL,
    route: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val showWallpaper = wallpaperUri != null && when (wallpaperScope) {
        WallpaperScope.ALL -> true
        WallpaperScope.HOME -> route == "home"
        WallpaperScope.SETTINGS -> route == "settings"
    }
    GlassHost(displayMode = displayMode, modifier = modifier.fillMaxSize(), background = {
        if (showWallpaper) {
            AsyncImage(
                model = Uri.parse(wallpaperUri),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = if (displayMode == UiDisplayMode.LIQUID_GLASS) 0.5f else 0.35f,
            )
        }
    }, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    displayMode: UiDisplayMode = UiDisplayMode.CLASSIC,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    androidx.compose.material3.TopAppBar(
        title = { androidx.compose.material3.Text(title) },
        navigationIcon = {
            onBack?.let {
                androidx.compose.material3.IconButton(onClick = it) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.filled.ArrowBack,
                        contentDescription = i18n("common.back"),
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (displayMode == UiDisplayMode.LIQUID_GLASS) {
                androidx.compose.ui.graphics.Color.Transparent
            } else {
                MaterialTheme.colorScheme.surface
            },
            scrolledContainerColor = if (displayMode == UiDisplayMode.LIQUID_GLASS) {
                androidx.compose.ui.graphics.Color.Transparent
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    )
}
