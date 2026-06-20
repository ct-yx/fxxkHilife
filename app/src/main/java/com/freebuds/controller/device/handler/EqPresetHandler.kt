package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.ISppClient
import com.freebuds.controller.bluetooth.SppPackage
import com.freebuds.controller.device.DeviceState

/**
 * Equalizer preset handler — read preset list and current mode only.
 * Custom EQ editing not implemented (too complex for MVP).
 *
 * Protocol: CMD_0x2B4A read, CMD_0x2B49 write
 */
class EqPresetHandler : Handler {
    override val id = "eq_preset"

    companion object {
        private val CMD_READ = byteArrayOf(0x2B, 0x4A)
        private val CMD_WRITE = byteArrayOf(0x2B, 0x49)

        private val KNOWN_PRESETS = mapOf(
            1 to "Default",
            2 to "Hardbass",
            3 to "Treble",
            9 to "Voices"
        )
    }

    override suspend fun init(client: ISppClient) {}

    override suspend fun applyToState(client: ISppClient, state: DeviceState): DeviceState {
        val resp = client.send(SppPackage.readRequest(CMD_READ, listOf(1, 2, 3, 4, 5, 6, 7, 8)))
            ?: return state

        val options = mutableListOf<String>()
        // param 3 = available built-in preset IDs
        val avail = resp.findParam(3)
        if (avail.isNotEmpty()) {
            for (id in avail) {
                val idInt = id.toInt() and 0xFF
                options.add(KNOWN_PRESETS[idInt] ?: "preset_$idInt")
            }
        }
        // param 2 = current preset ID
        val cur = resp.findParam(2)
        val current = if (cur.size == 1) {
            val idInt = cur[0].toInt() and 0xFF
            KNOWN_PRESETS[idInt] ?: "unknown_$idInt"
        } else state.eqPreset

        return state.copy(eqPreset = current, eqPresetOptions = options.ifEmpty { state.eqPresetOptions })
    }

    override suspend fun setProperty(client: ISppClient, prop: String, value: String) {
        if (prop != "eq_preset") return
        // value is preset name like "Default"/"Hardbass"/etc
        val modeId = KNOWN_PRESETS.entries.find { it.value == value }?.key ?: 1
        // Fire-and-forget — device doesn't ACK write commands
        client.send(SppPackage.writeRequest(CMD_WRITE, listOf(1 to byteArrayOf(modeId.toByte())), expectResponse = false))
    }
}
