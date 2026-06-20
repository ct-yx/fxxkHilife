package com.freebuds.controller.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "freebuds_prefs")

class PreferencesRepository(private val context: Context) {

    companion object {
        val DARK_MODE = stringPreferencesKey("dark_mode")            // "system" | "light" | "dark"
        val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val LOW_LATENCY_AUTO_ON = booleanPreferencesKey("low_latency_auto_on")
        val ANC_NOTIFICATION_ENABLED = booleanPreferencesKey("anc_notification_enabled")
        val DEBUG_LOG = booleanPreferencesKey("debug_log")
    }

    val darkMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DARK_MODE] ?: "system"
    }

    val lastDeviceAddress: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[LAST_DEVICE_ADDRESS]
    }

    val autoConnect: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_CONNECT] ?: true
    }

    val lowLatencyAutoOn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LOW_LATENCY_AUTO_ON] ?: false
    }

    val ancNotificationEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ANC_NOTIFICATION_ENABLED] ?: false
    }

    val debugLog: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DEBUG_LOG] ?: true  // enable by default during development
    }

    suspend fun setDarkMode(mode: String) {
        context.dataStore.edit { it[DARK_MODE] = mode }
    }

    suspend fun setLastDeviceAddress(address: String) {
        context.dataStore.edit { it[LAST_DEVICE_ADDRESS] = address }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_CONNECT] = enabled }
    }

    suspend fun setLowLatencyAutoOn(enabled: Boolean) {
        context.dataStore.edit { it[LOW_LATENCY_AUTO_ON] = enabled }
    }

    suspend fun setAncNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ANC_NOTIFICATION_ENABLED] = enabled }
    }

    suspend fun setDebugLog(enabled: Boolean) {
        context.dataStore.edit { it[DEBUG_LOG] = enabled }
    }
}
