package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.SppClient
import com.freebuds.controller.bluetooth.SppCommand
import com.freebuds.controller.bluetooth.SppPackage
import com.freebuds.controller.device.DeviceState

class BatteryHandler : Handler {
    override val id = "battery"

    override suspend fun init(client: SppClient) {
        client.registerHandler(SppCommand.BATTERY_NOTIFY) { pkg -> handleBattery(pkg) }
    }

    override suspend fun applyToState(client: SppClient, state: DeviceState): DeviceState {
        val resp = client.send(SppPackage.readRequest(SppCommand.BATTERY_READ, listOf(1)))
            ?: return state
        return handleBattery(resp, state)
    }

    override suspend fun setProperty(client: SppClient, prop: String, value: String) {}

    private fun handleBattery(pkg: SppPackage, state: DeviceState = DeviceState()): DeviceState {
        val data = pkg.findParam(1)
        if (data.isEmpty()) return state
        return state.copy(
            batteryLeft = if (data.size > 0) data[0].toInt() and 0xFF else state.batteryLeft,
            batteryRight = if (data.size > 1) data[1].toInt() and 0xFF else state.batteryRight,
            batteryCase = if (data.size > 2) data[2].toInt() and 0xFF else state.batteryCase
        )
    }
}
