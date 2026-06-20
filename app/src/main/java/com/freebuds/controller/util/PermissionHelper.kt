package com.freebuds.controller.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Unified permission helper providing user-friendly toasts, settings redirection,
 * and capability checks for all permissions that FreeBuds Controller requires.
 *
 * Features:
 * - Permission status checks (notification, bluetooth, location, battery optimization)
 * - Settings intent builders with user-friendly toast before redirecting
 * - Guided request that shows a toast explaining why, then opens the system settings page
 */
object PermissionHelper {

    // ──────────────────────────────────────────────
    //  Permission check helpers
    // ──────────────────────────────────────────────

    /**
     * Check if BLUETOOTH_CONNECT is granted (required on Android 12+ / API 31+).
     */
    fun hasBluetoothConnect(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    /**
     * Check if BLUETOOTH_SCAN is granted (required on Android 12+ / API 31+).
     */
    fun hasBluetoothScan(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    /**
     * Notification permission (Android 13+ / API 33+).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    /**
     * Location permission: on Android 10-11 (API 29-30) Bluetooth scan requires
     * ACCESS_FINE_LOCATION; on Android 12+ BLUETOOTH_SCAN replaces it.
     */
    fun hasLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT in 29..30) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    /**
     * Check if battery optimization is disabled for our app.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Check whether all essential permissions are granted.
     */
    fun allEssentialPermissionsGranted(context: Context): Boolean {
        return hasBluetoothConnect(context) &&
               hasBluetoothScan(context) &&
               hasNotificationPermission(context) &&
               hasLocationPermission(context)
    }

    /**
     * Return a list of human-readable permission names that are currently missing.
     */
    fun missingPermissionLabels(context: Context): List<String> {
        val missing = mutableListOf<String>()
        if (!hasBluetoothConnect(context)) missing.add("Bluetooth Connect")
        if (!hasBluetoothScan(context)) missing.add("Bluetooth Scan")
        if (!hasNotificationPermission(context)) missing.add("Notifications")
        if (!hasLocationPermission(context)) missing.add("Location (for Bluetooth scanning)")
        return missing
    }

    // ──────────────────────────────────────────────
    //  Toast + Intent-based user guidance
    // ──────────────────────────────────────────────

    /**
     * Show a brief informative toast, then open the Android **app settings** page
     * so the user can manually toggle the relevant toggles.
     */
    fun showToastAndOpenAppSettings(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Prompt the user to disable battery optimization. Shows a toast explaining why,
     * then opens the system's battery optimization settings page.
     */
    fun showToastAndOpenBatteryOptimizationSettings(context: Context) {
        Toast.makeText(
            context,
            "Battery optimization may disconnect earbuds in background — tap to allow 'No restrictions'",
            Toast.LENGTH_LONG
        ).show()
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Open system notification settings for this app.
     */
    fun showToastAndOpenNotificationSettings(context: Context) {
        Toast.makeText(
            context,
            "Status notifications require permission — please toggle 'Allow notifications'",
            Toast.LENGTH_LONG
        ).show()
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * For device discovery on Android 10-11: open location settings.
     */
    fun showToastAndOpenLocationSettings(context: Context) {
        Toast.makeText(
            context,
            "Location permission is needed for Bluetooth scanning on this device",
            Toast.LENGTH_LONG
        ).show()
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * General-purpose helper: show a toast then open the system Bluetooth settings
     * where the user can pair a new device.
     */
    fun showToastAndOpenBluetoothSettings(context: Context, message: String? = null) {
        Toast.makeText(
            context,
            message ?: "Please pair your earbuds in Bluetooth settings first",
            Toast.LENGTH_LONG
        ).show()
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Show a generic toast message.
     */
    fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }

    // ──────────────────────────────────────────────
    //  Error-scenario helpers
    // ──────────────────────────────────────────────

    /**
     * Called when a Bluetooth operation fails due to missing permission.
     * Shows a toast guiding the user to settings.
     */
    fun onPermissionDeniedBluetooth(context: Context) {
        showToastAndOpenAppSettings(
            context,
            "Bluetooth permission denied — opening settings, please grant Bluetooth access"
        )
    }

    /**
     * Called when a notification permission is denied (e.g. user taps "Show notification"
     * but permission is missing on Android 13+).
     */
    fun onPermissionDeniedNotification(context: Context) {
        showToastAndOpenNotificationSettings(context)
    }

    /**
     * Called when the background service gets killed — guides user to disable battery optimization.
     */
    fun onBackgroundKilled(context: Context) {
        showToastAndOpenBatteryOptimizationSettings(context)
    }

    /**
     * Called when the user tries to use ANC but device is not connected,
     * or the operation fails due to an unavailable feature.
     */
    fun onFeatureNotAvailable(context: Context, featureName: String) {
        Toast.makeText(
            context,
            "$featureName is not available on the connected device or firmware",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Called when auto-reconnect fails after exhaustion.
     */
    fun onAutoReconnectExhausted(context: Context, deviceName: String) {
        Toast.makeText(
            context,
            "Unable to reconnect to $deviceName after multiple attempts — tap to try again",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Called when the SPP handshake / connection fails with a clear reason.
     */
    fun onConnectionFailed(context: Context, reason: String) {
        Toast.makeText(
            context,
            "Connection failed: $reason",
            Toast.LENGTH_LONG
        ).show()
    }
}
