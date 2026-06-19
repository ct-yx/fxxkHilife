package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.ISppClient
import com.freebuds.controller.bluetooth.SppCommand
import com.freebuds.controller.bluetooth.SppPackage
import com.freebuds.controller.device.AncMode
import com.freebuds.controller.device.DeviceState

class AncModeHandler : Handler {
    override val id = "anc"

    override suspend fun init(client: ISppClient) {
        client.registerHandler(SppCommand.ANC_MODE_READ) { pkg -> handleAnc(pkg) }
    }

    override suspend fun applyToState(client: ISppClient, state: DeviceState): DeviceState {
        val resp = client.send(SppPackage.readRequest(SppCommand.ANC_MODE_READ, listOf(1)))
            ?: return state
        return state.copy(ancMode = parseAncMode(resp.findParam(1)))
    }

    override suspend fun setProperty(client: ISppClient, prop: String, value: String) {
        if (prop == "anc_mode") {
            val modeByte = when (value) {
                "cancellation" -> byteArrayOf(0x01)
                "awareness" -> byteArrayOf(0x02)
                else -> byteArrayOf(0x00)
            }
            client.send(SppPackage.writeRequest(SppCommand.ANC_MODE_WRITE, listOf(1 to modeByte)))
        }
    }

    private suspend fun handleAnc(pkg: SppPackage) {}

    private fun parseAncMode(data: ByteArray): AncMode = when {
        data.isEmpty() -> AncMode.UNKNOWN
        data[0].toInt() == 0x01 -> AncMode.CANCELLATION
        data[0].toInt() == 0x02 -> AncMode.AWARENESS
        else -> AncMode.OFF
    }
}
