package com.freebuds.controller

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.freebuds.controller.data.DeviceRepository
import com.freebuds.controller.i18n.I18n
import com.freebuds.controller.service.BluetoothService
import com.freebuds.controller.util.LogBuffer

class HilifeApplication : Application() {

    val deviceRepository by lazy { DeviceRepository() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        I18n.setLocale(I18n.loadLocale(this))
        deviceRepository.init(this)
        // 从 SharedPreferences 加载日志诊断设置。
        val logPrefs = getSharedPreferences("fxxk_theme", MODE_PRIVATE)
        val maxLines = logPrefs.getInt("log_max_lines", 2000)
        LogBuffer.setMaxLines(maxLines)
        LogBuffer.setProtocolFrameLogging(logPrefs.getBoolean("log_protocol_frames", false))
        LogBuffer.startSession(
            mapOf(
                "appVersion" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                "androidApi" to Build.VERSION.SDK_INT.toString(),
                "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "locale" to I18n.currentLocale().tag,
            )
        )
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BluetoothService.CHANNEL_ID,
                I18n.t("notification.channel.bluetooth_status"),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = I18n.t("notification.channel.bluetooth_status_desc")
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        lateinit var instance: HilifeApplication
            private set
    }
}
