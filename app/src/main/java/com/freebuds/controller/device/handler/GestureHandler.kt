package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.ISppClient
import com.freebuds.controller.bluetooth.SppCommand
import com.freebuds.controller.bluetooth.SppPackage
import com.freebuds.controller.device.DeviceState
import com.freebuds.controller.util.DebugLogger

/**
 * Full gesture handler — ported from OpenFreebuds 4 handlers:
 *   action_dual_tap.py, action_triple_tap.py,
 *   action_long_tap.py, action_swipe_gesture.py
 *
 * Covers: double_tap, triple_tap, long_tap (unified), swipe_gesture
 */
class GestureHandler : Handler {
    override val id = "gesture"

    companion object {
        private const val TAG = "GestureHandler"

        // Double-tap options (from multi_tap.py)
        val DOUBLE_TAP_ACTIONS = mapOf(
            -1 to "tap_action_off",
            1 to "tap_action_pause",
            2 to "tap_action_next",
            7 to "tap_action_prev",
            0 to "tap_action_assistant"
        )

        // Triple-tap options (same structure as double-tap)
        val TRIPLE_TAP_ACTIONS = mapOf(
            -1 to "tap_action_off",
            1 to "tap_action_pause",
            2 to "tap_action_next",
            7 to "tap_action_prev",
            0 to "tap_action_assistant"
        )

        // Long-tap options (from action_long_tap.py)
        val LONG_TAP_ACTIONS = mapOf(
            -1 to "noise_control_disabled",
            3 to "noise_control_off_on",
            5 to "noise_control_off_on_aw",
            6 to "noise_control_on_aw",
            9 to "noise_control_off_an"
        )

        // Swipe options (from action_swipe_gesture.py)
        val SWIPE_ACTIONS = mapOf(
            -1 to "tap_action_off",
            0 to "tap_action_change_volume"
        )
    }

    override suspend fun init(client: ISppClient) {
        DebugLogger.i(TAG, "GestureHandler initialized — covers double/triple/long/swipe")
    }

    override suspend fun applyToState(client: ISppClient, state: DeviceState): DeviceState {
        var s = state

        // Double-tap read (CMD 0x01,0x20)
        try {
            val resp = client.send(SppPackage.readRequest(SppCommand.DUAL_TAP_READ, listOf(1, 2)))
            if (resp != null) {
                val left = resp.findParam(1)
                val right = resp.findParam(2)
                if (left.size == 1) s = s.copy(doubleTapLeft = actionName(DOUBLE_TAP_ACTIONS, left[0].toInt()))
                if (right.size == 1) s = s.copy(doubleTapRight = actionName(DOUBLE_TAP_ACTIONS, right[0].toInt()))
            }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Double-tap read failed: ${e.message}")
        }

        // Triple-tap read (CMD 0x01,0x26)
        try {
            val resp = client.send(SppPackage.readRequest(SppCommand.TRIPLE_TAP_READ, listOf(1, 2)))
            if (resp != null) {
                val left = resp.findParam(1)
                val right = resp.findParam(2)
                if (left.size == 1) s = s.copy(tripleTapLeft = actionName(TRIPLE_TAP_ACTIONS, left[0].toInt()))
                if (right.size == 1) s = s.copy(tripleTapRight = actionName(TRIPLE_TAP_ACTIONS, right[0].toInt()))
            }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Triple-tap read failed: ${e.message}")
        }

        // Long-tap read (CMD 0x2B,0x17)
        try {
            val resp = client.send(SppPackage.readRequest(SppCommand.LONG_TAP_BASE_READ, listOf(1, 2)))
            if (resp != null) {
                val value = resp.findParam(1)
                if (value.size == 1) {
                    val intVal = if (value[0].toInt() > 127) value[0].toInt() - 256 else value[0].toInt()
                    s = s.copy(longTapAction = actionName(LONG_TAP_ACTIONS, intVal))
                }
            }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Long-tap read failed: ${e.message}")
        }

        // Swipe gesture read (CMD 0x2B,0x1F)
        try {
            val resp = client.send(SppPackage.readRequest(SppCommand.SWIPE_READ, listOf(1, 2)))
            if (resp != null) {
                val value = resp.findParam(1)
                if (value.size == 1) {
                    val intVal = if (value[0].toInt() > 127) value[0].toInt() - 256 else value[0].toInt()
                    s = s.copy(swipeGesture = actionName(SWIPE_ACTIONS, intVal))
                }
            }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Swipe read failed: ${e.message}")
        }

        return s
    }

    override suspend fun setProperty(client: ISppClient, prop: String, value: String) {
        DebugLogger.i(TAG, "setProperty($prop, $value)")
        when (prop) {
            "double_tap_left" -> writeTapAction(client, SppCommand.DUAL_TAP_WRITE, 1, value, DOUBLE_TAP_ACTIONS)
            "double_tap_right" -> writeTapAction(client, SppCommand.DUAL_TAP_WRITE, 2, value, DOUBLE_TAP_ACTIONS)
            "triple_tap_left" -> writeTapAction(client, SppCommand.TRIPLE_TAP_WRITE, 1, value, TRIPLE_TAP_ACTIONS)
            "triple_tap_right" -> writeTapAction(client, SppCommand.TRIPLE_TAP_WRITE, 2, value, TRIPLE_TAP_ACTIONS)
            "long_tap_action" -> writeLongTap(client, value)
            "swipe_gesture" -> writeSwipe(client, value)
        }
    }

    private suspend fun writeTapAction(client: ISppClient, cmd: ByteArray, paramType: Int, value: String, actions: Map<Int, String>) {
        val byteVal = actions.entries.find { it.value == value }?.key ?: -1
        val payloadByte = if (byteVal < 0) (byteVal + 256).toByte() else byteVal.toByte()
        client.send(SppPackage.writeRequest(cmd, listOf(paramType to byteArrayOf(payloadByte))))
        DebugLogger.i(TAG, "Written tap action: cmd=${cmd.contentToString()}, param=$paramType, value=$value(byte=$byteVal)")
    }

    private suspend fun writeLongTap(client: ISppClient, value: String) {
        val byteVal = LONG_TAP_ACTIONS.entries.find { it.value == value }?.key ?: -1
        val payloadByte = if (byteVal < 0) (byteVal + 256).toByte() else byteVal.toByte()
        client.send(SppPackage.writeRequest(SppCommand.LONG_TAP_BASE_WRITE, listOf(
            1 to byteArrayOf(payloadByte),
            2 to byteArrayOf(payloadByte)
        )))
    }

    private suspend fun writeSwipe(client: ISppClient, value: String) {
        val byteVal = SWIPE_ACTIONS.entries.find { it.value == value }?.key ?: -1
        val payloadByte = if (byteVal < 0) (byteVal + 256).toByte() else byteVal.toByte()
        client.send(SppPackage.writeRequest(SppCommand.SWIPE_WRITE, listOf(
            1 to byteArrayOf(payloadByte),
            2 to byteArrayOf(payloadByte)
        )))
    }

    private fun actionName(actions: Map<Int, String>, byteVal: Int): String {
        val signed = if (byteVal > 127) byteVal - 256 else byteVal
        return actions[signed] ?: "unknown_$byteVal"
    }
}
