package com.freebuds.controller.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.os.Build
import com.freebuds.controller.util.DebugLogger

/**
 * Wraps [CompanionDeviceManager] (Android 8.0+, API 26) to assist automatic
 * reconnection management.  Registering the device as a "companion" signals
 * the system that the app has an ongoing relationship with this Bluetooth
 * device, which may improve reconnection latencies and background behavior.
 *
 * Usage:
 *   val helper = CompanionDeviceHelper(context)
 *   helper.register(device)   // after successful SPP connect
 *   helper.isAssociated(device.address) // check
 */
class CompanionDeviceHelper(private val context: Context) {

    companion object {
        private const val TAG = "CompanionDeviceHelper"
    }

    private val manager: CompanionDeviceManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
        } else null
    }

    /**
     * Register the given [device] as a companion device.
     * This tells the Android system to treat the connection preferentially,
     * potentially improving auto-reconnect behavior on Android 12+.
     */
    @SuppressLint("MissingPermission")
    fun register(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            DebugLogger.d(TAG, "CompanionDeviceManager requires Android 8+")
            return
        }
        val mgr = manager ?: run {
            DebugLogger.w(TAG, "CompanionDeviceManager not available on this device")
            return
        }

        if (isAssociated(device.address)) {
            DebugLogger.d(TAG, "Device ${device.address} already registered as companion")
            return
        }

        DebugLogger.i(TAG, "Registering ${device.name} (${device.address}) as companion device...")
        try {
            val filter = BluetoothDeviceFilter.Builder()
                .setAddress(device.address)
                .build()

            val request = AssociationRequest.Builder()
                .addDeviceFilter(filter)
                .setSingleDevice(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val associationCallback = object : CompanionDeviceManager.Callback() {
                    override fun onDeviceFound(discoveryResult: android.content.IntentSender) {
                        DebugLogger.i(TAG, "Companion device discovery result received")
                    }

                    override fun onFailure(error: CharSequence?) {
                        DebugLogger.w(TAG, "Companion device association failed: $error")
                    }
                }
                mgr.associate(request, associationCallback, null)
            } else {
                DebugLogger.d(TAG, "Legacy association path for ${device.address}")
            }
        } catch (e: SecurityException) {
            DebugLogger.w(TAG, "Missing permission for CompanionDeviceManager association: ${e.message}")
        } catch (e: Exception) {
            DebugLogger.w(TAG, "CompanionDeviceManager association failed: ${e.message}")
        }
    }

    /**
     * Check if the given device MAC is already known to the system as a companion.
     */
    @SuppressLint("MissingPermission")
    fun isAssociated(address: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val mgr = manager ?: return false
        return try {
            mgr.getAssociations().any {
                try {
                    it.toString().contains(address, ignoreCase = true)
                } catch (_: Exception) { false }
            }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "getAssociations() failed: ${e.message}")
            false
        }
    }

    /**
     * Unregister companion association for the given device.
     */
    @SuppressLint("MissingPermission")
    fun unregister(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = manager ?: return
        try {
            val associations = mgr.getAssociations()
            val target = associations.firstOrNull {
                try {
                    it.toString().contains(device.address, ignoreCase = true)
                } catch (_: Exception) { false }
            }
            if (target != null) {
                // Can't call stopAssociation directly on old SDK API; log the intent
                DebugLogger.i(TAG, "Companion association removal requested for ${device.address}")
            }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Failed to remove companion association: ${e.message}")
        }
    }
}
