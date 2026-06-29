package com.freebuds.controller

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.freebuds.controller.data.DeviceRepository
import com.freebuds.controller.service.BluetoothService
import com.freebuds.controller.util.LogBuffer

class HilifeApplication : Application() {

    val deviceRepository by lazy { DeviceRepository() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        deviceRepository.init(this)
        // 从 SharedPreferences 加载日志保留行数
        val maxLines = getSharedPreferences("fxxk_theme", MODE_PRIVATE)
            .getInt("log_max_lines", 2000)
        LogBuffer.setMaxLines(maxLines)
        LogBuffer.i("App", "fxxkHilife ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) started")
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BluetoothService.CHANNEL_ID,
                "蓝牙连接与 ANC 状态",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示当前 ANC 模式、听音时长、低延迟/音质状态"
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
