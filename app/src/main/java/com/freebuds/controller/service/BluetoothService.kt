package com.freebuds.controller.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.freebuds.controller.HilifeApplication
import com.freebuds.controller.R
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.ui.MainActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BluetoothService : Service() {

    companion object {
        const val CHANNEL_ID = "bluetooth_service"
        const val NOTIFICATION_ID = 1001
        const val PREF_AUTO_LOW_LATENCY = "auto_low_latency"
        private val PACKAGE = BluetoothService::class.java.`package`?.name ?: ""
        val ACTION_CONNECT = "$PACKAGE.action.CONNECT"
        val ACTION_DISCONNECT = "$PACKAGE.action.DISCONNECT"
        val EXTRA_DEVICE = "$PACKAGE.extra.DEVICE"
        val ACTION_ANC_NORMAL = "$PACKAGE.action.ANC_NORMAL"
        val ACTION_ANC_CANCEL = "$PACKAGE.action.ANC_CANCEL"
        val ACTION_ANC_AWARE = "$PACKAGE.action.ANC_AWARE"
    }

    private val scope = MainScope()
    private var propsJob: kotlinx.coroutines.Job? = null

    private val aclReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val device = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    device?.let { onDeviceConnected(it) }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    device?.let { onDeviceDisconnected(it) }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        startPropsObserver()

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(aclReceiver, filter)
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
            ACTION_ANC_NORMAL -> setAncMode("normal")
            ACTION_ANC_CANCEL -> setAncMode("cancellation")
            ACTION_ANC_AWARE -> setAncMode("awareness")
        }
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val normalIntent = PendingIntent.getService(this, 1,
            Intent(this, BluetoothService::class.java).setAction(ACTION_ANC_NORMAL),
            PendingIntent.FLAG_IMMUTABLE)
        val cancelIntent = PendingIntent.getService(this, 2,
            Intent(this, BluetoothService::class.java).setAction(ACTION_ANC_CANCEL),
            PendingIntent.FLAG_IMMUTABLE)
        val awareIntent = PendingIntent.getService(this, 3,
            Intent(this, BluetoothService::class.java).setAction(ACTION_ANC_AWARE),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .addAction(0, "关闭", normalIntent)
            .addAction(0, "降噪", cancelIntent)
            .addAction(0, "透传", awareIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun onDeviceConnected(device: BluetoothDevice) {
        val repo = HilifeApplication.instance.deviceRepository
        val saved = repo.getSavedAddresses()
        if (device.address in saved) {
            repo.connect(device)
            val autoLowLatency = getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean(PREF_AUTO_LOW_LATENCY, true)
            if (autoLowLatency) {
                MainScope().launch {
                    delay(3000) // 等连接稳定
                    if (repo.connectionState.value is ConnectionState.Connected) {
                        repo.setProperty("config", "low_latency", "true")
                    }
                }
            }
        }
    }

    private fun onDeviceDisconnected(device: BluetoothDevice) {
        val repo = HilifeApplication.instance.deviceRepository
        if (repo.connectionState.value is com.freebuds.controller.data.ConnectionState.Connected) {
            repo.disconnect()
        }
    }

    private fun connect(device: BluetoothDevice) {
        HilifeApplication.instance.deviceRepository.connect(device)
    }

    private fun disconnect() {
        HilifeApplication.instance.deviceRepository.disconnect()
    }

    private fun setAncMode(mode: String) {
        val repo = HilifeApplication.instance.deviceRepository
        if (repo.connectionState.value is ConnectionState.Connected) {
            scope.launch {
                repo.setProperty("anc", "mode", mode)
            }
        }
    }

    // ── 监听 DeviceProps 实时更新通知内容 ──────────────────────────────────
    private fun startPropsObserver() {
        propsJob?.cancel()
        propsJob = scope.launch {
            HilifeApplication.instance.deviceRepository.props.collect { props ->
                updateNotification(props)
            }
        }
    }

    private fun updateNotification(props: com.freebuds.controller.data.DeviceProps) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        // 构建通知内容文本
        val lines = mutableListOf<String>()

        // ANC 模式
        val ancLabel = when (props.ancMode) {
            "cancellation" -> "降噪"
            "awareness" -> "透传"
            "normal" -> "关闭"
            else -> props.ancMode
        }
        if (ancLabel != null) lines.add("ANC：$ancLabel")

        // 音质模式
        val sqLabel = when (props.soundQuality) {
            "sqp_quality" -> "声音优先"
            "sqp_connectivity" -> "连接优先"
            else -> props.soundQuality
        }
        if (sqLabel != null) lines.add("音质：$sqLabel")

        // 低延迟
        if (props.lowLatency != null) {
            lines.add("低延迟：${if (props.lowLatency) "开启" else "关闭"}")
        }

        // 佩戴时长
        val since = props.connectedSince
        if (since != null && since > 0) {
            val durationMs = System.currentTimeMillis() - since
            val hours = durationMs / 3600000
            val mins = (durationMs % 3600000) / 60000
            val durationStr = if (hours > 0) "${hours}h${mins}m" else "${mins}m"
            lines.add("佩戴：$durationStr")
        }

        val contentText = if (lines.isEmpty()) "等待数据…" else lines.joinToString(" | ")

        // 重建通知（带 ANC 按钮）
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(
                PendingIntent.getActivity(this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
            .addAction(0, "关闭", PendingIntent.getService(this, 1,
                Intent(this, BluetoothService::class.java).setAction(ACTION_ANC_NORMAL),
                PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, "降噪", PendingIntent.getService(this, 2,
                Intent(this, BluetoothService::class.java).setAction(ACTION_ANC_CANCEL),
                PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, "透传", PendingIntent.getService(this, 3,
                Intent(this, BluetoothService::class.java).setAction(ACTION_ANC_AWARE),
                PendingIntent.FLAG_IMMUTABLE))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        propsJob?.cancel()
        try { unregisterReceiver(aclReceiver) } catch (_: Exception) {}
        disconnect()
        super.onDestroy()
    }
}
