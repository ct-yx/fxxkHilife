package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.ISppClient
import com.freebuds.controller.bluetooth.SppCommand
import com.freebuds.controller.bluetooth.SppPackage
import com.freebuds.controller.device.DeviceState

class LowLatencyHandler : Handler {
    override val id = "low_latency"

    override suspend fun init(client: ISppClient) {}

    override suspend fun applyToState(client: ISppClient, state: DeviceState): DeviceState {
        val resp = client.send(SppPackage.readRequest(SppCommand.LOW_LATENCY, listOf(2)))
            ?: return state
        val data = resp.findParam(2)
        return state.copy(lowLatency = data.isNotEmpty() && data[0].toInt() == 1)
    }

    override suspend fun setProperty(client: ISppClient, prop: String, value: String) {
        if (prop == "low_latency") {
            val on = value == "true"
            // Match upstream: write with ACK, then sleep(1), then re-read
            client.send(SppPackage.writeRequest(SppCommand.LOW_LATENCY,
                listOf(1 to if (on) byteArrayOf(0x01) else byteArrayOf(0x00)),
                expectResponse = true), timeoutMs = 2000)
            // Upstream asyncio.sleep(1) — give device time to apply
            kotlinx.coroutines.delay(1000)
        }
    }
}
