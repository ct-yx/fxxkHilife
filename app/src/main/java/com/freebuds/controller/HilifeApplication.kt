package com.freebuds.controller

import android.app.Application

class HilifeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: HilifeApplication
            private set
    }
}
