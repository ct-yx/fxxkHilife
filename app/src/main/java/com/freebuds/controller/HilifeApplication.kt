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
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BluetoothService.CHANNEL_ID,
                "蓝牙连接",
                NotificationManager.IMPORTANCE_LOW
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
