package com.freebuds.controller

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.freebuds.controller.data.DeviceRepository
import com.freebuds.controller.data.SettingsRepository
import com.freebuds.controller.i18n.I18n
import com.freebuds.controller.runtime.EarbudRuntime
import com.freebuds.controller.runtime.RepositoryEarbudRuntime
import com.freebuds.controller.service.BluetoothService
import com.freebuds.controller.util.CrashReporter
import com.freebuds.controller.util.LogBuffer

class HilifeApplication : Application() {

    val settingsRepository by lazy { SettingsRepository(this) }
    val deviceRepository by lazy { DeviceRepository() }
    /** Single command/state seam shared by the new UI and system entry points. */
    val earbudRuntime: EarbudRuntime by lazy { RepositoryEarbudRuntime(deviceRepository) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashReporter.install(this)
        I18n.setLocale(settingsRepository.locale())
        deviceRepository.init(this, settingsRepository)
        // SettingsRepository 已完成旧设置迁移，并统一应用日志诊断配置。
        LogBuffer.setMaxLines(settingsRepository.state.value.logMaxLines)
        LogBuffer.setProtocolFrameLogging(settingsRepository.state.value.protocolFrameLogging)
        LogBuffer.startSession(
            mapOf(
                "appVersion" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                "androidApi" to Build.VERSION.SDK_INT.toString(),
                "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "locale" to I18n.currentLocale().tag,
            )
        )
        if (CrashReporter.latestReport() != null) {
            LogBuffer.e("Crash", "previous uncaught exception captured; included in this report")
        }
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
