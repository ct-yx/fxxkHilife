package com.freebuds.controller.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.freebuds.controller.nativeui.NativeUiBridge
import com.freebuds.controller.service.BluetoothService
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

/** Flutter host for the new presentation layer. */
class MainActivity : FlutterActivity() {
    private var nativeUiBridge: NativeUiBridge? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        startBluetoothForegroundServiceIfAllowed(autoConnect = true)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        nativeUiBridge = NativeUiBridge(this, flutterEngine)
    }

    override fun onResume() {
        super.onResume()
        nativeUiBridge?.nativeController?.setAppInForeground(true)
        startBluetoothForegroundServiceIfAllowed(autoConnect = true)
    }

    override fun onPause() {
        nativeUiBridge?.nativeController?.setAppInForeground(false)
        super.onPause()
    }

    override fun onDestroy() {
        nativeUiBridge?.close()
        nativeUiBridge = null
        super.onDestroy()
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED

    private fun startBluetoothForegroundServiceIfAllowed(autoConnect: Boolean) {
        if (!hasBluetoothConnectPermission()) return
        val intent = Intent(this, BluetoothService::class.java).apply {
            if (autoConnect) action = BluetoothService.ACTION_AUTO_CONNECT_LAST
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 1001
    }
}
