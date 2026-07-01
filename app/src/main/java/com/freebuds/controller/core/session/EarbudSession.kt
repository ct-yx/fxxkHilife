package com.freebuds.controller.core.session

import com.freebuds.controller.bluetooth.HuaweiDeviceHandler
import com.freebuds.controller.bluetooth.SppDriver
import com.freebuds.controller.data.DeviceProps

/**
 * Vendor-neutral runtime session boundary for a connected earbud.
 *
 * DeviceRepository owns high-level connection and UI state, while concrete sessions
 * own protocol transport, handler registration, property writes and state mapping.
 * The current Huawei implementation is a legacy SppDriver bridge; future vendors can
 * implement this interface without exposing their protocol internals to the repository.
 */
interface EarbudSession {
    val id: String
    val displayName: String
    val isConnected: Boolean
    val failedHandlerIds: Set<String>

    fun setPropertyChangedListener(listener: (() -> Unit)?)
    fun setDisconnectedListener(listener: (() -> Unit)?)

    fun registerHandler(handler: HuaweiDeviceHandler)
    fun getHandlerById(id: String): HuaweiDeviceHandler?

    suspend fun connect(): Boolean
    fun disconnect()
    suspend fun setProperty(group: String, prop: String, value: String)
    suspend fun mapState(failedHandlers: Collection<String>, connectedSince: Long?): DeviceProps

    /** Temporary bridge for legacy Huawei handlers while SppDriver is still the production path. */
    fun legacyDriverOrNull(): SppDriver? = null
}
