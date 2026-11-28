package com.freebuds.controller.bluetooth

import com.freebuds.controller.adapter.huawei.protocol.HuaweiCommandCatalog
import com.freebuds.controller.protocol.HuaweiCapability
import com.freebuds.controller.protocol.HuaweiSppPackage

open class AbstractTapHandler(
    override val id: String,
    private val propPrefix: String,
    private val command: com.freebuds.controller.adapter.huawei.protocol.HuaweiCommandSpec,
    private val wInCall: Boolean,
    override val capabilities: List<HuaweiCapability>,
    optionsMap: Map<Int, String>? = null,
) : HuaweiDeviceHandler {
    override val commandIds = command.incomingCommandIds + listOf(command.writeCommand!!)
    override val properties = buildList {
        add("action" to "${propPrefix}_left")
        add("action" to "${propPrefix}_right")
        if (wInCall) add("action" to "${propPrefix}_in_call")
    }
    private val opts = optionsMap ?: mapOf(
        -1 to "tap_action_off",
        1 to "tap_action_pause",
        2 to "tap_action_next",
        7 to "tap_action_prev",
        0 to "tap_action_assistant",
    )
    private val callOpts = mapOf(-1 to "tap_action_off", 0 to "tap_action_answer")

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(command.readRequest(), operation = "$id.read")?.let { onPackage(it, driver) }
    }

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        onPackage(pkg, driver)
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        if (!pkg.commandId.contentEquals(command.readCommand)) return
        readSide(driver, "${propPrefix}_left", pkg.findParam(1), opts)
        readSide(driver, "${propPrefix}_right", pkg.findParam(2), opts)
        val available = pkg.findParam(3)
        if (available.isNotEmpty()) {
            val out = available.map { raw ->
                val code = raw.toInt().let { if (it > 127) it - 256 else it }
                opts[code] ?: code.toString()
            }.joinToString(",")
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
            "${propPrefix}_in_call" -> {
                if (!wInCall) return
                4 to callOpts
            }
            else -> return
        }
        driver.sendPackage(
            command.writeRequest(type to b(reverseOption(opts, value))),
            operation = "$id.write",
        )
        driver.putProperty(group, prop, value)
    }
}

class DoubleTapHandler(
    wInCall: Boolean = true,
    optionsMap: Map<Int, String>? = null,
) : AbstractTapHandler(
    id = "gesture_double",
    propPrefix = "double_tap",
    command = HuaweiCommandCatalog.doubleTap,
    wInCall = wInCall,
    capabilities = buildList {
        add(HuaweiCapability.ACTION_DOUBLE_TAP)
        if (wInCall) add(HuaweiCapability.ACTION_DOUBLE_TAP_IN_CALL)
    },
    optionsMap = optionsMap,
)

class TripleTapHandler(
    wInCall: Boolean = true,
) : AbstractTapHandler(
    id = "gesture_triple",
    propPrefix = "triple_tap",
    command = HuaweiCommandCatalog.tripleTap,
    wInCall = wInCall,
    capabilities = buildList {
        add(HuaweiCapability.ACTION_TRIPLE_TAP)
        if (wInCall) add(HuaweiCapability.ACTION_DOUBLE_TAP_IN_CALL)
    },
)

class SwipeGestureHandler : HuaweiDeviceHandler {
    private val command = HuaweiCommandCatalog.swipe
    override val id = "gesture_swipe"
    override val commandIds = command.incomingCommandIds + listOf(command.writeCommand!!)
    override val properties = listOf("action" to "swipe_gesture")
    override val capabilities = listOf(HuaweiCapability.ACTION_SWIPE)
    private val opts = mapOf(-1 to "tap_action_off", 0 to "tap_action_change_volume")

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(command.readRequest(), operation = "gesture_swipe.read")?.let { onPackage(it, driver) }
    }

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        onPackage(pkg, driver)
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        if (!pkg.commandId.contentEquals(command.readCommand)) return
        val action = pkg.findParam(1)
        if (action.size == 1) driver.putProperty("action", "swipe_gesture", opts[action.signedByte()] ?: action.signedByte().toString())
        driver.putProperty("action", "swipe_gesture_options", options(opts))
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        val v = reverseOption(opts, value)
        driver.sendPackage(
            command.writeRequest(1 to b(v), 2 to b(v)),
            operation = "gesture_swipe.write",
        )
        driver.putProperty(group, prop, value)
    }
}

class LongTapHandler(
    extraOptions: Boolean = false,
) : HuaweiDeviceHandler {
    private val command = HuaweiCommandCatalog.longTap
    override val id = "gesture_long"
    override val commandIds = command.incomingCommandIds
    override val ignoreCommandIds = listOf(command.writeCommand!!)
    override val properties = listOf("action" to "long_tap")
    override val capabilities = buildList {
        add(HuaweiCapability.ACTION_LONG_TAP)
        add(HuaweiCapability.ACTION_LONG_TAP_SPLIT)
        if (extraOptions) add(HuaweiCapability.ACTION_LONG_TAP_EXTRA)
    }
    private val opts = buildMap {
        put(-1, "noise_control_disabled")
        put(3, "noise_control_off_on")
        put(5, "noise_control_off_on_aw")
        put(6, "noise_control_on_aw")
        put(9, "noise_control_off_an")
        if (extraOptions) {
            put(0, "tap_action_assistant")
            put(18, "tap_action_vol_up")
            put(19, "tap_action_vol_down")
        }
    }

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(command.readRequest(), operation = "gesture_long.read")?.let { onPackage(it, driver) }
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
        val resp = driver.sendPackage(
            command.writeRequest(1 to b(v), 2 to b(v)),
            operation = "gesture_long.write",
        )
        if ((resp?.findParam(2)?.firstOrNull()?.toInt() ?: -1) == 0) driver.putProperty(group, prop, value)
    }
}

class PowerButtonHandler : HuaweiDeviceHandler {
    private val command = HuaweiCommandCatalog.doubleTap
    override val id = "gesture_power"
    override val commandIds = command.incomingCommandIds + listOf(command.writeCommand!!)
    override val properties = listOf("action" to "power_button")
    override val capabilities = listOf(HuaweiCapability.ACTION_POWER_BUTTON)
    private val opts = mapOf(-1 to "tap_action_off", 12 to "tap_action_switch_device")

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(command.readRequest(), operation = "gesture_power.read")?.let { onPackage(it, driver) }
    }

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        onPackage(pkg, driver)
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        if (!pkg.commandId.contentEquals(command.readCommand)) return
        val action = pkg.findParam(1)
        if (action.size == 1) driver.putProperty("action", "power_button", opts[action.signedByte()] ?: action.signedByte().toString())
        driver.putProperty("action", "power_button_options", options(opts))
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        val v = reverseOption(opts, value)
        driver.sendPackage(
            command.writeRequest(1 to b(v), 2 to b(v)),
            operation = "gesture_power.write",
        )
        driver.putProperty(group, prop, value)
    }
}
