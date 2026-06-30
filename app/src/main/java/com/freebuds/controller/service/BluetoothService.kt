package com.freebuds.controller.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.freebuds.controller.HilifeApplication
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.i18n.I18n
import com.freebuds.controller.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BluetoothService : Service() {

    companion object {
        const val CHANNEL_ID = "bluetooth_service"
        const val NOTIFICATION_ID = 1001
        private val PACKAGE = BluetoothService::class.java.`package`?.name ?: ""
        val ACTION_DISCONNECT = "$PACKAGE.action.DISCONNECT"
        val ACTION_AUTO_CONNECT_LAST = "$PACKAGE.action.AUTO_CONNECT_LAST"
        val ACTION_ANC_NORMAL = "$PACKAGE.action.ANC_NORMAL"
        val ACTION_ANC_CANCEL = "$PACKAGE.action.ANC_CANCEL"
        val ACTION_ANC_AWARE = "$PACKAGE.action.ANC_AWARE"
    }

    private val scope = MainScope()
    private var propsJob: Job? = null
    private var notificationTickerJob: Job? = null


    override fun onCreate() {
        super.onCreate()
        createNotificationChannelIfNeeded()
        startForeground(NOTIFICATION_ID, createNotification())
        startPropsObserver()
        startNotificationTicker()
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, I18n.t("notification.channel.bluetooth_status"), NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = I18n.t("notification.channel.bluetooth_status_desc")
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            null, ACTION_AUTO_CONNECT_LAST -> autoConnectLastSavedDevice()
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
            .setContentTitle(I18n.t("notification.service.title"))
            .setContentText(I18n.t("notification.service.text"))
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .addAction(0, I18n.t("common.disabled"), normalIntent)
            .addAction(0, I18n.t("notification.action.anc_cancellation"), cancelIntent)
            .addAction(0, I18n.t("notification.action.anc_awareness"), awareIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun disconnect() {
        HilifeApplication.instance.deviceRepository.disconnect()
    }

    private fun autoConnectLastSavedDevice() {
        HilifeApplication.instance.deviceRepository.autoConnectLastSaved()
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
                // 属性变化时立即刷新：ANC / 低延迟 / 音质模式实时更新
                updateNotification(props)
            }
        }
    }

    private fun startNotificationTicker() {
        notificationTickerJob?.cancel()
        notificationTickerJob = scope.launch {
            while (isActive) {
                delay(60_000)
                val repo = HilifeApplication.instance.deviceRepository
                if (repo.connectionState.value is ConnectionState.Connected) {
                    // 听音时长随时间变化，需低频刷新；不重新查询设备，避免增加蓝牙压力
                    updateNotification(repo.props.value)
                }
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

        // 电池电量：只展示 props 中已有数据，不额外触发查询；电量随仓库轮询低频更新
        val batteryParts = mutableListOf<String>()
        props.batteryLeft?.let { batteryParts.add("左${it}%") }
        props.batteryRight?.let { batteryParts.add("右${it}%") }
        props.batteryCase?.let { batteryParts.add("盒${it}%") }
        if (batteryParts.isNotEmpty()) {
            lines.add("电量：${batteryParts.joinToString("/")}")
        } else {
            props.batteryGlobal?.let { lines.add("电量：${it}%") }
        }

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

        val contentText = if (lines.isEmpty()) I18n.t("common.loading") else lines.joinToString(" | ")

        // 重建通知（带 ANC 按钮）
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(I18n.t("notification.service.title"))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(
                PendingIntent.getActivity(this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
            .addAction(0, I18n.t("common.disabled"), PendingIntent.getService(this, 1,
                Intent(this, BluetoothService::class.java).setAction(ACTION_ANC_NORMAL),
                PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, I18n.t("notification.action.anc_cancellation"), PendingIntent.getService(this, 2,
                Intent(this, BluetoothService::class.java).setAction(ACTION_ANC_CANCEL),
                PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, I18n.t("notification.action.anc_awareness"), PendingIntent.getService(this, 3,
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
        notificationTickerJob?.cancel()
        // 前台服务被系统回收/重建时不要主动断开耳机；手动断连只走 ACTION_DISCONNECT
        super.onDestroy()
    }
}
