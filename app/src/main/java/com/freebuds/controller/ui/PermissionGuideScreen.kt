package com.freebuds.controller.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PermissionGuideScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    val bluetoothPerms = remember {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
            list.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.Q..Build.VERSION_CODES.R) {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        list.toTypedArray()
    }
    val notificationPerms = remember {
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
    }
    val requestPerms = remember { bluetoothPerms + notificationPerms }

    fun hasBluetoothPermissions(): Boolean = bluetoothPerms.all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(): Boolean = notificationPerms.isEmpty() || notificationPerms.all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    var bluetoothGranted by remember { mutableStateOf(hasBluetoothPermissions()) }
    var notificationGranted by remember { mutableStateOf(hasNotificationPermission()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        bluetoothGranted = hasBluetoothPermissions()
        notificationGranted = hasNotificationPermission()
        if (bluetoothGranted) onGranted()
    }

    LaunchedEffect(Unit) {
        bluetoothGranted = hasBluetoothPermissions()
        notificationGranted = hasNotificationPermission()
        if (bluetoothGranted && notificationGranted) onGranted()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(76.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(22.dp))
            Text("fxxkHilife", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "华为 / 荣耀耳机控制",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                "需要蓝牙权限连接耳机；建议开启通知与后台/自启动权限，以保证常驻通知和开机自动连接正常工作。",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))
            PermissionStatusRow(Icons.Default.Bluetooth, "蓝牙扫描与连接", bluetoothGranted, required = true)
            PermissionStatusRow(Icons.Default.Notifications, "通知权限", notificationGranted, required = false)
            PermissionStatusRow(Icons.Default.BatterySaver, "后台保活 / 电池优化白名单", false, required = false, hint = "需在系统设置中手动允许")
            PermissionStatusRow(Icons.Default.PowerSettingsNew, "自启动 / 开机后自动连接", false, required = false, hint = "部分国产 ROM 需手动开启")
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { launcher.launch(requestPerms) }) {
                Text(if (notificationPerms.isEmpty()) "授予蓝牙权限" else "授予蓝牙与通知权限")
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { openBatteryOptimizationSettings(context) }) {
                    Text("后台保活")
                }
                OutlinedButton(onClick = { openAutoStartSettings(context) }) {
                    Text("自启动设置")
                }
            }
            TextButton(onClick = onGranted, enabled = bluetoothGranted) {
                Text("继续")
            }
            Text(
                "我们不会上传任何数据，所有操作均在本地完成。",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun PermissionStatusRow(
    icon: ImageVector,
    title: String,
    granted: Boolean,
    required: Boolean,
    hint: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                hint ?: if (required) "必需" else "建议开启",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            AssistChip(
                onClick = {},
                label = { Text(if (granted) "已授权" else if (required) "未授权" else "建议") },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    )
}

private fun openBatteryOptimizationSettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        },
    )
    openFirstAvailable(context, intents)
}

private fun openAutoStartSettings(context: Context) {
    val packageName = context.packageName
    val intents = listOf(
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        },
    )
    openFirstAvailable(context, intents)
}

private fun openFirstAvailable(context: Context, intents: List<Intent>) {
    for (intent in intents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return
        }
    }
}
