package com.freebuds.controller.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.ui.theme.loadThemeMode
import com.freebuds.controller.ui.loadWallpaperScope

enum class Screen {
    PermissionGuide, Home, Device, Gesture, Settings
}

@Composable
fun AppNavHost(viewModel: DeviceViewModel, onOpenTerminal: () -> Unit) {
    val context = LocalContext.current
    val connState by viewModel.connectionState.collectAsState()

    val themeMode = remember { loadThemeMode(context) }
    var currentTheme by remember { mutableStateOf(themeMode) }
    val savedWallpaper = remember {
        context.getSharedPreferences("fxxk_theme", android.content.Context.MODE_PRIVATE)
            .getString("wallpaper_uri", null)
    }
    var wallpaperUri by remember { mutableStateOf(savedWallpaper) }
    val savedScope = remember { loadWallpaperScope(context) }
    var wallpaperScope by remember { mutableStateOf(savedScope) }

    // 检查蓝牙权限
    val hasPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                .all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        } else true
    }

    var currentScreen by remember { mutableStateOf(
        if (!hasPermissions) Screen.PermissionGuide else Screen.Home
    ) }

    LaunchedEffect(connState) {
        if (connState is ConnectionState.Connected) {
            currentScreen = Screen.Device
        }
    }

    LaunchedEffect(connState) {
        if (connState is ConnectionState.Disconnected &&
            currentScreen != Screen.Home &&
            currentScreen != Screen.PermissionGuide) {
            currentScreen = Screen.Home
        }
    }

    when (currentScreen) {
        Screen.PermissionGuide -> PermissionGuideScreen(
            onGranted = { currentScreen = Screen.Home }
        )
        Screen.Home -> HomeScreen(
            viewModel = viewModel,
            onDeviceClick = { address ->
                viewModel.autoConnectSaved(address)
            },
            onRemoveDevice = { address ->
                viewModel.removeSavedDevice(address)
                if (viewModel.connectionState.value is ConnectionState.Connected) {
                    viewModel.disconnect()
                }
            },
            onSettings = { currentScreen = Screen.Settings },
        )
        Screen.Device -> DeviceScreen(
            viewModel = viewModel,
            onBack = { currentScreen = Screen.Home },
            onGesture = { currentScreen = Screen.Gesture },
            onSettings = { currentScreen = Screen.Settings },
            onOpenTerminal = onOpenTerminal,
        )
        Screen.Gesture -> GestureScreen(
            props = viewModel.props.collectAsState().value,
            viewModel = viewModel,
            onBack = { currentScreen = Screen.Device },
        )
        Screen.Settings -> SettingsScreen(
            viewModel = viewModel,
            onBack = {
                currentScreen = when (connState) {
                    is ConnectionState.Connected -> Screen.Device
                    else -> Screen.Home
                }
            },
            themeMode = currentTheme,
            onThemeChange = { currentTheme = it },
            wallpaperUri = wallpaperUri,
            onWallpaperChange = { wallpaperUri = it },
            wallpaperScope = wallpaperScope,
            onWallpaperScopeChange = { wallpaperScope = it },
        )
    }
}
