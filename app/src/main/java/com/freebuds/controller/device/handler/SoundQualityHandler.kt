package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.ISppClient
import com.freebuds.controller.bluetooth.SppCommand
import com.freebuds.controller.bluetooth.SppPackage
import com.freebuds.controller.device.DeviceState

class SoundQualityHandler : Handler {
    override val id = "sound_quality"

    override suspend fun init(client: ISppClient) {}

    override suspend fun applyToState(client: ISppClient, state: DeviceState): DeviceState {
        val resp = client.send(SppPackage.readRequest(SppCommand.SOUND_QUALITY_READ, listOf(1)))
            ?: return state
        val data = resp.findParam(1)
        return if (data.isNotEmpty()) {
            state.copy(soundQuality = if (data[0].toInt() == 1) "sqp_quality" else "sqp_connectivity")
        } else state
    }

    override suspend fun setProperty(client: ISppClient, prop: String, value: String) {
        if (prop == "sound_quality") {
            val byteVal = if (value == "sqp_quality") byteArrayOf(0x01) else byteArrayOf(0x00)
            client.send(SppPackage.writeRequest(SppCommand.SOUND_QUALITY_WRITE, listOf(1 to byteVal)))
        }
    }
}
