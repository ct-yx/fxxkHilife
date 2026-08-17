package com.freebuds.controller.ui.foundation.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import com.freebuds.controller.i18n.i18n
import com.freebuds.controller.ui.UiDisplayMode
import com.freebuds.controller.ui.WallpaperScope

/** App-level shell for the standard Material 3 surface tree. */
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
    val contentBackground = if (showWallpaper) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.background
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(contentBackground),
    ) {
        if (showWallpaper) {
            AsyncImage(
                model = Uri.parse(wallpaperUri),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 1f,
            )
        }
        Box(Modifier.fillMaxSize(), content = content)
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
    androidx.compose.material3.TopAppBar(
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
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}
