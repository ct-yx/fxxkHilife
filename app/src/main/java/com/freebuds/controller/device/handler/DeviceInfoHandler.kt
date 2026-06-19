package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.ISppClient
import com.freebuds.controller.bluetooth.SppPackage
import com.freebuds.controller.device.DeviceState

/**
 * Device info handler — reads firmware, serial, hardware version.
 * Protocol: CMD_0x0107 read
 */
class DeviceInfoHandler : Handler {
    override val id = "device_info"

    companion object {
        private val CMD_READ = byteArrayOf(0x01, 0x07)

        private val FIELD_MAP = mapOf(
            3 to "hardware_ver",
            7 to "software_ver",
            9 to "serial_number",
            10 to "device_submodel",
            15 to "device_model"
        )
    }

    override suspend fun init(client: ISppClient) {}

    override suspend fun applyToState(client: ISppClient, state: DeviceState): DeviceState {
        val resp = client.send(SppPackage.readRequest(CMD_READ, (0..31).toList()))
            ?: return state

        var firmware = state.firmwareVersion
        var serial = state.serialNumber
        var hardware = state.hardwareVersion

        for ((key, value) in resp.parameters) {
            val str = try {
                String(value, Charsets.UTF_8).trim().trimEnd('\u0000')
            } catch (_: Exception) {
                value.joinToString("") { "%02x".format(it) }
            }
            when (key) {
                7 -> firmware = str
                9 -> serial = str
                3 -> hardware = str
            }
        }

        return state.copy(firmwareVersion = firmware, serialNumber = serial, hardwareVersion = hardware)
    }

    override suspend fun setProperty(client: ISppClient, prop: String, value: String) {}
}
