package com.freebuds.controller.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.data.DeviceViewModel

enum class Screen {
    PermissionGuide, Scan, Device, Settings
}

@Composable
fun AppNavHost(viewModel: DeviceViewModel, onOpenTerminal: () -> Unit) {
    val context = LocalContext.current
    val connState by viewModel.connectionState.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.Scan) }

    // 检查权限
    val hasPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                .all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        } else true
    }

    LaunchedEffect(hasPermissions) {
        currentScreen = if (!hasPermissions) Screen.PermissionGuide else Screen.Scan
    }

    // 监听连接状态：连接成功自动进入 DeviceScreen
    LaunchedEffect(connState) {
        if (connState is ConnectionState.Connected) {
            currentScreen = Screen.Device
        }
    }

    when (currentScreen) {
        Screen.PermissionGuide -> PermissionGuideScreen(
            onGranted = { currentScreen = Screen.Scan }
        )
        Screen.Scan -> ScanScreen(
            viewModel = viewModel,
            onSettings = { currentScreen = Screen.Settings },
        )
        Screen.Device -> DeviceScreen(
            viewModel = viewModel,
            onBack = { currentScreen = Screen.Scan },
            onSettings = { currentScreen = Screen.Settings },
            onOpenTerminal = onOpenTerminal,
        )
        Screen.Settings -> SettingsScreen(
            viewModel = viewModel,
            onBack = { currentScreen = when (connState) {
                is ConnectionState.Connected -> Screen.Device
                else -> Screen.Scan
            } },
        )
    }
}
