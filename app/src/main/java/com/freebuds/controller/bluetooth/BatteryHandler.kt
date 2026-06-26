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
        HuaweiSppCommand.CMD_BATTERY_READ,
        HuaweiSppCommand.CMD_BATTERY_NOTIFY
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
        val pkg = HuaweiSppPackage.readRequest(HuaweiSppCommand.CMD_BATTERY_READ, 1, 2, 3)
        val resp = driver.sendPackage(pkg)
        if (resp != null) {
            onPackage(resp)
        } else {
            LogBuffer.w("Battery", "No response to battery read request")
        }
    }

    /** 处理电池数据包（包含主动请求响应和被动通知） */
    override suspend fun onPackage(pkg: HuaweiSppPackage) {
        val out = mutableMapOf<String, Int?>()

        // param 1: 综合电量 (1 byte)
        val p1 = pkg.findParam(1)
        if (p1.isNotEmpty() && p1.size == 1) {
            out["global"] = p1[0].toInt() and 0xFF
        }

        // param 2: 左右耳+充电盒 (3 bytes: left, right, case)
        if (wTws) {
            val p2 = pkg.findParam(2)
            if (p2.size == 3) {
                out["left"] = p2[0].toInt() and 0xFF
                out["right"] = p2[1].toInt() and 0xFF
                out["case"] = p2[2].toInt() and 0xFF
            }
        }

        // param 3: 充电状态 (包含 0x01 表示正在充电)
        val p3 = pkg.findParam(3)
        if (p3.isNotEmpty()) {
            out["is_charging"] = if (p3.contains(0x01.toByte())) 1 else 0
        }

        // 终端输出
        val parts = out.map { (k, v) -> "$k=${v ?: "?"}" }.joinToString(", ")
        LogBuffer.i("Battery", "[$parts]")

        batteryCallback?.invoke(out)
    }
}
