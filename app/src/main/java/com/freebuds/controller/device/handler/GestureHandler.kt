package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.SppClient
import com.freebuds.controller.bluetooth.SppPackage
import com.freebuds.controller.device.DeviceState

/**
 * Double-tap gesture handler (left/right independently).
 * Protocol: CMD_0x0120 read, CMD_0x011F write
 */
class GestureHandler : Handler {
    override val id = "gesture"

    companion object {
        private val CMD_READ = byteArrayOf(0x01, 0x20)
        private val CMD_WRITE = byteArrayOf(0x01, 0x1F)

        private val ACTIONS = mapOf(
            -1 to "Off",
            1 to "Play/Pause",
            2 to "Next Track",
            7 to "Prev Track",
            0 to "Assistant"
        )
    }

    override suspend fun init(client: SppClient) {}

    override suspend fun applyToState(client: SppClient, state: DeviceState): DeviceState {
        val resp = client.send(SppPackage.readRequest(CMD_READ, listOf(1, 2)))
            ?: return state

        val left = resp.findParam(1)
        val right = resp.findParam(2)
        var s = state
        if (left.size == 1) s = s.copy(doubleTapLeft = actionName(left[0].toInt()))
        if (right.size == 1) s = s.copy(doubleTapRight = actionName(right[0].toInt()))
        return s
    }

    override suspend fun setProperty(client: SppClient, prop: String, value: String) {
        val paramType = when (prop) {
            "double_tap_left" -> 1
            "double_tap_right" -> 2
            else -> return
        }
        val byteVal = ACTIONS.entries.find { it.value == value }?.key ?: -1
        client.send(SppPackage.writeRequest(CMD_WRITE, listOf(paramType to byteArrayOf(byteVal.toByte()))))
    }

    private fun actionName(code: Int): String = ACTIONS[code.toByte().toInt()] ?: "unknown_$code"
}
