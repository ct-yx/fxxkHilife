package com.freebuds.controller.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.ui.glass.LiquidGlassConfig
import com.freebuds.controller.ui.glass.LocalLiquidGlassConfig
import com.freebuds.controller.ui.glass.loadLiquidGlassConfig
import com.freebuds.controller.ui.glass.saveLiquidGlassConfig
import com.freebuds.controller.ui.theme.ThemeMode
import com.freebuds.controller.ui.theme.loadThemeMode

private object Route {
    const val PermissionGuide = "permission_guide"
    const val Home = "home"
    const val Scan = "scan"
    const val Device = "device"
    const val Gesture = "gesture"
    const val Settings = "settings"
}

@Composable
fun AppNavHost(
    viewModel: DeviceViewModel,
    onOpenTerminal: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val connState by viewModel.connectionState.collectAsState()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val initialTheme = remember { loadThemeMode(context) }
    var currentTheme by remember { mutableStateOf(initialTheme) }

    val savedWallpaper = remember {
        context.getSharedPreferences("fxxk_theme", android.content.Context.MODE_PRIVATE)
            .getString("wallpaper_uri", null)
    }
    var wallpaperUri by remember { mutableStateOf(savedWallpaper) }
    val savedScope = remember { loadWallpaperScope(context) }
    var wallpaperScope by remember { mutableStateOf(savedScope) }

    val initialDisplayMode = remember { loadUiDisplayMode(context) }
    var displayMode by remember { mutableStateOf(initialDisplayMode) }
    var glassConfig by remember { mutableStateOf(loadLiquidGlassConfig(context)) }
    val hazeState = rememberHazeState()

    val hasPermissions = remember {
        val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                .all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        } else true
        val notificationGranted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        bluetoothGranted && notificationGranted
    }

    val startDestination = remember { if (!hasPermissions) Route.PermissionGuide else Route.Home }

    LaunchedEffect(connState) {
        if (connState is ConnectionState.Connected && currentRoute != Route.Device) {
            navController.navigate(Route.Device) { launchSingleTop = true }
        }
    }

    val showWallpaper = wallpaperUri != null && when (wallpaperScope) {
        WallpaperScope.ALL -> true
        WallpaperScope.HOME -> currentRoute == Route.Home
        WallpaperScope.SETTINGS -> currentRoute == Route.Settings
    }

    CompositionLocalProvider(LocalLiquidGlassConfig provides glassConfig) {
        Box(
            modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (showWallpaper) {
            AsyncImage(
                model = Uri.parse(wallpaperUri),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (displayMode == UiDisplayMode.LIQUID_GLASS) 0.5f else 0.35f),
                contentScale = ContentScale.Crop,
            )
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            composable(Route.PermissionGuide) {
                PermissionGuideScreen(
                    onGranted = {
                        navController.navigate(Route.Home) {
                            popUpTo(Route.PermissionGuide) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Route.Home) {
                HomeScreen(
                    viewModel = viewModel,
                    displayMode = displayMode,
                    hazeState = hazeState,
                    onDeviceClick = { address ->
                        viewModel.autoConnectSaved(address)
                        navController.navigate(Route.Device) { launchSingleTop = true }
                    },
                    onRemoveDevice = { address ->
                        viewModel.removeSavedDevice(address)
                        if (viewModel.connectionState.value is ConnectionState.Connected) {
                            viewModel.disconnect()
                        }
                    },
                    onSettings = { navController.navigate(Route.Settings) { launchSingleTop = true } },
                    onScan = { navController.navigate(Route.Scan) { launchSingleTop = true } },
                )
            }

            composable(Route.Scan) {
                ScanScreen(
                    viewModel = viewModel,
                    displayMode = displayMode,
                    hazeState = hazeState,
                    onBack = { navController.popBackStack() },
                    onDeviceSelected = { address ->
                        viewModel.autoConnectSaved(address)
                        navController.navigate(Route.Device) { launchSingleTop = true }
                    },
                )
            }

            composable(Route.Device) {
                DeviceScreen(
                    viewModel = viewModel,
                    displayMode = displayMode,
                    hazeState = hazeState,
                    onBack = {
                        navController.popBackStack(Route.Home, inclusive = false)
                    },
                    onGesture = { navController.navigate(Route.Gesture) { launchSingleTop = true } },
                    onSettings = { navController.navigate(Route.Settings) { launchSingleTop = true } },
                    onOpenTerminal = onOpenTerminal,
                )
            }

            composable(Route.Gesture) {
                GestureScreen(
                    props = viewModel.props.collectAsState().value,
                    viewModel = viewModel,
                    displayMode = displayMode,
                    hazeState = hazeState,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Route.Settings) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    themeMode = currentTheme,
                    onThemeChange = { mode ->
                        currentTheme = mode
                        onThemeChange(mode)
                    },
                    wallpaperUri = wallpaperUri,
                    onWallpaperChange = { wallpaperUri = it },
                    wallpaperScope = wallpaperScope,
                    onWallpaperScopeChange = { wallpaperScope = it },
                    displayMode = displayMode,
                    hazeState = hazeState,
                    glassConfig = glassConfig,
                    onGlassConfigChange = { config: LiquidGlassConfig ->
                        glassConfig = config
                        saveLiquidGlassConfig(context, config)
                    },
                    onDisplayModeChange = { mode ->
                        displayMode = mode
                        saveUiDisplayMode(context, mode)
                    },
                )
            }
        }
    }
    }
}
