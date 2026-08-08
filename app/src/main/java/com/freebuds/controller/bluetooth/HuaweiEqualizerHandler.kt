package com.freebuds.controller.bluetooth

import com.freebuds.controller.adapter.huawei.protocol.HuaweiCommandCatalog
import com.freebuds.controller.protocol.HuaweiCapability
import com.freebuds.controller.protocol.HuaweiSppPackage
import kotlinx.coroutines.delay

class EqualizerPresetHandler(
    private val wCustom: Boolean = false,
    private val wFakeBuiltIn: Boolean = false,
    private val wCustomRows: Int = 10,
    private val wCustomMaxCount: Int = 3,
) : HuaweiDeviceHandler {
    private val command = HuaweiCommandCatalog.equalizerPreset
    override val id = "config_eq"
    override val commandIds = command.incomingCommandIds
    override val ignoreCommandIds = listOf(command.writeCommand!!)
    override val properties = listOf(
        "sound" to "equalizer_preset",
        "sound" to "equalizer_saved",
    )
    override val capabilities =
        listOf(HuaweiCapability.EQ_PRESET) + if (wCustom) listOf(HuaweiCapability.EQ_CUSTOM) else emptyList()

    private val knownBuiltInPresets = mapOf(
        1 to "equalizer_preset_default",
        2 to "equalizer_preset_hardbass",
        3 to "equalizer_preset_treble",
        9 to "equalizer_preset_voices",
    )
    private val fakeBuiltInPresets = listOf(
        -56 to "equalizer_preset_symphony",
        -55 to "equalizer_preset_hi_fi_live",
    )
    private val fakeBuiltInPresetData = mapOf(
        -56 to "0f0f0afb0f190ffb322d",
        -55 to "fb141e0a0000e7f60a00",
    )
    private var presetIdsByLabel: Map<String, Int> = emptyMap()
    private var changesSaved = true

    override suspend fun onInit(driver: SppDriver) {
        driver.sendPackage(
            command.readRequest(),
            operation = "equalizer.read",
        )?.let { onPackage(it, driver) }
    }

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        onPackage(pkg, driver)
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        if (!pkg.commandId.contentEquals(command.readCommand)) return

        val presets = linkedMapOf<Int, String>()
        val availableModes = pkg.findParam(3)
        if (availableModes.isNotEmpty()) {
            availableModes.forEach { raw ->
                val id = raw.toInt() and 0xFF
                presets[id] = knownBuiltInPresets[id] ?: "equalizer_preset_$id"
            }
        } else {
            presets.putAll(knownBuiltInPresets)
        }
        if (wFakeBuiltIn) {
            fakeBuiltInPresets.forEach { (id, label) -> presets[id] = label }
        }

        val customRows = mutableListOf<Int>()
        val customLabels = mutableListOf<String>()
        if (wCustom) {
            val customModes = pkg.findParam(8)
            var offset = 0
            while (offset + 36 <= customModes.size) {
                val row = customModes.copyOfRange(offset, offset + 36)
                val modeId = row[0].toInt()
                val count = row[1].toInt().coerceIn(0, wCustomRows)
                val labelBytes = row.copyOfRange(2 + count, row.size)
                    .takeWhile { it != 0.toByte() }
                    .toByteArray()
                val label = String(labelBytes, Charsets.UTF_8)
                    .ifBlank { "equalizer_preset_custom_$modeId" }
                presets[modeId] = label
                customLabels.add(label)
                if (customRows.isEmpty()) {
                    row.copyOfRange(2, 2 + count).forEach { customRows.add(it.toInt()) }
                }
                offset += 36
            }
        }

        presetIdsByLabel = presets.entries.associate { it.value to it.key }
        val currentId = pkg.findParam(2).firstOrNull()?.toInt()
        val currentLabel = currentId?.let { presets[it] ?: "unknown_$it" }

        val out = linkedMapOf(
            "equalizer_preset_options" to presets.values.joinToString(","),
            "equalizer_preset_create_options" to if (wCustom && !wFakeBuiltIn) fakeBuiltInPresets.joinToString(",") { it.second } else "",
            "equalizer_rows" to customRows.joinToString(","),
            "equalizer_saved" to changesSaved.asString(),
            "equalizer_rows_count" to wCustomRows.toString(),
            "equalizer_max_custom_modes" to if (wCustom) wCustomMaxCount.toString() else "0",
        )
        if (currentLabel != null) out["equalizer_preset"] = currentLabel
        if (customLabels.isNotEmpty()) out["equalizer_custom_options"] = customLabels.joinToString(",")
        driver.putProperty("sound", null, out.entries.joinToString("\n") { "${it.key}=${it.value}" }, extendGroup = true)
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        if (prop != "equalizer_preset") {
            com.freebuds.controller.util.LogBuffer.w("SPP", "EQ $prop write is not enabled without verified custom payload")
            return
        }
        val modeId = presetIdsByLabel[value] ?: value.removePrefix("equalizer_preset_").toIntOrNull()
        if (modeId == null) {
            com.freebuds.controller.util.LogBuffer.w("SPP", "Skip EQ preset write for unsupported/custom value=$value")
            return
        }
        driver.putProperty(group, prop, value)
        val overrideData = fakeBuiltInPresetData[modeId]
        val request = if (overrideData != null) {
            command.writeRequest(
                1 to b(modeId),
                2 to b(overrideData.length / 2),
                3 to overrideData.hexToBytes(),
                4 to value.toByteArray(Charsets.UTF_8),
                5 to b(1),
            )
        } else {
            command.writeRequest(1 to b(modeId))
        }
        driver.sendPackage(request, operation = "equalizer.write")
        delay(150)
        onInit(driver)
    }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
