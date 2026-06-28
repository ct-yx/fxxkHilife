package com.freebuds.controller

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.freebuds.controller.data.DeviceRepository
import com.freebuds.controller.service.BluetoothService

class HilifeApplication : Application() {

    val deviceRepository by lazy { DeviceRepository() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        deviceRepository.init(this)
        // 从 SharedPreferences 加载日志保留行数
        val maxLines = getSharedPreferences("fxxk_theme", MODE_PRIVATE)
            .getInt("log_max_lines", 2000)
        com.freebuds.controller.util.LogBuffer.setMaxLines(maxLines)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BluetoothService.CHANNEL_ID,
                "蓝牙连接",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "FreeBuds 后台连接服务" }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        lateinit var instance: HilifeApplication
            private set
    }
}
