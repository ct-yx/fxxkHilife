package com.freebuds.controller.bluetooth

import com.freebuds.controller.adapter.huawei.protocol.HuaweiCommandCatalog
import com.freebuds.controller.protocol.HuaweiCapability
import com.freebuds.controller.protocol.HuaweiSppPackage

class VoiceLanguageHandler : HuaweiDeviceHandler {
    private val command = HuaweiCommandCatalog.voiceLanguage
    override val id = "voice_language"
    override val commandIds = command.incomingCommandIds
    override val ignoreCommandIds = listOf(command.writeCommand!!)
    override val properties = listOf("service" to "language")
    override val capabilities = listOf(HuaweiCapability.VOICE_LANGUAGE)

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(command.readRequest(), operation = "voice_language.read")?.let { onPackage(it, driver) }
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        val locales = pkg.findParam(3)
        if (locales.size > 1) {
            driver.putProperty("service", "language", "")
            driver.putProperty("service", "language_options", String(locales, Charsets.UTF_8))
        }
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        driver.sendPackage(
            command.writeRequest(1 to value.toByteArray(Charsets.UTF_8), 2 to b(1)),
            operation = "voice_language.write",
        )
    }
}

class AncLegacyChangeHandler : HuaweiDeviceHandler {
    private val command = HuaweiCommandCatalog.ancLegacyChange
    private val ancRead = HuaweiCommandCatalog.anc
    override val id = "anc_change"
    override val commandIds = command.incomingCommandIds
    override val capabilities = listOf(HuaweiCapability.ANC_LEGACY)

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        val data = pkg.findParam(1)
        if (data.size == 1 && data[0].toInt() in 0..2) {
            driver.sendPackage(ancRead.readRequest(), operation = "anc_legacy.read")
        }
    }
}

class AncHandler(
    private val wCancelLevel: Boolean = true,
    private val wCancelDynamic: Boolean = true,
    private val wVoiceBoost: Boolean = true,
) : HuaweiDeviceHandler {
    private val command = HuaweiCommandCatalog.anc
    override val id = "anc_global"
    override val commandIds = command.incomingCommandIds
    override val ignoreCommandIds = listOf(command.writeCommand!!)
    override val properties = listOf("anc" to "mode", "anc" to "level")
    override val capabilities = listOf(HuaweiCapability.ANC, HuaweiCapability.ANC_LEVEL, HuaweiCapability.ANC_DYNAMIC, HuaweiCapability.VOICE_BOOST)

    private var activeMode = 0
    private var pendingMode: Int? = null
    private var pendingModeUntil: Long = 0L
    private var pendingLevel: Int? = null
    private var pendingLevelMode: Int = 0
    private var pendingLevelUntil: Long = 0L
    private val modeOptions = mapOf(0 to "normal", 1 to "cancellation", 2 to "awareness")
    private val cancelOptions = linkedMapOf(1 to "comfort", 0 to "normal", 2 to "ultra", 3 to "dynamic")
    private val awarenessOptions = mapOf(1 to "voice_boost", 2 to "normal")

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(
            command.readRequest(),
            responsePredicate = { it.findParam(1).isNotEmpty() },
            operation = "anc.read",
        )?.let { onPackage(it, driver) }
    }

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        onPackage(pkg, driver)
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        val data = pkg.findParam(1)
        if (data.size >= 1) {
            val modeByte = if (data.size == 2) data[1] else data[0]
            val level = if (data.size == 2) data[0].toInt() and 0xFF else 0
            val mode = modeByte.toInt() and 0xFF
            val now = System.currentTimeMillis()
            val targetMode = pendingMode
            if (targetMode != null && now >= pendingModeUntil) {
                pendingMode = null
                pendingModeUntil = 0L
            }
            if (targetMode != null && now < pendingModeUntil && mode != targetMode) {
                com.freebuds.controller.util.LogBuffer.d("SPP", "Ignore stale ANC state mode=$mode while pending=$targetMode")
                return
            }
            val targetLevel = pendingLevel
            if (targetLevel != null && now >= pendingLevelUntil) {
                pendingLevel = null
                pendingLevelUntil = 0L
            }
            if (targetLevel != null && now < pendingLevelUntil && mode == pendingLevelMode && level != targetLevel) {
                com.freebuds.controller.util.LogBuffer.d("SPP", "Ignore stale ANC level mode=$mode level=$level while pending=$targetLevel")
                return
            }
            // Keep the pending guard for the whole short window even after the first
            // target confirmation. Some earbuds send a correct 2b2a first and then
            // a stale 2b2c from the previous mode, which otherwise causes UI jump.
            activeMode = mode
            val out = linkedMapOf(
                "mode" to (modeOptions[mode] ?: mode.toString()),
                "mode_options" to options(modeOptions),
            )
            if (mode == 1 && wCancelLevel) {
                out["level"] = cancelOptions[level] ?: level.toString()
                out["level_options"] = options(cancelOptions)
            } else if (mode == 2 && wVoiceBoost) {
                out["level"] = awarenessOptions[level] ?: if (level == 0) "normal" else level.toString()
                out["level_options"] = options(awarenessOptions)
            } else {
                out["level"] = ""
                out["level_options"] = ""
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
        // 先直接写入目标值，防止 onInit 读请求超时而 UI 无反馈。
        // 切换主模式时同步刷新子模式/选项，避免“降噪强度/通透模式”混用旧 options。
        if (prop == "mode") {
            activeMode = valueByte
            pendingMode = valueByte
            pendingModeUntil = System.currentTimeMillis() + 4_000L
            pendingLevel = null
            pendingLevelUntil = 0L
            val out = linkedMapOf(
                "mode" to value,
                "mode_options" to options(modeOptions),
            )
            when (valueByte) {
                1 -> {
                    out["level"] = "normal"
                    out["level_options"] = options(cancelOptions)
                }
                2 -> {
                    out["level"] = "normal"
                    out["level_options"] = options(awarenessOptions)
                }
                else -> {
                    out["level"] = ""
                    out["level_options"] = ""
                }
            }
            driver.putProperty("anc", null, out.entries.joinToString("\n") { "${it.key}=${it.value}" })
        } else {
            pendingLevel = valueByte
            pendingLevelMode = activeMode
            pendingLevelUntil = System.currentTimeMillis() + 4_000L
            driver.putProperty(group, prop, value)
        }
        // 7i/6i 常见行为是写入 2b04 不直接 ACK，而是稍后用 2b2a/2b2c 异步上报状态。
        // 先保持乐观 UI，并用 pending guard 抑制旧状态回跳，不再阻塞等待 2b04 ACK。
        driver.sendNowait(command.writeRequest(1 to data), operation = "anc.write")
    }
}
