package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.ISppClient
import com.freebuds.controller.bluetooth.SppCommand
import com.freebuds.controller.bluetooth.SppPackage
import com.freebuds.controller.device.DeviceState

class BatteryHandler : Handler {
    override val id = "battery"

    override suspend fun init(client: ISppClient) {
        client.registerHandler(SppCommand.BATTERY_NOTIFY) { pkg -> handleBattery(pkg) }
    }

    override suspend fun applyToState(client: ISppClient, state: DeviceState): DeviceState {
        // Param 1=global level, 2=left/right/case levels, 3=charging status (aligned with upstream battery.py)
        val resp = client.send(SppPackage.readRequest(SppCommand.BATTERY_READ, listOf(1, 2, 3)))
            ?: return state
        return handleBattery(resp, state)
    }

    override suspend fun setProperty(client: ISppClient, prop: String, value: String) {}

    private fun handleBattery(pkg: SppPackage, state: DeviceState = DeviceState()): DeviceState {
        // Param 1: global battery (single byte)
        // Param 2: left/right/case (3 bytes)
        // Param 3: charging status (bitmask)
        val levelData = pkg.findParam(2)
        val chargeData = pkg.findParam(3)

        if (levelData.isEmpty()) return state

        return state.copy(
            batteryLeft = if (levelData.size > 0) levelData[0].toInt() and 0xFF else state.batteryLeft,
            batteryRight = if (levelData.size > 1) levelData[1].toInt() and 0xFF else state.batteryRight,
            batteryCase = if (levelData.size > 2) levelData[2].toInt() and 0xFF else state.batteryCase,
            batteryChargingLeft = chargeData.contains(0x01.toByte()),
            batteryChargingRight = chargeData.contains(0x02.toByte()),
            batteryChargingCase = chargeData.contains(0x04.toByte())
        )
    }
}
