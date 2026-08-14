package com.freebuds.controller.core.adapter

import android.bluetooth.BluetoothDevice
import com.freebuds.controller.bluetooth.SppDriver
import com.freebuds.controller.core.capability.EarbudCapability
import com.freebuds.controller.core.state.EarbudState
import com.freebuds.controller.data.DeviceProps
import java.util.concurrent.CopyOnWriteArrayList

/** High level adapter identity for a vendor/protocol family. */
interface EarbudAdapter {
    val id: String
    val displayName: String

    /** Whether this adapter is a plausible match for the scanned Android Bluetooth device. */
    fun canHandle(device: BluetoothDevice): Boolean

    /** Generic capabilities for the model name selected by this adapter. */
    fun capabilities(deviceName: String): Set<EarbudCapability> = emptySet()

    /** Register protocol handlers onto the current legacy driver bridge. */
    fun registerHandlers(driver: SppDriver, deviceName: String, callbacks: EarbudAdapterCallbacks = EarbudAdapterCallbacks())

    /** Map adapter-owned raw properties into the app-level UI state snapshot. */
    suspend fun mapState(
        driver: SppDriver,
        pendingHandlers: Collection<String>,
        connectedSince: Long?,
    ): DeviceProps

    /** Generic state output; [DeviceProps] remains the compatibility projection. */
    suspend fun mapEarbudState(
        driver: SppDriver,
        deviceName: String,
        pendingHandlers: Collection<String>,
        connectedSince: Long?,
    ): EarbudState
}

/** Callbacks used by adapter-owned handlers to notify the repository without depending on it directly. */
data class EarbudAdapterCallbacks(
    val onStateChanged: suspend () -> Unit = {},
)

object EarbudAdapterRegistry {
    private val adapters = CopyOnWriteArrayList<EarbudAdapter>()

    fun register(adapter: EarbudAdapter) {
        if (adapters.none { it.id == adapter.id }) adapters.add(adapter)
    }

    fun all(): List<EarbudAdapter> = adapters.toList()

    fun findFor(device: BluetoothDevice): EarbudAdapter? = adapters.firstOrNull {
        runCatching { it.canHandle(device) }.getOrDefault(false)
    }
}
