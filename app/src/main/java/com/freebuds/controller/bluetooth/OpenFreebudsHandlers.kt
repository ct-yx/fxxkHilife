package com.freebuds.controller.bluetooth

import com.freebuds.controller.protocol.HuaweiCapability
import com.freebuds.controller.protocol.HuaweiSppCommand
import com.freebuds.controller.protocol.HuaweiSppPackage
import java.nio.charset.Charset

private fun b(vararg values: Int): ByteArray = values.map { it.toByte() }.toByteArray()
private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
private fun ByteArray.signedByte(): Int = first().toInt()
private fun Boolean.asString(): String = if (this) "true" else "false"
private fun options(values: Map<Int, String>): String = values.values.joinToString(",")
private fun reverseOption(values: Map<Int, String>, value: String): Int =
    values.entries.firstOrNull { it.value == value }?.key ?: value.toInt()

class InfoHandler : HuaweiDeviceHandler {
    override val id = "device_info"
    override val commandIds = listOf(b(0x01, 0x07))
    override val capabilities = listOf(HuaweiCapability.INFO)

    private val descriptor = mapOf(
        3 to "hardware_ver",
        7 to "software_ver",
        9 to "serial_number",
        10 to "device_submodel",
        15 to "device_model",
    )

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(HuaweiSppPackage.readRequest(b(0x01, 0x07), *IntArray(32) { it }))?.let { onPackage(it, driver) }
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
    override val id = "tws_in_ear"
    override val commandIds = listOf(b(0x2b, 0x03))
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
    override val ignoreCommandIds = listOf(b(0x0a, 0x0d))
    override val capabilities = listOf(HuaweiCapability.LOGS)
}

class AutoPauseHandler : HuaweiDeviceHandler {
    override val id = "tws_auto_pause"
    override val commandIds = listOf(HuaweiSppCommand.AUTO_PAUSE_READ, HuaweiSppCommand.AUTO_PAUSE_WRITE)
    override val properties = listOf("config" to "auto_pause")
    override val capabilities = listOf(HuaweiCapability.AUTO_PAUSE)

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(HuaweiSppPackage.readRequest(HuaweiSppCommand.AUTO_PAUSE_READ, 1))?.let { onPackage(it, driver) }
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        val data = pkg.findParam(1)
        if (data.size == 1) driver.putProperty("config", "auto_pause", (data[0].toInt() == 1).asString())
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        val resp = driver.sendPackage(HuaweiSppPackage.changeRequest(HuaweiSppCommand.AUTO_PAUSE_WRITE, 1 to b(if (value == "true") 1 else 0)))
        if (resp?.findParam(127) != null) driver.putProperty(group, prop, value)
    }
}

class LowLatencyHandler : HuaweiDeviceHandler {
    override val id = "low_latency"
    override val commandIds = listOf(HuaweiSppCommand.LOW_LATENCY)
    override val properties = listOf("config" to "low_latency")
    override val capabilities = listOf(HuaweiCapability.LOW_LATENCY)

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(HuaweiSppPackage.readRequest(HuaweiSppCommand.LOW_LATENCY, 2))?.let { onPackage(it, driver) }
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        val value = pkg.findParam(2)
        if (value.isNotEmpty()) driver.putProperty("config", "low_latency", (value[0].toInt() == 1).asString())
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        driver.sendPackage(HuaweiSppPackage.changeRequest(HuaweiSppCommand.LOW_LATENCY, 1 to b(if (value == "true") 1 else 0)))
        onInit(driver)
    }
}

class SoundQualityHandler : HuaweiDeviceHandler {
    override val id = "config_sound_quality"
    override val commandIds = listOf(b(0x2b, 0xa3))
    override val ignoreCommandIds = listOf(b(0x2b, 0xa2))
    override val properties = listOf("sound" to "quality_preference")
    override val capabilities = listOf(HuaweiCapability.SOUND_QUALITY)
    private val opts = mapOf(0 to "sqp_connectivity", 1 to "sqp_quality")

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(HuaweiSppPackage.readRequest(b(0x2b, 0xa3), 1))?.let { onPackage(it, driver) }
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        val value = pkg.findParam(2)
        if (value.size == 1) {
            driver.putProperty("sound", "quality_preference", opts[value.signedByte()] ?: value.signedByte().toString())
            driver.putProperty("sound", "quality_preference_options", options(opts))
        }
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        driver.sendPackage(HuaweiSppPackage.changeRequest(b(0x2b, 0xa2), 1 to b(reverseOption(opts, value))))
        onInit(driver)
    }
}

class VoiceLanguageHandler : HuaweiDeviceHandler {
    override val id = "voice_language"
    override val commandIds = listOf(b(0x0c, 0x02))
    override val ignoreCommandIds = listOf(b(0x0c, 0x01))
    override val properties = listOf("service" to "language")
    override val capabilities = listOf(HuaweiCapability.VOICE_LANGUAGE)

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(HuaweiSppPackage.readRequest(b(0x0c, 0x02), 1, 2))?.let { onPackage(it, driver) }
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        val locales = pkg.findParam(3)
        if (locales.size > 1) {
            driver.putProperty("service", "language", "")
            driver.putProperty("service", "language_options", String(locales, Charsets.UTF_8))
        }
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        driver.sendPackage(HuaweiSppPackage.changeRequest(b(0x0c, 0x01), 1 to value.toByteArray(Charsets.UTF_8), 2 to b(1)))
    }
}

class AncLegacyChangeHandler : HuaweiDeviceHandler {
    override val id = "anc_change"
    override val commandIds = listOf(b(0x2b, 0x03))
    override val capabilities = listOf(HuaweiCapability.ANC_LEGACY)

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        val data = pkg.findParam(1)
        if (data.size == 1 && data[0].toInt() in 0..2) {
            driver.sendPackage(HuaweiSppPackage(b(0x2b, 0x2a), parameters = mapOf(1 to byteArrayOf())))
        }
    }
}

class AncHandler(
    private val wCancelLevel: Boolean = true,
    private val wCancelDynamic: Boolean = true,
    private val wVoiceBoost: Boolean = true,
) : HuaweiDeviceHandler {
    override val id = "anc_global"
    override val commandIds = listOf(b(0x2b, 0x2a))
    override val ignoreCommandIds = listOf(b(0x2b, 0x04))
    override val properties = listOf("anc" to "mode", "anc" to "level")
    override val capabilities = listOf(HuaweiCapability.ANC, HuaweiCapability.ANC_LEVEL, HuaweiCapability.ANC_DYNAMIC, HuaweiCapability.VOICE_BOOST)

    private var activeMode = 0
    private val modeOptions = mapOf(0 to "normal", 1 to "cancellation", 2 to "awareness")
    private val cancelOptions = linkedMapOf(1 to "comfort", 0 to "normal", 2 to "ultra", 3 to "dynamic")
    private val awarenessOptions = mapOf(1 to "voice_boost", 2 to "normal")

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(HuaweiSppPackage.readRequest(b(0x2b, 0x2a), 1, 2))?.let { onPackage(it, driver) }
    }

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        onPackage(pkg, driver)
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        val data = pkg.findParam(1)
        if (data.size == 2) {
            val level = data[0].toInt() and 0xFF
            val mode = data[1].toInt() and 0xFF
            activeMode = mode
            val out = linkedMapOf(
                "mode" to (modeOptions[mode] ?: mode.toString()),
                "mode_options" to options(modeOptions),
            )
            if (mode == 1 && wCancelLevel) {
                out["level"] = cancelOptions[level] ?: level.toString()
                out["level_options"] = options(cancelOptions)
            } else if (mode == 2 && wVoiceBoost) {
                out["level"] = awarenessOptions[level] ?: level.toString()
                out["level_options"] = options(awarenessOptions)
            }
            driver.putProperty("anc", null, out.entries.joinToString("\n") { "${it.key}=${it.value}" })
        }
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        val valueByte = when {
            prop == "mode" -> reverseOption(modeOptions, value)
            activeMode == 2 -> reverseOption(awarenessOptions, value)
            else -> reverseOption(cancelOptions, value)
        }
        val data = if (prop == "mode") {
            b(valueByte, if (valueByte == 0) 0x00 else 0xff)
        } else {
            b(activeMode, valueByte)
        }
        driver.sendPackage(HuaweiSppPackage.changeRequest(b(0x2b, 0x04), 1 to data))
        onInit(driver)
    }
}

open class AbstractTapHandler(
    override val id: String,
    private val propPrefix: String,
    private val cmdRead: ByteArray,
    private val cmdWrite: ByteArray,
    private val wInCall: Boolean,
    override val capabilities: List<HuaweiCapability>,
) : HuaweiDeviceHandler {
    override val commandIds = listOf(cmdRead, cmdWrite)
    override val properties = listOf("action" to "${propPrefix}_left", "action" to "${propPrefix}_right", "action" to "${propPrefix}_in_call")
    private val opts = mapOf(-1 to "tap_action_off", 1 to "tap_action_pause", 2 to "tap_action_next", 7 to "tap_action_prev", 0 to "tap_action_assistant")
    private val callOpts = mapOf(-1 to "tap_action_off", 0 to "tap_action_answer")

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(HuaweiSppPackage.readRequest(cmdRead, 1, 2))?.let { onPackage(it, driver) }
    }

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        onPackage(pkg, driver)
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        if (!pkg.commandId.contentEquals(cmdRead)) return
        readSide(driver, "${propPrefix}_left", pkg.findParam(1), opts)
        readSide(driver, "${propPrefix}_right", pkg.findParam(2), opts)
        val available = pkg.findParam(3)
        if (available.isNotEmpty()) {
            val out = available.map { opts[it.toInt()] ?: it.toInt().toString() }.joinToString(",")
            driver.putProperty("action", "${propPrefix}_options", out)
        }
        val inCall = pkg.findParam(4)
        if (inCall.size == 1 && wInCall) {
            readSide(driver, "${propPrefix}_in_call", inCall, callOpts)
            driver.putProperty("action", "${propPrefix}_in_call_options", options(callOpts))
        }
    }

    private suspend fun readSide(driver: SppDriver, prop: String, data: ByteArray, opts: Map<Int, String>) {
        if (data.size == 1) driver.putProperty("action", prop, opts[data.signedByte()] ?: data.signedByte().toString())
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        val (type, opts) = when (prop) {
            "${propPrefix}_left" -> 1 to opts
            "${propPrefix}_right" -> 2 to opts
            "${propPrefix}_in_call" -> 4 to callOpts
            else -> return
        }
        driver.sendPackage(HuaweiSppPackage.changeRequest(cmdWrite, type to b(reverseOption(opts, value))))
        driver.putProperty(group, prop, value)
    }
}

class DoubleTapHandler : AbstractTapHandler("gesture_double", "double_tap", HuaweiSppCommand.DUAL_TAP_READ, HuaweiSppCommand.DUAL_TAP_WRITE, true, listOf(HuaweiCapability.ACTION_DOUBLE_TAP, HuaweiCapability.ACTION_DOUBLE_TAP_IN_CALL))
class TripleTapHandler : AbstractTapHandler("gesture_triple", "triple_tap", HuaweiSppCommand.TRIPLE_TAP_READ, HuaweiSppCommand.TRIPLE_TAP_WRITE, true, listOf(HuaweiCapability.ACTION_TRIPLE_TAP, HuaweiCapability.ACTION_DOUBLE_TAP_IN_CALL))

class SwipeGestureHandler : HuaweiDeviceHandler {
    override val id = "gesture_swipe"
    override val commandIds = listOf(HuaweiSppCommand.SWIPE_READ, HuaweiSppCommand.SWIPE_WRITE)
    override val properties = listOf("action" to "swipe_gesture")
    override val capabilities = listOf(HuaweiCapability.ACTION_SWIPE)
    private val opts = mapOf(-1 to "tap_action_off", 0 to "tap_action_change_volume")

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(HuaweiSppPackage.readRequest(HuaweiSppCommand.SWIPE_READ, 1, 2))?.let { onPackage(it, driver) }
    }

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        onPackage(pkg, driver)
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        if (!pkg.commandId.contentEquals(HuaweiSppCommand.SWIPE_READ)) return
        val action = pkg.findParam(1)
        if (action.size == 1) driver.putProperty("action", "swipe_gesture", opts[action.signedByte()] ?: action.signedByte().toString())
        driver.putProperty("action", "swipe_gesture_options", options(opts))
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        val v = reverseOption(opts, value)
        driver.sendPackage(HuaweiSppPackage.changeRequest(HuaweiSppCommand.SWIPE_WRITE, 1 to b(v), 2 to b(v)))
        driver.putProperty(group, prop, value)
    }
}

class LongTapHandler : HuaweiDeviceHandler {
    override val id = "gesture_long"
    override val commandIds = listOf(HuaweiSppCommand.LONG_TAP_SPLIT_READ_BASE)
    override val ignoreCommandIds = listOf(HuaweiSppCommand.LONG_TAP_SPLIT_WRITE_BASE)
    override val properties = listOf("action" to "long_tap")
    override val capabilities = listOf(HuaweiCapability.ACTION_LONG_TAP)
    private val opts = mapOf(-1 to "noise_control_disabled", 3 to "noise_control_off_on", 5 to "noise_control_off_on_aw", 6 to "noise_control_on_aw", 9 to "noise_control_off_an")

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(HuaweiSppPackage.readRequest(HuaweiSppCommand.LONG_TAP_SPLIT_READ_BASE, 1, 2))?.let { onPackage(it, driver) }
    }

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        onPackage(pkg, driver)
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        val value = pkg.findParam(1)
        if (value.size == 1) {
            driver.putProperty("action", "long_tap", opts[value.signedByte()] ?: value.signedByte().toString())
            driver.putProperty("action", "long_tap_options", options(opts))
        }
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        val v = reverseOption(opts, value)
        val resp = driver.sendPackage(HuaweiSppPackage.changeRequest(HuaweiSppCommand.LONG_TAP_SPLIT_WRITE_BASE, 1 to b(v), 2 to b(v)))
        if ((resp?.findParam(2)?.firstOrNull()?.toInt() ?: -1) == 0) driver.putProperty(group, prop, value)
    }
}

class PowerButtonHandler : HuaweiDeviceHandler {
    override val id = "gesture_power"
    override val commandIds = listOf(HuaweiSppCommand.DUAL_TAP_READ, HuaweiSppCommand.DUAL_TAP_WRITE)
    override val properties = listOf("action" to "power_button")
    override val capabilities = listOf(HuaweiCapability.ACTION_POWER_BUTTON)
    private val opts = mapOf(-1 to "tap_action_off", 12 to "tap_action_switch_device")

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(HuaweiSppPackage.readRequest(HuaweiSppCommand.DUAL_TAP_READ, 1, 2))?.let { onPackage(it, driver) }
    }

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        onPackage(pkg, driver)
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        if (!pkg.commandId.contentEquals(HuaweiSppCommand.DUAL_TAP_READ)) return
        val action = pkg.findParam(1)
        if (action.size == 1) driver.putProperty("action", "power_button", opts[action.signedByte()] ?: action.signedByte().toString())
        driver.putProperty("action", "power_button_options", options(opts))
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        val v = reverseOption(opts, value)
        driver.sendPackage(HuaweiSppPackage.changeRequest(HuaweiSppCommand.DUAL_TAP_WRITE, 1 to b(v), 2 to b(v)))
        driver.putProperty(group, prop, value)
    }
}
