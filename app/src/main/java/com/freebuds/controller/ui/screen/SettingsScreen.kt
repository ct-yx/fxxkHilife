package com.freebuds.controller.ui.screen
import android.content.Intent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.freebuds.controller.BuildConfig
import com.freebuds.controller.FreeBudsApp
import com.freebuds.controller.device.DeviceManager
import com.freebuds.controller.util.DebugLogger
import com.freebuds.controller.util.PermissionHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    deviceManager: DeviceManager,
    onNavigateBack: () -> Unit
) {
    val prefs = FreeBudsApp.instance.preferences
    val darkMode by prefs.darkMode.collectAsState(initial = "system")
    val autoConnect by prefs.autoConnect.collectAsState(initial = true)
    val lowLatencyFixed by prefs.lowLatencyAutoOn.collectAsState(initial = false)
    val ancNotificationEnabled by prefs.ancNotificationEnabled.collectAsState(initial = false)
    val debugLog by prefs.debugLog.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Battery optimization check
    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
    val isBatteryOptIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // === Appearance ===
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SettingsCard(title = "Theme") {
                BlurStyleOption(
                    label = "System Default",
                    description = "Follow system theme",
                    selected = darkMode == "system",
                    onClick = { scope.launch { prefs.setDarkMode("system") } }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                BlurStyleOption(
                    label = "Light",
                    description = "Always light mode",
                    selected = darkMode == "light",
                    onClick = { scope.launch { prefs.setDarkMode("light") } }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                BlurStyleOption(
                    label = "Dark",
                    description = "Always dark mode",
                    selected = darkMode == "dark",
                    onClick = { scope.launch { prefs.setDarkMode("dark") } }
                )
            }

            Spacer(Modifier.height(8.dp))

            // === Connection ===
            Text(
                text = "Connection",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SettingsCard(title = "Auto Connect") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto connect on launch", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Reconnect to last used device automatically",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoConnect,
                        onCheckedChange = { scope.launch { prefs.setAutoConnect(it) } }
                    )
                }
            }

            // Battery Optimization — guide to ignore
            if (!isBatteryOptIgnored) {
                SettingsCard(title = "Background Keep-Alive") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.BatteryChargingFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Battery Optimization Active",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "System may kill the app in background. Allow \"No restrictions\" to keep connection alive.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Button(
                        onClick = {
                            PermissionHelper.showToastAndOpenBatteryOptimizationSettings(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Disable Battery Optimization")
                    }
                }
            }

            // Low Latency Fixed
            SettingsCard(title = "Game Mode") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Low Latency Fixed On", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (lowLatencyFixed) "Auto-enables on every connection"
                            else "Off — toggle manually in controls",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = lowLatencyFixed,
                        onCheckedChange = { enabled ->
                            scope.launch { prefs.setLowLatencyAutoOn(enabled) }
                        }
                    )
                }
            }

            // Status Notification
            SettingsCard(title = "Status Notification") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show listening time & ANC", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Persistent notification with battery, ANC mode, listening duration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = ancNotificationEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                prefs.setAncNotificationEnabled(enabled)
                                val ctx = FreeBudsApp.instance
                                if (enabled) {
                                    val intent = Intent(ctx, com.freebuds.controller.service.StatusNotificationService::class.java)
                                    ctx.startForegroundService(intent)
                                } else {
                                    val intent = Intent(ctx, com.freebuds.controller.service.StatusNotificationService::class.java).apply {
                                        action = com.freebuds.controller.service.StatusNotificationService.ACTION_STOP
                                    }
                                    ctx.startService(intent)
                                }
                            }
                        }
                    )
                }
            }

            // Debug logging
            SettingsCard(title = "Debug") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Debug Logging", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Output SPP connection details to Logcat & file",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = debugLog,
                        onCheckedChange = { scope.launch { prefs.setDebugLog(it) } }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Share Logs", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Send debug log file to developer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            DebugLogger.createShareLogIntent(context)?.let { intent ->
                                context.startActivity(Intent.createChooser(intent, "Share Debug Logs"))
                            }
                        }
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = "Share Logs",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // === About ===
            Text(
                text = "About",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SettingsCard(title = "") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Version", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Device Profile", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        deviceManager.currentProfile.modelName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse("https://github.com/ct-yx/fxxkHilife")
                            }
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Project Homepage", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "Open in browser",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
private fun BlurStyleOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
