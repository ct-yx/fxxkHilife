package com.freebuds.controller.core.session

import com.freebuds.controller.bluetooth.HuaweiDeviceHandler
import com.freebuds.controller.bluetooth.SppDriver
import com.freebuds.controller.core.capability.EarbudCapability
import com.freebuds.controller.core.state.EarbudState
import com.freebuds.controller.data.DeviceProps
import com.freebuds.controller.data.EarbudStateMapper

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
    val handlerIds: List<String>
        get() = emptyList()
    val capabilities: Set<EarbudCapability>
        get() = emptySet()

    fun setPropertyChangedListener(listener: (() -> Unit)?)
    fun setDisconnectedListener(listener: (() -> Unit)?)

    fun registerHandler(handler: HuaweiDeviceHandler)
    fun getHandlerById(id: String): HuaweiDeviceHandler?

    suspend fun connect(): Boolean
    suspend fun initializeCoreHandlers(timeoutMs: Long = 3_000L, maxAttempts: Int = 1) {}
    suspend fun initializeDeferredHandlers() {}
    fun disconnect()
    suspend fun setProperty(group: String, prop: String, value: String)
    suspend fun mapState(pendingHandlers: Collection<String>, connectedSince: Long?): DeviceProps

    suspend fun mapEarbudState(
        pendingHandlers: Collection<String>,
        connectedSince: Long?,
    ): EarbudState = EarbudStateMapper.fromDeviceProps(
        props = mapState(pendingHandlers, connectedSince),
        connectedSince = connectedSince,
    )

    /** Temporary bridge for legacy Huawei handlers while SppDriver is still the production path. */
    fun legacyDriverOrNull(): SppDriver? = null
}
