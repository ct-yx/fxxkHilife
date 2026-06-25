package com.freebuds.controller.service

import android.app.Notification
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.freebuds.controller.R

class BluetoothService : Service() {

    companion object {
        const val CHANNEL_ID = "bluetooth_service"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val device = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DEVICE)
                }
                device?.let { connect(it) }
            }
            ACTION_DISCONNECT -> disconnect()
        }
        return START_STICKY
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun connect(device: BluetoothDevice) {
        // TODO: SPP connection
    }

    private fun disconnect() {
        // TODO: disconnect
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    // Intent actions
    const val ACTION_CONNECT = "$PACKAGE.action.CONNECT"
    const val ACTION_DISCONNECT = "$PACKAGE.action.DISCONNECT"
    const val EXTRA_DEVICE = "$PACKAGE.extra.DEVICE"

    private val PACKAGE get() = BluetoothService::class.java.`package`?.name ?: ""
}
