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

    /** 该 Handler 明确忽略的命令 ID 列表（对照 OpenFreebuds ignore_commands） */
    val ignoreCommandIds: List<ByteArray>
        get() = emptyList()

    /** 该 Handler 可写入的属性（group, prop），空 prop 表示 group 级路由 */
    val properties: List<Pair<String, String>>
        get() = emptyList()

    /** 该 Handler 代表的能力 */
    val capabilities: List<HuaweiCapability>

    /** 初始化（发送请求读取设备状态） */
    suspend fun onInit(driver: SppDriver) {}

    /** 收到设备推送包 */
    suspend fun onPackage(pkg: HuaweiSppPackage) {}

    /** 收到设备推送包，带 driver 上下文，便于写入属性存储 */
    suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        onPackage(pkg)
    }

    /** 写入属性（对照 OpenFreebuds set_property） */
    suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {}
}
