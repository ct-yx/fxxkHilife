package com.freebuds.controller

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.freebuds.controller.bluetooth.CompanionDeviceHelper
import com.freebuds.controller.bluetooth.SppClient
import com.freebuds.controller.data.PreferencesRepository
import com.freebuds.controller.device.DeviceManager
import com.freebuds.controller.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

class FreeBudsApp : Application() {

    lateinit var bluetoothAdapter: BluetoothAdapter
        private set

    lateinit var preferences: PreferencesRepository
        private set

    lateinit var deviceManager: DeviceManager
        private set

    lateinit var companionDeviceHelper: CompanionDeviceHelper
        private set

    /** Currently active locale code ("en" or "zh") */
    var currentLocale: String = "en"
        private set

    override fun attachBaseContext(base: Context) {
        // Read saved language before super call so all Activity contexts inherit it.
        // DataStore isn't available yet, so we read from our sync SharedPreferences file.
        val savedLang = try {
            val prefs = base.getSharedPreferences(LANG_PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(LANG_PREFS_KEY, "en") ?: "en"
        } catch (_: Exception) { "en" }
        currentLocale = savedLang
        super.attachBaseContext(updateBaseContextLocale(base, savedLang))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val bm = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bm.adapter

        preferences = PreferencesRepository(this)
        companionDeviceHelper = CompanionDeviceHelper(this)
        deviceManager = DeviceManager(bluetoothAdapter)

        // Initialize dual-path debug logger
        DebugLogger.init(this)

        // Seed SharedPreferences sync file with current language (for attachBaseContext)
        val syncPrefs = getSharedPreferences(LANG_PREFS_NAME, Context.MODE_PRIVATE)
        syncPrefs.edit().putString(LANG_PREFS_KEY, currentLocale).apply()

        // Sync debug log preference to SppClient + DebugLogger
        val appScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        appScope.launch {
            preferences.debugLog.collect { enabled ->
                SppClient.logEnabled = enabled
                DebugLogger.setEnabled(enabled)
            }
        }

        createNotificationChannels()
    }

    /**
     * Update the locale for the application context and all subsequent
     * resource lookups.  Call this when the user toggles language.
     */
    fun updateLocale(langCode: String) {
        currentLocale = langCode
        Locale.setDefault(toLocale(langCode))
        val config = Configuration(resources.configuration)
        config.setLocale(toLocale(langCode))
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun toLocale(langCode: String): Locale = when (langCode) {
        "zh" -> Locale.CHINESE
        else -> Locale.ENGLISH
    }

    /**
     * Wrap a context with the given locale so all resource lookups
     * (including Jetpack Compose) see the correct language.
     */
    private fun updateBaseContextLocale(context: Context, langCode: String): Context {
        val locale = toLocale(langCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_STATUS,
                "Device Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows earbuds connection status and listening time"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_STATUS = "freebuds_status"
        const val LANG_PREFS_NAME = "freebuds_lang_sync"
        const val LANG_PREFS_KEY = "language"
        lateinit var instance: FreeBudsApp
            private set
    }
}
