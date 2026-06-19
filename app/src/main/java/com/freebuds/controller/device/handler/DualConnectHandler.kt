package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.SppClient
import com.freebuds.controller.bluetooth.SppPackage
import com.freebuds.controller.device.DeviceState

/**
 * Dual-connect handler — reads enabled state + preferred device.
 * Full device list management not implemented for MVP.
 * Protocol: CMD_0x2B2F read, CMD_0x2B2E write
 */
class DualConnectHandler : Handler {
    override val id = "dual_connect"

    companion object {
        private val CMD_READ = byteArrayOf(0x2B, 0x2F)
        private val CMD_WRITE = byteArrayOf(0x2B, 0x2E)
    }

    override suspend fun init(client: SppClient) {}

    override suspend fun applyToState(client: SppClient, state: DeviceState): DeviceState {
        val resp = client.send(SppPackage.readRequest(CMD_READ, listOf(1)))
            ?: return state

        val value = resp.findParam(1)
        return if (value.size == 1) {
            state.copy(dualConnectEnabled = value[0].toInt() == 1)
        } else state
    }

    override suspend fun setProperty(client: SppClient, prop: String, value: String) {
        if (prop != "dual_connect_enabled") return
        val byteVal = if (value == "true") byteArrayOf(0x01) else byteArrayOf(0x00)
        client.send(SppPackage.writeRequest(CMD_WRITE, listOf(1 to byteVal)))
    }
}
