package com.freebuds.controller.bluetooth

import com.freebuds.controller.protocol.HuaweiCapability
import com.freebuds.controller.protocol.HuaweiSppCommand
import com.freebuds.controller.protocol.HuaweiSppPackage
import com.freebuds.controller.util.LogBuffer

/**
 * 电池电量 Handler
 * 对照 OpenFreebuds: OfbHuaweiBatteryHandler
 *
 * 使用方式:
 *   val handler = BatteryHandler()
 *   handler.setOnBatteryUpdate { map -> ... }
 *   driver.registerHandler(handler)
 */
class BatteryHandler(private val wTws: Boolean = true) : HuaweiDeviceHandler {

    override val id = "battery"
    override val commandIds = listOf(
        HuaweiSppCommand.BATTERY_READ,
        HuaweiSppCommand.BATTERY_NOTIFY
    )
    override val capabilities = listOf(HuaweiCapability.BATTERY)

    private var batteryCallback: ((Map<String, Int?>) -> Unit)? = null

    /** 注册电池更新回调，每次收到电池数据时触发 */
    fun setOnBatteryUpdate(callback: (Map<String, Int?>) -> Unit) {
        batteryCallback = callback
    }

    /** 初始化：发送电池读取请求 (param 1=global, 2=left/right/case, 3=charging) */
    override suspend fun onInit(driver: SppDriver) {
        LogBuffer.i("Battery", "Requesting battery levels...")
        val pkg = HuaweiSppPackage.readRequest(HuaweiSppCommand.BATTERY_READ, 1, 2, 3)
        val resp = driver.sendPackage(pkg)
        if (resp != null) {
            onPackage(resp, driver)
        } else {
            // 响应为空就抛异常，让上层 initHandlers 重试（匹配上游 init() 重试逻辑）
            throw RuntimeException("No response to battery read request")
        }
    }

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        onPackage(pkg, driver)
    }

    /** 处理电池数据包（包含主动请求响应和被动通知） */
    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        val out = mutableMapOf<String, String>()

        // param 1: 综合电量 (至少 1 byte)
        val p1 = pkg.findParam(1)
        if (p1.isNotEmpty()) {
            out["global"] = (p1[0].toInt() and 0xFF).toString()
        }

        // param 2: 左右耳+充电盒 (至少 3 bytes)
        if (wTws) {
            val p2 = pkg.findParam(2)
            if (p2.size >= 3) {
                out["left"] = (p2[0].toInt() and 0xFF).toString()
                out["right"] = (p2[1].toInt() and 0xFF).toString()
                out["case"] = (p2[2].toInt() and 0xFF).toString()
            }
        }

        // param 3: 充电状态 (包含 0x01 表示正在充电)
        val p3 = pkg.findParam(3)
        if (p3.isNotEmpty()) {
            out["is_charging"] = if (p3.contains(0x01.toByte())) "true" else "false"
        }

        LogBuffer.i("Battery", "Update: $out")

        driver.putProperty("battery", null, out.entries.joinToString("\n") { (k, v) -> "$k=$v" })

        batteryCallback?.invoke(out.mapValues { it.value.toIntOrNull() })
    }
}
