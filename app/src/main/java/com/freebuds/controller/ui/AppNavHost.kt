package com.freebuds.controller.ui

import android.Manifest
import android.bluetooth.BluetoothDevice
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.data.WallpaperStore
import com.freebuds.controller.i18n.I18n
import com.freebuds.controller.i18n.LocalI18n
import com.freebuds.controller.ui.foundation.components.AppScaffold
import com.freebuds.controller.ui.state.SettingsEvent
import com.freebuds.controller.ui.state.ConnectionSummary
import com.freebuds.controller.ui.state.DeviceConnectionSession
import com.freebuds.controller.ui.state.DeviceNavigationState
import com.freebuds.controller.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var navigationState by remember { mutableStateOf(DeviceNavigationState()) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val currentLocale = settingsState.locale
    val wallpaperUri = settingsState.wallpaperUri
    val wallpaperScope = settingsState.wallpaperScope
    val displayMode = UiDisplayMode.MATERIAL3
    val controlChannelState by viewModel.controlChannelState.collectAsStateWithLifecycle()
    val connection = remember(controlChannelState) { ConnectionSummary.from(controlChannelState) }

    // Migrate wallpapers selected by older builds. New selections are copied into app-private
    // storage before they reach SettingsRepository, so this only handles legacy content URIs.
    LaunchedEffect(wallpaperUri) {
        val legacyUri = wallpaperUri?.takeIf { it.startsWith("content://") } ?: return@LaunchedEffect
        val storedUri = withContext(Dispatchers.IO) {
            WallpaperStore.importWallpaper(context, Uri.parse(legacyUri))
        }
        if (storedUri != null && storedUri != legacyUri) {
            viewModel.onSettingsEvent(SettingsEvent.SetWallpaperUri(storedUri))
        }
    }

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

    val connectionSession = remember(connection.address, connection.attemptId) {
        val address = connection.address
        val attemptId = connection.attemptId
        if (!address.isNullOrBlank() && !attemptId.isNullOrBlank()) {
            DeviceConnectionSession(
                address = address,
                attemptId = attemptId,
            )
        } else {
            null
        }
    }

    // Navigation is an intent boundary, not a side effect of calling autoConnectSaved().
    // Automatic entry is consumed once per system-triggered connection session.
    LaunchedEffect(connectionSession, connection.isReady, connection.trigger, currentRoute) {
        val observed = navigationState.observeSession(connectionSession)
        navigationState = observed
        if (currentRoute == Route.Home && observed.shouldAutoOpen(connection.isReady, connection.trigger)) {
            navigationState = observed.markAutoOpened()
            navController.navigate(Route.Device) { launchSingleTop = true }
        }
    }

    fun openSavedDevice(address: String) {
        navigationState = navigationState.markUserOpened()
        viewModel.autoConnectSaved(address)
        navController.navigate(Route.Device) { launchSingleTop = true }
    }

    fun openScannedDevice(device: BluetoothDevice) {
        navigationState = navigationState.markUserOpened()
        viewModel.connect(device)
        navController.navigate(Route.Device) { launchSingleTop = true }
    }

    fun returnHomeFromDevice() {
        navigationState = navigationState.markUserLeft()
        navController.navigate(Route.Home) {
            popUpTo(Route.Home) { inclusive = false }
            launchSingleTop = true
        }
    }

    CompositionLocalProvider(LocalI18n provides I18n.provider(currentLocale)) {
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
                        onDeviceClick = ::openSavedDevice,
                        onRemoveDevice = { address ->
                            viewModel.removeSavedDevice(address)
                            if (connection.address == address) {
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
                        onDeviceSelected = ::openScannedDevice,
                    )
                }

                composable(Route.Device) {
                    DeviceScreen(
                        viewModel = viewModel,
                        displayMode = displayMode,
                        onBack = ::returnHomeFromDevice,
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
                    val props by viewModel.props.collectAsStateWithLifecycle()
                    GestureScreen(
                        props = props,
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
