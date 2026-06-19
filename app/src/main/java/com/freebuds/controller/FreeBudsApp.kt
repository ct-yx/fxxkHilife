package com.freebuds.controller

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.os.Build
import com.freebuds.controller.bluetooth.SppClient
import com.freebuds.controller.data.PreferencesRepository
import com.freebuds.controller.device.DeviceManager
import com.freebuds.controller.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FreeBudsApp : Application() {

    lateinit var bluetoothAdapter: BluetoothAdapter
        private set

    lateinit var preferences: PreferencesRepository
        private set

    lateinit var deviceManager: DeviceManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        val bm = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bm.adapter

        preferences = PreferencesRepository(this)
        deviceManager = DeviceManager(bluetoothAdapter)

        // Initialize dual-path debug logger
        DebugLogger.init(this)

        // Sync debug log preference to SppClient + DebugLogger
        val appScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        appScope.launch {
            preferences.debugLog.collect { enabled ->
                SppClient.logEnabled = enabled
                DebugLogger.setEnabled(enabled)
            }
        }

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_STATUS,
                "Device Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows earbuds connection status and listening time"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_STATUS = "freebuds_status"
        lateinit var instance: FreeBudsApp
            private set
    }
}
