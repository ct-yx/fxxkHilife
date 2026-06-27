package com.freebuds.controller.bluetooth

import com.freebuds.controller.protocol.HuaweiCapability
import com.freebuds.controller.protocol.HuaweiSppPackage

/**
 * 单个功能模块的 Handler 接口
 * 对照 OpenFreebuds 的 OfbDriverHandlerHuawei
 */
interface HuaweiDeviceHandler {

    /** Handler 唯一标识 */
    val id: String

    /** 该 Handler 处理的命令 ID 列表（用于包分发） */
    val commandIds: List<ByteArray>

    /** 该 Handler 代表的能力 */
    val capabilities: List<HuaweiCapability>

    /** 初始化（发送请求读取设备状态） */
    suspend fun onInit(driver: SppDriver)

    /** 收到设备推送包 */
    suspend fun onPackage(pkg: HuaweiSppPackage)
}
