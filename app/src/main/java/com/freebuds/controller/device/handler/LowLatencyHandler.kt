package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.ISppClient
import com.freebuds.controller.bluetooth.SppCommand
import com.freebuds.controller.bluetooth.SppPackage
import com.freebuds.controller.device.DeviceState
import com.freebuds.controller.util.DebugLogger

class LowLatencyHandler : Handler {
    override val id = "low_latency"

    companion object {
        private const val TAG = "LowLatencyHandler"
    }

    override suspend fun init(client: ISppClient) {}

    override suspend fun onInit(client: ISppClient, state: DeviceState): DeviceState? {
        val resp = client.send(SppPackage.readRequest(SppCommand.LOW_LATENCY, listOf(2)))
            ?: return null
        val data = resp.findParam(2)
        return state.copy(lowLatency = data.isNotEmpty() && data[0].toInt() == 1)
    }

    override suspend fun applyToState(client: ISppClient, state: DeviceState): DeviceState {
        val resp = client.send(SppPackage.readRequest(SppCommand.LOW_LATENCY, listOf(2)))
            ?: return state
        val data = resp.findParam(2)
        return state.copy(lowLatency = data.isNotEmpty() && data[0].toInt() == 1)
    }

    override suspend fun setProperty(client: ISppClient, prop: String, value: String) {
        if (prop == "low_latency") {
            val on = value == "true"
            client.send(SppPackage.writeRequest(SppCommand.LOW_LATENCY,
                listOf(1 to if (on) byteArrayOf(0x01) else byteArrayOf(0x00)),
                expectResponse = true), timeoutMs = 2000)
            // Upstream low_latency.py: write → sleep(1) → on_init (re-read)
            kotlinx.coroutines.delay(1000)
            val reRead = client.send(SppPackage.readRequest(SppCommand.LOW_LATENCY, listOf(2)))
            if (reRead != null) {
                val data = reRead.findParam(2)
                val readBack = data.isNotEmpty() && data[0].toInt() == 1
                DebugLogger.i(TAG, "Post-write low_latency: wrote=$on, read back=$readBack")
            }
        }
    }
}
