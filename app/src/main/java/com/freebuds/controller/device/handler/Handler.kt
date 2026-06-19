package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.SppClient
import com.freebuds.controller.device.DeviceState

interface Handler {
    val id: String
    suspend fun init(client: SppClient)
    suspend fun applyToState(client: SppClient, state: DeviceState): DeviceState
    suspend fun setProperty(client: SppClient, prop: String, value: String)
}
