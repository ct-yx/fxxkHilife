package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.ISppClient
import com.freebuds.controller.bluetooth.SppCommand
import com.freebuds.controller.bluetooth.SppPackage
import com.freebuds.controller.device.DeviceState

/**
 * Battery handler, aligned with upstream OpenFreebuds battery.py:
 * - Param 1: global battery level (single byte, 0-100)
 * - Param 2: left/right/case levels (3 bytes)
 * - Param 3: charging status bitmask (bit 0=left charging, bit 1=right charging, bit 2=case charging)
 *
 * Fixed: now properly reads param 1 (global) as fallback when param 2 is missing/incomplete,
 * matching upstream behavior exactly.
 */
class BatteryHandler : Handler {
    override val id = "battery"

    override suspend fun init(client: ISppClient) {
        client.registerHandler(SppCommand.BATTERY_NOTIFY) { pkg -> handleBattery(pkg) }
    }

    override suspend fun applyToState(client: ISppClient, state: DeviceState): DeviceState {
        val resp = client.send(SppPackage.readRequest(SppCommand.BATTERY_READ, listOf(1, 2, 3)))
            ?: return state
        return handleBattery(resp, state)
    }

    override suspend fun setProperty(client: ISppClient, prop: String, value: String) {}

    private fun handleBattery(pkg: SppPackage, state: DeviceState = DeviceState()): DeviceState {
        val levelData = pkg.findParam(2)
        val chargeData = pkg.findParam(3)
        val globalData = pkg.findParam(1)

        // If param 2 (left/right/case) is missing or not exactly 3 bytes,
        // fall back to param 1 (global) — matching upstream battery.py
        if (levelData.size == 3) {
            // Upstream: if 2 in package.parameters and len(package.parameters[2]) == 3 and w_tws
            return state.copy(
                batteryLeft = levelData[0].toInt() and 0xFF,
                batteryRight = levelData[1].toInt() and 0xFF,
                batteryCase = levelData[2].toInt() and 0xFF,
                batteryChargingLeft = chargeData.contains(0x01.toByte()),
                batteryChargingRight = chargeData.contains(0x02.toByte()),
                batteryChargingCase = chargeData.contains(0x04.toByte())
            )
        } else if (globalData.size == 1) {
            // Upstream: if 1 in package.parameters and len(package.parameters[1]) == 1
            // Use global battery level for all three slots as fallback
            val global = globalData[0].toInt() and 0xFF
            return state.copy(
                batteryLeft = global,
                batteryRight = global,
                batteryCase = global,
                batteryChargingLeft = chargeData.contains(0x01.toByte()),
                batteryChargingRight = chargeData.contains(0x02.toByte()),
                batteryChargingCase = chargeData.contains(0x04.toByte())
            )
        }

        // Upstream doesn't update anything if neither param 1 nor param 2 is valid
        return state
    }
}
