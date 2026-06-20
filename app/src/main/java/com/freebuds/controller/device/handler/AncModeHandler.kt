package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.ISppClient
import com.freebuds.controller.bluetooth.SppCommand
import com.freebuds.controller.bluetooth.SppPackage
import com.freebuds.controller.device.AncMode
import com.freebuds.controller.device.DeviceState
import com.freebuds.controller.util.DebugLogger

/**
 * Full ANC handler — ported from OpenFreebuds anc.py.
 * Reads mode (Off/Cancellation/Awareness) + level (cancel: comfort/normal/ultra/dynamic, awareness: voice_boost/normal)
 * Writes via 0x2B,0x04 with proper payload: mode → value+0xff/0x00, level → active_mode+value
 */
class AncModeHandler : Handler {
    override val id = "anc"

    companion object {
        private const val TAG = "AncModeHandler"

        // Mode mapping (from anc.py)
        val MODE_MAP = mapOf(
            0 to "normal",
            1 to "cancellation",
            2 to "awareness"
        )

        // Cancel level mapping (from anc.py, optionals: w_cancel_lvl, w_cancel_dynamic)
        val CANCEL_LEVEL_MAP = mapOf(
            1 to "comfort",
            0 to "normal",
            2 to "ultra",
            3 to "dynamic"  // only if w_cancel_dynamic
        )

        // Awareness level mapping (from anc.py, w_voice_boost)
        val AWARENESS_LEVEL_MAP = mapOf(
            1 to "voice_boost",
            2 to "normal"
        )
    }

    private var activeModeByte: Int = 0
    private var wCancelLevel: Boolean = true
    private var wCancelDynamic: Boolean = true
    private var wVoiceBoost: Boolean = true

    fun configure(cancelLevel: Boolean = true, cancelDynamic: Boolean = true, voiceBoost: Boolean = true): AncModeHandler {
        wCancelLevel = cancelLevel
        wCancelDynamic = cancelDynamic
        wVoiceBoost = voiceBoost
        return this
    }

    override suspend fun init(client: ISppClient) {
        client.registerHandler(SppCommand.ANC_MODE_READ) { pkg -> handleAnc(pkg) }
        DebugLogger.i(TAG, "AncModeHandler initialized (w_cancel_lvl=$wCancelLevel, w_dynamic=$wCancelDynamic, w_voice=$wVoiceBoost)")
    }

    override suspend fun applyToState(client: ISppClient, state: DeviceState): DeviceState {
        // Read cmd 0x2B,0x2A with params [1,2] — matches anc.py on_init
        val resp = client.send(SppPackage.readRequest(SppCommand.ANC_MODE_READ, listOf(1, 2)))
            ?: return state

        return handleAncResponse(resp, state)
    }

    override suspend fun setProperty(client: ISppClient, prop: String, value: String) {
        DebugLogger.i(TAG, "setProperty($prop, $value), activeMode=$activeModeByte")

        when (prop) {
            "anc_mode" -> {
                // Write mode with proper payload: value_byte + (0x00 if off else 0xff)
                val options = reverseMap(MODE_MAP)
                val valueByte = options[value] ?: 0
                val payload = if (valueByte == 0) byteArrayOf(valueByte.toByte(), 0x00)
                              else byteArrayOf(valueByte.toByte(), 0xFF.toByte())
                val pkg = SppPackage.writeRequest(SppCommand.ANC_MODE_WRITE, listOf(1 to payload))
                client.send(pkg)
                DebugLogger.i(TAG, "Written ANC mode: $value (byte=$valueByte)")
            }

            "anc_level" -> {
                // Write level: active_mode + value_byte
                val activeModeValue = activeModeByte.toByte()
                val options = when {
                    activeModeByte == 2 -> reverseMap(AWARENESS_LEVEL_MAP)
                    else -> reverseMap(CANCEL_LEVEL_MAP)
                }
                val valueByte = options[value] ?: 0
                val payload = byteArrayOf(activeModeValue, valueByte.toByte())
                val pkg = SppPackage.writeRequest(SppCommand.ANC_MODE_WRITE, listOf(1 to payload))
                client.send(pkg)
                DebugLogger.i(TAG, "Written ANC level: $value (byte=$valueByte, active_mode=$activeModeByte)")
            }
        }
    }

    private fun handleAnc(pkg: SppPackage) {
        DebugLogger.d(TAG, "ANC notification received: cmd=${pkg.commandId.contentToString()}")
    }

    private fun handleAncResponse(resp: SppPackage, state: DeviceState): DeviceState {
        val data = resp.findParam(1)
        if (data.size < 2) return state

        val modeByte = data[1].toInt() and 0xFF
        val levelByte = data[0].toInt() and 0xFF
        activeModeByte = modeByte

        val modeName = MODE_MAP[modeByte] ?: "unknown_$modeByte"
        val ancMode = when (modeName) {
            "cancellation" -> AncMode.CANCELLATION
            "awareness" -> AncMode.AWARENESS
            "normal" -> AncMode.OFF
            else -> AncMode.UNKNOWN
        }

        var s = state.copy(ancMode = ancMode)

        // Level parsing (matching anc.py logic)
        if (modeByte == 1 && wCancelLevel) {
            val levelName = if (wCancelDynamic) CANCEL_LEVEL_MAP[levelByte] ?: "unknown_$levelByte"
                          else CANCEL_LEVEL_MAP.filterKeys { it != 3 }[levelByte] ?: "unknown_$levelByte"
            s = s.copy(ancLevel = levelName, ancLevelOptions = getCancelLevelOptions())
        } else if (modeByte == 2 && wVoiceBoost) {
            val levelName = AWARENESS_LEVEL_MAP[levelByte] ?: "unknown_$levelByte"
            s = s.copy(ancLevel = levelName, ancLevelOptions = getAwarenessLevelOptions())
        }

        return s
    }

    private fun getCancelLevelOptions(): List<String> {
        val map = if (wCancelDynamic) CANCEL_LEVEL_MAP else CANCEL_LEVEL_MAP.filterKeys { it != 3 }
        return map.entries.sortedBy { it.key }.map { it.value }
    }

    private fun getAwarenessLevelOptions(): List<String> {
        return AWARENESS_LEVEL_MAP.entries.sortedBy { it.key }.map { it.value }
    }

    private fun reverseMap(map: Map<Int, String>): Map<String, Int> {
        return map.entries.associate { it.value to it.key }
    }
}
