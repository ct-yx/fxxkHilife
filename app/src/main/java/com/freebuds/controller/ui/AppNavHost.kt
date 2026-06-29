package com.freebuds.controller.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.ui.theme.loadThemeMode
import com.freebuds.controller.ui.theme.ThemeMode
import com.freebuds.controller.ui.loadWallpaperScope

enum class Screen {
    PermissionGuide, Home, Scan, Device, Gesture, Settings
}

@Composable
fun AppNavHost(
    viewModel: DeviceViewModel,
    onOpenTerminal: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val connState by viewModel.connectionState.collectAsState()

    // 主题由 MainActivity 管理，AppNavHost 通过 onThemeChange 回调通知 MainActivity
    // currentTheme 从 SharedPreferences 加载初始值，后续由 SettingsScreen 回调更新
    val initialTheme = remember { loadThemeMode(context) }
    var currentTheme by remember { mutableStateOf(initialTheme) }
    val savedWallpaper = remember {
        context.getSharedPreferences("fxxk_theme", android.content.Context.MODE_PRIVATE)
            .getString("wallpaper_uri", null)
    }
    var wallpaperUri by remember { mutableStateOf(savedWallpaper) }
    val savedScope = remember { loadWallpaperScope(context) }
    var wallpaperScope by remember { mutableStateOf(savedScope) }

    // 检查权限：蓝牙为必需；Android 13+ 通知权限缺失时也展示引导页，但允许用户稍后继续。
    val hasPermissions = remember {
        val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                .all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        } else true
        val notificationGranted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        bluetoothGranted && notificationGranted
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
            currentScreen != Screen.Scan &&
            currentScreen != Screen.PermissionGuide &&
            !currentScreen.name.startsWith(Screen.Gesture.name)) {
            currentScreen = Screen.Home
        }
    }

    val showWallpaper = wallpaperUri != null && when (wallpaperScope) {
        WallpaperScope.ALL -> true
        WallpaperScope.HOME -> currentScreen == Screen.Home
        WallpaperScope.SETTINGS -> currentScreen == Screen.Settings
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (showWallpaper) {
            AsyncImage(
                model = Uri.parse(wallpaperUri),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.35f),
                contentScale = ContentScale.Crop,
            )
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
            onScan = { currentScreen = Screen.Scan },
        )
        Screen.Scan -> ScanScreen(
            viewModel = viewModel,
            onBack = { currentScreen = Screen.Home },
            onDeviceSelected = { address ->
                viewModel.autoConnectSaved(address)
                currentScreen = Screen.Device
            },
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
            onThemeChange = { mode ->
                currentTheme = mode
                onThemeChange(mode)
            },
            wallpaperUri = wallpaperUri,
            onWallpaperChange = { wallpaperUri = it },
            wallpaperScope = wallpaperScope,
            onWallpaperScopeChange = { wallpaperScope = it },
        )
        }
    }
}
