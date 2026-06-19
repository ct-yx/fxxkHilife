package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.ISppClient
import com.freebuds.controller.device.DeviceState

interface Handler {
    val id: String
    suspend fun init(client: ISppClient)
    suspend fun applyToState(client: ISppClient, state: DeviceState): DeviceState
    suspend fun setProperty(client: ISppClient, prop: String, value: String)
}
