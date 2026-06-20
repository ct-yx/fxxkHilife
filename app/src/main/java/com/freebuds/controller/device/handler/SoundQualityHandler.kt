package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.ISppClient
import com.freebuds.controller.bluetooth.SppCommand
import com.freebuds.controller.bluetooth.SppPackage
import com.freebuds.controller.device.DeviceState
import com.freebuds.controller.util.DebugLogger

class SoundQualityHandler : Handler {
    override val id = "sound_quality"

    companion object {
        private const val TAG = "SoundQualityHandler"
    }

    override suspend fun init(client: ISppClient) {}

    override suspend fun applyToState(client: ISppClient, state: DeviceState): DeviceState {
        val resp = client.send(SppPackage.readRequest(SppCommand.SOUND_QUALITY_READ, listOf(1)))
            ?: return state
        val data = resp.findParam(2)
        return if (data.isNotEmpty()) {
            state.copy(soundQuality = if (data[0].toInt() == 1) "sqp_quality" else "sqp_connectivity")
        } else state
    }

    override suspend fun setProperty(client: ISppClient, prop: String, value: String) {
        if (prop == "sound_quality") {
            val byteVal = if (value == "sqp_quality") byteArrayOf(0x01) else byteArrayOf(0x00)
            client.send(SppPackage.writeRequest(SppCommand.SOUND_QUALITY_WRITE, listOf(1 to byteVal), expectResponse = true), timeoutMs = 2000)
            // Upstream sound_quality_preference.py: write → on_init (re-read after write)
            kotlinx.coroutines.delay(500)
            val reRead = client.send(SppPackage.readRequest(SppCommand.SOUND_QUALITY_READ, listOf(1)))
            if (reRead != null) {
                val data = reRead.findParam(2)
                val readBack = if (data.isNotEmpty() && data[0].toInt() == 1) "sqp_quality" else "sqp_connectivity"
                DebugLogger.i(TAG, "Post-write sound_quality: wrote=$value, read back=$readBack")
            }
        }
    }
}
