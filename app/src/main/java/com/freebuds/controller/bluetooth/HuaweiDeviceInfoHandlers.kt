package com.freebuds.controller.bluetooth

import com.freebuds.controller.adapter.huawei.protocol.HuaweiCommandCatalog
import com.freebuds.controller.protocol.HuaweiCapability
import com.freebuds.controller.protocol.HuaweiSppPackage
import java.nio.charset.Charset

class InfoHandler : HuaweiDeviceHandler {
    private val command = HuaweiCommandCatalog.deviceInfo
    override val id = "device_info"
    override val commandIds = command.incomingCommandIds
    override val capabilities = listOf(HuaweiCapability.INFO)

    private val descriptor = mapOf(
        3 to "hardware_ver",
        7 to "software_ver",
        9 to "serial_number",
        10 to "device_submodel",
        15 to "device_model",
    )

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(command.readRequest(), operation = "device_info.read")?.let { onPackage(it, driver) }
    }

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        onPackage(pkg, driver)
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        val out = linkedMapOf<String, String>()
        for ((key, value) in pkg.parameters) {
            if (key == 24 && value.size >= 2 && value[0] == 'L'.code.toByte() && value[1] == '-'.code.toByte()) {
                parsePerEarSerials(out, value.decodeText())
                continue
            }
            out[descriptor[key] ?: "field_$key"] = value.decodeText()
        }
        driver.putProperty("info", null, out.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }

    private fun parsePerEarSerials(out: MutableMap<String, String>, data: String) {
        val parts = data.split(",")
        if (parts.size >= 2) {
            out["left_serial_number"] = parts[0].removePrefix("L-")
            out["right_serial_number"] = parts[1].removePrefix("R-")
        }
    }

    private fun ByteArray.decodeText(): String {
        for (name in listOf("UTF-8", "GBK", "GB2312", "US-ASCII")) {
            runCatching { return String(this, Charset.forName(name)) }
        }
        return hex()
    }
}

class InEarHandler : HuaweiDeviceHandler {
    private val command = HuaweiCommandCatalog.inEar
    override val id = "tws_in_ear"
    override val commandIds = command.incomingCommandIds
    override val capabilities = listOf(HuaweiCapability.IN_EAR, HuaweiCapability.WEAR_DETECT)

    override suspend fun onInit(driver: SppDriver) {
        driver.putProperty("state", "in_ear", "false")
    }

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        val value = pkg.findParam(8, 9)
        if (value.size == 1) {
            driver.putProperty("state", "in_ear", (value[0].toInt() == 1).asString())
        }
    }
}

class LogsHandler : HuaweiDeviceHandler {
    override val id = "drop_logs"
    override val commandIds = emptyList<ByteArray>()
    override val ignoreCommandIds = listOf(HuaweiCommandCatalog.logs.readCommand)
    override val capabilities = listOf(HuaweiCapability.LOGS)
}
