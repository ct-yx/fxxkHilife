package com.freebuds.controller.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.data.DeviceViewModel

enum class Screen {
    PermissionGuide, Home, Device, Gesture, Settings
}

@Composable
fun AppNavHost(viewModel: DeviceViewModel, onOpenTerminal: () -> Unit) {
    val context = LocalContext.current
    val connState by viewModel.connectionState.collectAsState()

    // 检查蓝牙权限（Android 12+ 需要运行时授权）
    val hasPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                .all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        } else true
    }

    var currentScreen by remember { mutableStateOf(
        if (!hasPermissions) Screen.PermissionGuide else Screen.Home
    ) }

    // 连接成功自动进 DeviceScreen
    LaunchedEffect(connState) {
        if (connState is ConnectionState.Connected) {
            currentScreen = Screen.Device
        }
    }

    // 连接断开自动退回 Home（除非正在权限引导）
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
        )
    }
}
