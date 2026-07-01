package com.freebuds.controller.core.session

import android.bluetooth.BluetoothDevice
import com.freebuds.controller.bluetooth.HuaweiDeviceHandler
import com.freebuds.controller.bluetooth.SppDriver
import com.freebuds.controller.core.adapter.EarbudAdapter
import com.freebuds.controller.data.DeviceProps

/**
 * EarbudSession implementation backed by the current legacy SppDriver.
 *
 * This keeps DeviceRepository off the concrete Huawei driver while allowing the
 * existing handler stack to continue working until each vendor moves to a native
 * EarbudTransport/EarbudProtocol session.
 */
class LegacySppEarbudSession(
    private val device: BluetoothDevice,
    private val adapter: EarbudAdapter,
) : EarbudSession {
    private val driver = SppDriver(device)

    override val id: String = adapter.id
    override val displayName: String = adapter.displayName
    override val isConnected: Boolean get() = driver.isConnected
    override val failedHandlerIds: Set<String> get() = driver.failedHandlerIds

    override fun setPropertyChangedListener(listener: (() -> Unit)?) {
        driver.onPropertyChanged = listener
    }

    override fun setDisconnectedListener(listener: (() -> Unit)?) {
        driver.onDisconnected = listener
    }

    override fun registerHandler(handler: HuaweiDeviceHandler) {
        driver.registerHandler(handler)
    }

    override fun getHandlerById(id: String): HuaweiDeviceHandler? = driver.getHandlerById(id)

    override suspend fun connect(): Boolean = driver.connect()

    override fun disconnect() = driver.disconnect()

    override suspend fun setProperty(group: String, prop: String, value: String) {
        driver.setProperty(group, prop, value)
    }

    override suspend fun mapState(
        failedHandlers: Collection<String>,
        connectedSince: Long?,
    ): DeviceProps = adapter.mapState(driver, failedHandlers, connectedSince)

    override fun legacyDriverOrNull(): SppDriver = driver
}