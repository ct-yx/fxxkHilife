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
import com.freebuds.controller.i18n.i18n

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
            Text(i18n("app.name"), style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                i18n("permission.subtitle"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                i18n("permission.description"),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))
            PermissionStatusRow(Icons.Default.Bluetooth, i18n("permission.bluetooth_connect"), bluetoothGranted, required = true)
            PermissionStatusRow(Icons.Default.Notifications, i18n("permission.notification"), notificationGranted, required = false)
            PermissionStatusRow(Icons.Default.BatterySaver, i18n("permission.keep_alive_whitelist"), false, required = false, hint = i18n("permission.manual_allow_hint"))
            PermissionStatusRow(Icons.Default.PowerSettingsNew, i18n("permission.auto_start"), false, required = false, hint = i18n("permission.rom_manual_hint"))
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { launcher.launch(requestPerms) }) {
                Text(if (notificationPerms.isEmpty()) i18n("permission.grant_bluetooth") else i18n("permission.grant_bluetooth_notification"))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { openBatteryOptimizationSettings(context) }) {
                    Text(i18n("permission.keep_alive"))
                }
                OutlinedButton(onClick = { openAutoStartSettings(context) }) {
                    Text(i18n("permission.auto_start_settings"))
                }
            }
            TextButton(onClick = onGranted, enabled = bluetoothGranted) {
                Text(i18n("common.continue"))
            }
            Text(
                i18n("permission.privacy_local"),
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
                hint ?: if (required) i18n("permission.required") else i18n("permission.recommended"),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            AssistChip(
                onClick = {},
                label = { Text(if (granted) i18n("permission.granted") else if (required) i18n("permission.not_granted") else i18n("permission.suggested")) },
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
