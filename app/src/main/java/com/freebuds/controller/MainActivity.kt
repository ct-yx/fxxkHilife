package com.freebuds.controller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // Re-check permissions after grant/deny — recomposition will handle
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FreeBudsTheme(darkTheme = isSystemInDarkTheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var allGranted by remember { mutableStateOf(checkPermissions()) }

                    if (!allGranted) {
                        PermissionPromptScreen(
                            onRequestPermissions = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.BLUETOOTH_CONNECT,
                                        Manifest.permission.BLUETOOTH_SCAN
                                    )
                                )
                            },
                            onPermissionResult = { allGranted = checkPermissions() }
                        )
                    } else {
                        val navController = rememberNavController()
                        // Trigger auto-connect once when permissions are ready (safe, won't conflict with UI)
                        LaunchedEffect(Unit) {
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
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
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

@Composable
private fun PermissionPromptScreen(
    onRequestPermissions: () -> Unit,
    onPermissionResult: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

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
            text = "This app needs Bluetooth permissions to connect and control your HUAWEI FreeBuds earbuds.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "• BLUETOOTH_CONNECT — to find and connect to your earbuds\n• BLUETOOTH_SCAN — to discover paired devices",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                onRequestPermissions()
                // Re-check after returning from system permission dialog
                lifecycleOwner.lifecycle.addObserver(
                    object : LifecycleEventObserver {
                        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                            if (event == Lifecycle.Event.ON_RESUME) {
                                onPermissionResult()
                                source.lifecycle.removeObserver(this)
                            }
                        }
                    }
                )
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Grant Permissions", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = {
            activity?.finish()
        }) {
            Text("Exit App")
        }
    }
}
