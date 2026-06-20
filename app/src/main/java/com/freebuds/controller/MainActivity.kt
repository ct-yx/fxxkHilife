package com.freebuds.controller

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.freebuds.controller.ui.navigation.FreeBudsNavHost
import com.freebuds.controller.ui.theme.FreeBudsTheme
import com.freebuds.controller.util.PermissionHelper
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    /**
     * Per-Grant launcher for Bluetooth permissions, location (on older devices),
     * and notification permission (Android 13+).
     *
     * On callback we perform a global re-check and display a toast if any
     * essential permission remains denied.
     */
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grantResults ->
            // Re-check all permissions — the UI will recompose
            if (!PermissionHelper.allEssentialPermissionsGranted(this)) {
                val missing = PermissionHelper.missingPermissionLabels(this)
                if (missing.isNotEmpty()) {
                    Toast.makeText(
                        this,
                        "Permissions still missing: ${missing.joinToString(", ")}. Some features may not work.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(
                    this,
                    "All permissions granted! You can now connect your earbuds.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val prefs = (application as FreeBudsApp).preferences
            val darkMode by prefs.darkMode.collectAsState(initial = "system")
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            FreeBudsTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var allGranted by remember { mutableStateOf(PermissionHelper.allEssentialPermissionsGranted(this@MainActivity)) }

                    if (!allGranted) {
                        PermissionPromptScreen(
                            onRequestPermissions = {
                                // Collect all permissions that still need requesting
                                val permsToRequest = mutableListOf<String>().apply {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        if (!PermissionHelper.hasBluetoothConnect(this@MainActivity))
                                            add(Manifest.permission.BLUETOOTH_CONNECT)
                                        if (!PermissionHelper.hasBluetoothScan(this@MainActivity))
                                            add(Manifest.permission.BLUETOOTH_SCAN)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        if (!PermissionHelper.hasNotificationPermission(this@MainActivity))
                                            add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    if (Build.VERSION.SDK_INT in 29..30) {
                                        if (!PermissionHelper.hasLocationPermission(this@MainActivity))
                                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                }
                                if (permsToRequest.isNotEmpty()) {
                                    permissionLauncher.launch(permsToRequest.toTypedArray())
                                }
                            },
                            onPermissionResult = {
                                allGranted = PermissionHelper.allEssentialPermissionsGranted(this@MainActivity)
                            },
                            onNavigateToSettings = {
                                // If user chooses "Go to Settings" instead — open app details
                                PermissionHelper.showToastAndOpenAppSettings(
                                    this@MainActivity,
                                    "Please manually grant all required permissions"
                                )
                            }
                        )
                    } else {
                        val navController = rememberNavController()
                        // Show a brief battery optimization warning if applicable
                        LaunchedEffect(Unit) {
                            if (!PermissionHelper.isBatteryOptimizationIgnored(this@MainActivity)) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Tip: Disable battery optimization in Settings > Apps > FreeBuds for stable background connection",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            // Trigger auto-connect
                            val app = application as FreeBudsApp
                            val lastAddr = app.preferences.lastDeviceAddress.first()
                            app.deviceManager.tryAutoConnect(lastAddr)
                        }
                        FreeBudsNavHost(
                            navController = navController,
                            deviceManager = (application as FreeBudsApp).deviceManager
                        )
                    }
                }
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return PermissionHelper.allEssentialPermissionsGranted(this)
    }

    private suspend fun tryConnectAfterPermission() {
        val app = application as FreeBudsApp
        val auto = app.preferences.autoConnect.first()
        if (auto) {
            val lastAddr = app.preferences.lastDeviceAddress.first()
            if (!lastAddr.isNullOrEmpty()) {
                val devices = app.deviceManager.findPairedDevices()
                val lastDevice = devices.find { it.address == lastAddr }
                if (lastDevice != null) {
                    app.deviceManager.connect(lastDevice)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

// ──────────────────────────────────────────────────────────
//  Permission Prompt Screen (Composable)
// ──────────────────────────────────────────────────────────

@Composable
private fun PermissionPromptScreen(
    onRequestPermissions: () -> Unit,
    onPermissionResult: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    // Recheck after returning from system settings
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.addObserver(
            object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    if (event == Lifecycle.Event.ON_RESUME) {
                        onPermissionResult()
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "FreeBuds Controller",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "This app needs the following permissions to connect and control your earbuds:",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        // Dynamically list required permissions based on Android version
        val permissionItems = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add("BLUETOOTH_CONNECT — to connect to your earbuds")
                add("BLUETOOTH_SCAN — to discover paired devices")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add("POST_NOTIFICATIONS — to show ANC & battery status")
            }
            if (Build.VERSION.SDK_INT in 29..30) {
                add("ACCESS_FINE_LOCATION — needed for Bluetooth scanning")
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            permissionItems.forEach { item ->
                Text(
                    text = "• $item",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Additionally for stable background connection:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "• Disable Battery Optimization (Settings -> Apps)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // "Grant All" primary button
        Button(
            onClick = {
                onRequestPermissions()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Grant Required Permissions", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(12.dp))

        // Secondary: open system app settings page (for manual toggle)
        OutlinedButton(
            onClick = {
                onNavigateToSettings()
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Open App Settings", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(16.dp))

        // Skip / exit
        TextButton(onClick = {
            PermissionHelper.showToast(
                context,
                "Permissions required — app may not function correctly without Bluetooth access",
                Toast.LENGTH_LONG
            )
            activity?.finish()
        }) {
            Text("Exit App")
        }
    }
}
