package com.freebuds.controller.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.i18n.I18n
import com.freebuds.controller.i18n.LocalI18n
import com.freebuds.controller.ui.foundation.components.AppScaffold
import com.freebuds.controller.ui.glass.LocalLiquidGlassConfig
import com.freebuds.controller.ui.state.SettingsEvent
import com.freebuds.controller.ui.theme.ThemeMode

private object Route {
    const val PermissionGuide = "permission_guide"
    const val Home = "home"
    const val Scan = "scan"
    const val Device = "device"
    const val Gesture = "gesture"
    const val ListeningStats = "listening_stats"
    const val Settings = "settings"
}

@Composable
fun AppNavHost(
    viewModel: DeviceViewModel,
    onOpenTerminal: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    var userLeftDevice by remember { mutableStateOf(false) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val settingsState by viewModel.settingsState.collectAsState()
    val currentLocale = settingsState.locale
    val wallpaperUri = settingsState.wallpaperUri
    val wallpaperScope = settingsState.wallpaperScope
    val displayMode = settingsState.displayMode
    val glassConfig = settingsState.glassConfig
    val deviceUiState by viewModel.deviceUiState.collectAsState()

    val hasPermissions = remember {
        val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                .all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        } else {
            true
        }
        val notificationGranted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        bluetoothGranted && notificationGranted
    }
    val startDestination = remember { if (!hasPermissions) Route.PermissionGuide else Route.Home }

    LaunchedEffect(deviceUiState.connection, currentRoute, userLeftDevice) {
        if (deviceUiState.connection.isReady && currentRoute == Route.Home && !userLeftDevice) {
            navController.navigate(Route.Device) { launchSingleTop = true }
        }
        if (!deviceUiState.connection.isReady) {
            userLeftDevice = false
        }
    }

    CompositionLocalProvider(
        LocalLiquidGlassConfig provides glassConfig,
        LocalI18n provides I18n.provider(currentLocale),
    ) {
        AppScaffold(
            displayMode = displayMode,
            wallpaperUri = wallpaperUri,
            wallpaperScope = wallpaperScope,
            route = currentRoute,
        ) {
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
                        },
                    )
                }

                composable(Route.Home) {
                    HomeScreen(
                        viewModel = viewModel,
                        displayMode = displayMode,
                        onDeviceClick = { address -> viewModel.autoConnectSaved(address) },
                        onRemoveDevice = { address ->
                            viewModel.removeSavedDevice(address)
                            if (deviceUiState.connection.address == address) {
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
                        onBack = { navController.popBackStack() },
                        onDeviceSelected = { address ->
                            viewModel.autoConnectSaved(address)
                            navController.popBackStack(Route.Home, inclusive = false)
                        },
                    )
                }

                composable(Route.Device) {
                    DeviceScreen(
                        viewModel = viewModel,
                        displayMode = displayMode,
                        onBack = {
                            userLeftDevice = true
                            navController.popBackStack(Route.Home, inclusive = false)
                        },
                        onGesture = { navController.navigate(Route.Gesture) { launchSingleTop = true } },
                        onListeningStats = { navController.navigate(Route.ListeningStats) { launchSingleTop = true } },
                        onSettings = { navController.navigate(Route.Settings) { launchSingleTop = true } },
                        onOpenTerminal = onOpenTerminal,
                    )
                }

                composable(Route.ListeningStats) {
                    ListeningStatsScreen(
                        viewModel = viewModel,
                        displayMode = displayMode,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Route.Gesture) {
                    GestureScreen(
                        props = viewModel.props.collectAsState().value,
                        viewModel = viewModel,
                        displayMode = displayMode,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Route.Settings) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onEvent = { event ->
                            when (event) {
                                is SettingsEvent.SetThemeMode -> {
                                    onThemeChange(event.value)
                                    viewModel.onSettingsEvent(event)
                                }
                                is SettingsEvent.SetLocale -> {
                                    I18n.setLocale(event.value)
                                    viewModel.onSettingsEvent(event)
                                }
                                SettingsEvent.InstallUpdate -> {
                                    viewModel.onSettingsEvent(event)?.let(context::startActivity)
                                }
                                is SettingsEvent.OpenRelease -> {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(event.url ?: "https://github.com/ct-yx/fxxkHilife/releases"),
                                        )
                                    )
                                }
                                else -> viewModel.onSettingsEvent(event)
                            }
                        },
                    )
                }
            }
        }
    }
}
