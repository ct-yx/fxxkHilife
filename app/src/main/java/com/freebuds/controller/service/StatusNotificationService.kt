package com.freebuds.controller.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.freebuds.controller.FreeBudsApp
import com.freebuds.controller.MainActivity
import com.freebuds.controller.device.AncMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class StatusNotificationService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.freebuds.controller.STOP_NOTIFICATION"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listeningStartTime: Long = 0L
    private var totalListeningSeconds: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        startUpdateLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startUpdateLoop() {
        scope.launch {
            val dm = FreeBudsApp.instance.deviceManager
            dm.state.collect { state ->
                if (state.connected && listeningStartTime == 0L) {
                    listeningStartTime = System.currentTimeMillis()
                } else if (!state.connected) {
                    if (listeningStartTime > 0) {
                        totalListeningSeconds += (System.currentTimeMillis() - listeningStartTime) / 1000
                        listeningStartTime = 0L
                    }
                }
                val nm = getSystemService(android.app.NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification(state))
            }
        }
    }

    private fun buildNotification(state: com.freebuds.controller.device.DeviceState? = null): Notification {
        val dm = FreeBudsApp.instance.deviceManager
        val s = state ?: dm.state.value

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val listeningSeconds = if (s.connected && listeningStartTime > 0) {
            totalListeningSeconds + (System.currentTimeMillis() - listeningStartTime) / 1000
        } else {
            totalListeningSeconds
        }
        val listeningStr = formatDuration(listeningSeconds)

        val batteryStr = buildString {
            s.batteryLeft?.let { append("L:$it% ") }
            s.batteryRight?.let { append("R:$it% ") }
            s.batteryCase?.let { append("Case:$it%") }
        }

        val ancStr = when (s.ancMode) {
            AncMode.OFF -> "ANC Off"
            AncMode.CANCELLATION -> "Noise Cancel"
            AncMode.AWARENESS -> "Awareness"
            AncMode.UNKNOWN -> "ANC: ?"
        }

        val title = if (s.connected) s.deviceName.ifEmpty { "Connected" } else "Disconnected"
        val content = buildString {
            if (s.connected) {
                append("$ancStr · $listeningStr")
                if (batteryStr.isNotEmpty()) append(" · $batteryStr")
            } else {
                append("Tap to connect")
            }
        }

        return NotificationCompat.Builder(this, FreeBudsApp.CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(tapPendingIntent)
            .setSilent(true)
            .build()
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}
