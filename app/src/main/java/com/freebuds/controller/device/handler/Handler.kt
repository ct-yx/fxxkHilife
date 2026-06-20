package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.ISppClient
import com.freebuds.controller.device.DeviceState

interface Handler {
    val id: String
    suspend fun init(client: ISppClient)
    suspend fun applyToState(client: ISppClient, state: DeviceState): DeviceState
    suspend fun setProperty(client: ISppClient, prop: String, value: String)

    /**
     * Actively query device for initial state during connect.
     * Returns parsed [DeviceState] delta or null if handler doesn't support init read.
     *
     * Matches upstream pattern: connect → handler.on_init() → send read cmd → parse → store
     */
    suspend fun onInit(client: ISppClient, state: DeviceState): DeviceState? = null
}
