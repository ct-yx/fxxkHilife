package com.freebuds.controller.core.transport

/**
 * Generic transport boundary for earbud control channels.
 *
 * The first production implementation is still the existing Huawei RFCOMM SPP driver,
 * but keeping this interface separate makes later BLE / native bridge / vendor SDK
 * transports possible without pushing more branches into DeviceRepository.
 */
interface EarbudTransport {
    val id: String
    val isConnected: Boolean

    suspend fun connect(): Boolean
    fun disconnect()

    suspend fun send(raw: ByteArray)
    fun setPacketListener(listener: (suspend (ByteArray) -> Unit)?)
    fun setDisconnectListener(listener: (() -> Unit)?)
}
