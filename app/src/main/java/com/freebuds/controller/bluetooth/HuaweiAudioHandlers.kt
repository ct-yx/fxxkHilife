package com.freebuds.controller.bluetooth

import com.freebuds.controller.adapter.huawei.protocol.HuaweiCommandCatalog
import com.freebuds.controller.adapter.huawei.protocol.HuaweiCommandPhase
import com.freebuds.controller.protocol.HuaweiCapability
import com.freebuds.controller.protocol.HuaweiSppPackage
import kotlinx.coroutines.delay

class AutoPauseHandler : HuaweiDeviceHandler {
    private val command = HuaweiCommandCatalog.autoPause
    override val id = "tws_auto_pause"
    override val commandIds = command.incomingCommandIds + listOf(command.writeCommand!!)
    override val properties = listOf("config" to "auto_pause")
    override val capabilities = listOf(HuaweiCapability.AUTO_PAUSE)

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(command.readRequest(), operation = "auto_pause.read")?.let { onPackage(it, driver) }
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        val data = pkg.findParam(1)
        if (data.size == 1) driver.putProperty("config", "auto_pause", (data[0].toInt() == 1).asString())
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        val target = value == "true"
        val resp = driver.sendPackage(
            command.writeRequest(1 to b(if (target) 1 else 0)),
            operation = "auto_pause.write",
        )
        if (resp?.findParam(127) == null) return

        // Upstream treats param 127 as write ACK. Some newer earbuds still return this
        // ACK even when the setting is not actually applied, so verify by reading back.
        delay(120)
        val confirm = driver.sendPackage(command.readRequest(), operation = "auto_pause.readback")
        val confirmed = confirm?.findParam(1)?.firstOrNull()?.let { (it.toInt() == 1) == target } == true
        if (confirmed) {
            driver.putProperty(group, prop, value)
        } else {
            com.freebuds.controller.util.LogBuffer.w("SPP", "Auto pause write was not confirmed by read-back")
            onInit(driver)
        }
    }
}

class LowLatencyHandler : HuaweiDeviceHandler {
    private companion object {
        const val WRITE_CONFIRM_TIMEOUT_MS = 2_000L
        const val WRITE_TO_READBACK_DELAY_MS = 1_000L
    }

    override val id = "low_latency"
    private val command = HuaweiCommandCatalog.lowLatency
    override val commandIds = command.incomingCommandIds
    override val properties = listOf("config" to "low_latency")
    override val capabilities = listOf(HuaweiCapability.LOW_LATENCY)

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(command.readRequest(), operation = "low_latency.read")?.let { onPackage(it, driver) }
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        val value = pkg.findParam(2)
        if (value.isNotEmpty()) driver.putProperty("config", "low_latency", (value[0].toInt() == 1).asString())
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        // Keep the entire write/read-back exchange short.  This is invoked by automatic low
        // latency during initialization, so an unsupported write must not hold the shared SPP
        // request lane for the driver's five-second default timeout.
        com.freebuds.controller.util.LogBuffer.i("SPP", "Low latency write start target=$value")
        val target = value == "true"
        val result = driver.writeAndReadBack(
            operation = "low_latency.write_readback",
            write = command.writeRequest(1 to b(if (target) 1 else 0)),
            read = command.readRequest(),
            writeTimeoutMs = WRITE_CONFIRM_TIMEOUT_MS,
            readTimeoutMs = WRITE_CONFIRM_TIMEOUT_MS,
            settleDelayMs = WRITE_TO_READBACK_DELAY_MS,
            readResponsePredicate = { it.findParam(2).isNotEmpty() },
            readBackPredicate = { it.findParam(2).firstOrNull()?.toInt() == if (target) 1 else 0 },
        )
        result.ack?.let { onPackage(it, driver) }
        result.readBack?.let { onPackage(it, driver) }
        when (result.phase) {
            HuaweiCommandPhase.READ_BACK_CONFIRMED -> com.freebuds.controller.util.LogBuffer.i(
                "SPP", "Low latency write confirmed target=$value"
            )
            HuaweiCommandPhase.READ_BACK_MISMATCH -> com.freebuds.controller.util.LogBuffer.w(
                "SPP", "Low latency write read-back mismatch target=$value actual=${driver.getProperty(group, prop) ?: "unknown"}"
            )
            HuaweiCommandPhase.ACKED -> com.freebuds.controller.util.LogBuffer.w(
                "SPP", "Low latency write ACK received without read-back target=$value"
            )
            HuaweiCommandPhase.TIMEOUT -> com.freebuds.controller.util.LogBuffer.w(
                "SPP", "Low latency write/read-back timeout target=$value"
            )
            HuaweiCommandPhase.SENT,
            HuaweiCommandPhase.FAILED -> com.freebuds.controller.util.LogBuffer.w(
                "SPP", "Low latency write failed phase=${result.phase} target=$value error=${result.error ?: "unknown"}"
            )
        }
    }
}

class SoundQualityHandler : HuaweiDeviceHandler {
    private val command = HuaweiCommandCatalog.soundQuality
    override val id = "config_sound_quality"
    override val commandIds = command.incomingCommandIds
    override val ignoreCommandIds = listOf(command.writeCommand!!)
    override val properties = listOf("sound" to "quality_preference")
    override val capabilities = listOf(HuaweiCapability.SOUND_QUALITY)
    private val opts = mapOf(0 to "sqp_connectivity", 1 to "sqp_quality")

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(command.readRequest(), operation = "sound_quality.read")?.let { onPackage(it, driver) }
    }

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        onPackage(pkg, driver)
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        val value = pkg.findParam(2)
        if (value.size == 1) {
            driver.putProperty("sound", "quality_preference", opts[value.signedByte()] ?: value.signedByte().toString())
            driver.putProperty("sound", "quality_preference_options", options(opts))
        }
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        val target = reverseOption(opts, value)
        driver.putProperty(group, prop, value)
        driver.putProperty("sound", "quality_preference_options", options(opts))

        // FreeBuds 6i often answers a 2ba2 write with an async 2ba3 state packet
        // instead of a direct 2ba2 ACK. Do not block on 2ba2, otherwise the first
        // switch looks delayed and repeated toggles are needed before UI catches up.
        driver.sendNowait(command.writeRequest(1 to b(target)), operation = "sound_quality.write")

        repeat(3) {
            kotlinx.coroutines.delay(250)
            val confirmed = driver.sendPackage(command.readRequest(), timeout = 700, operation = "sound_quality.readback")
            if (confirmed != null) {
                onPackage(confirmed, driver)
                if (driver.getProperty("sound", "quality_preference") == value) return
            }
        }
    }
}
