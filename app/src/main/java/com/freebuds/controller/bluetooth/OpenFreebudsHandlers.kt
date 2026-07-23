package com.freebuds.controller.bluetooth

import com.freebuds.controller.protocol.HuaweiCapability
import com.freebuds.controller.protocol.HuaweiSppCommand
import com.freebuds.controller.protocol.HuaweiSppPackage
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
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
        driver.sendPackage(HuaweiSppPackage.readRequest(b(0x01, 0x07), 3, 7, 9, 10, 15))?.let { onPackage(it, driver) }
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
        val target = value == "true"
        val resp = driver.sendPackage(
            HuaweiSppPackage.changeRequest(
                HuaweiSppCommand.AUTO_PAUSE_WRITE,
                1 to b(if (target) 1 else 0),
            )
        )
        if (resp?.findParam(127) == null) return

        // Upstream treats param 127 as write ACK. Some newer earbuds still return this
        // ACK even when the setting is not actually applied, so verify by reading back.
        delay(120)
        val confirm = driver.sendPackage(HuaweiSppPackage.readRequest(HuaweiSppCommand.AUTO_PAUSE_READ, 1))
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
        const val WRITE_CONFIRM_TIMEOUT_MS = 1_000L
    }

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
        // Keep the entire write/read-back exchange short.  This is invoked by automatic low
        // latency during initialization, so an unsupported write must not hold the shared SPP
        // request lane for the driver's five-second default timeout.
        com.freebuds.controller.util.LogBuffer.i("SPP", "Low latency write start target=$value")
        val writeResponse = driver.sendPackage(
            HuaweiSppPackage.changeRequest(
                HuaweiSppCommand.LOW_LATENCY,
                1 to b(if (value == "true") 1 else 0),
            ),
            timeout = WRITE_CONFIRM_TIMEOUT_MS,
        )
        if (writeResponse != null) {
            com.freebuds.controller.util.LogBuffer.d("SPP", "Low latency write ACK received target=$value")
            onPackage(writeResponse, driver)
        } else {
            com.freebuds.controller.util.LogBuffer.w("SPP", "Low latency write ACK timeout target=$value; reading back")
        }

        val readBack = driver.sendPackage(
            HuaweiSppPackage.readRequest(HuaweiSppCommand.LOW_LATENCY, 2),
            timeout = WRITE_CONFIRM_TIMEOUT_MS,
        )
        if (readBack != null) {
            onPackage(readBack, driver)
            val actual = driver.getProperty(group, prop)
            if (actual == value) {
                com.freebuds.controller.util.LogBuffer.i(
                    "SPP",
                    "Low latency write confirmed target=$value"
                )
            } else {
                com.freebuds.controller.util.LogBuffer.w(
                    "SPP",
                    "Low latency write read-back mismatch target=$value actual=${actual ?: "unknown"}"
                )
            }
        } else {
            com.freebuds.controller.util.LogBuffer.w(
                "SPP",
                "Low latency write was not confirmed by read-back target=$value"
            )
        }
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
        driver.sendNowait(HuaweiSppPackage.changeRequest(b(0x2b, 0xa2), 1 to b(target)))

        repeat(3) {
            kotlinx.coroutines.delay(250)
            val confirmed = driver.sendPackage(HuaweiSppPackage.readRequest(b(0x2b, 0xa3), 1), timeout = 700)
            if (confirmed != null) {
                onPackage(confirmed, driver)
                if (driver.getProperty("sound", "quality_preference") == value) return
            }
        }
    }
}

class EqualizerPresetHandler(
    private val wCustom: Boolean = false,
    private val wFakeBuiltIn: Boolean = false,
    private val wCustomRows: Int = 10,
    private val wCustomMaxCount: Int = 3,
) : HuaweiDeviceHandler {
    override val id = "config_eq"
    override val commandIds = listOf(HuaweiSppCommand.EQ_PRESET_READ)
    override val ignoreCommandIds = listOf(HuaweiSppCommand.EQ_PRESET_WRITE)
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
            HuaweiSppPackage.readRequest(HuaweiSppCommand.EQ_PRESET_READ, 1, 2, 3, 4, 5, 6, 7, 8)
        )?.let { onPackage(it, driver) }
    }

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        onPackage(pkg, driver)
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        if (!pkg.commandId.contentEquals(HuaweiSppCommand.EQ_PRESET_READ)) return

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
            HuaweiSppPackage.changeRequest(
                HuaweiSppCommand.EQ_PRESET_WRITE,
                1 to b(modeId),
                2 to b(overrideData.length / 2),
                3 to overrideData.hexToBytes(),
                4 to value.toByteArray(Charsets.UTF_8),
                5 to b(1),
            )
        } else {
            HuaweiSppPackage.changeRequest(HuaweiSppCommand.EQ_PRESET_WRITE, 1 to b(modeId))
        }
        driver.sendPackage(request)
        delay(150)
        onInit(driver)
    }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

class DualConnectHandler(
    private val wAutoConnect: Boolean = true,
) : HuaweiDeviceHandler {
    override val id = "dual_connect"
    override val initTimeoutMs = 1_200L
    override val initAttemptMax = 6
    override val commandIds = listOf(
        HuaweiSppCommand.DUAL_CONNECT_ENUMERATE,
        HuaweiSppCommand.DUAL_CONNECT_CHANGE_EVENT,
        HuaweiSppCommand.DUAL_CONNECT_ENABLED_READ,
    )
    override val ignoreCommandIds = listOf(
        HuaweiSppCommand.DUAL_CONNECT_ENABLED_WRITE,
        HuaweiSppCommand.DUAL_CONNECT_PREFERRED_WRITE,
        HuaweiSppCommand.DUAL_CONNECT_EXECUTE,
    )
    override val properties = listOf("dual_connect" to "")
    override val capabilities = listOf(HuaweiCapability.DUAL_CONNECT, HuaweiCapability.DUAL_CONNECT_AUTO)

    private val pendingDevices = mutableMapOf<Int, DualConnectRow>()
    private var expectedCount = Int.MAX_VALUE

    override suspend fun onInit(driver: SppDriver) {
        pendingDevices.clear()
        expectedCount = Int.MAX_VALUE
        requestEnabledNoWait(driver)
        driver.sendNowait(HuaweiSppPackage.changeRequestNoWait(HuaweiSppCommand.DUAL_CONNECT_ENUMERATE, 1 to byteArrayOf()))
        waitForEnumeration(driver)
    }

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        if (pkg.commandId.contentEquals(HuaweiSppCommand.DUAL_CONNECT_CHANGE_EVENT)) {
            com.freebuds.controller.util.LogBuffer.i("SPP", "dual_connect change event received; scheduling refresh")
            onInit(driver)
            return
        }
        onPackage(pkg, driver)
    }

    private suspend fun readEnabled(driver: SppDriver) {
        driver.sendPackage(HuaweiSppPackage.readRequest(HuaweiSppCommand.DUAL_CONNECT_ENABLED_READ, 1), timeout = 1000)
            ?.let { pkg ->
                val enabled = pkg.findParam(1).firstOrNull()?.toInt() == 1
                driver.putProperty("dual_connect", "enabled", enabled.asString())
            }
    }

    private suspend fun requestEnabledNoWait(driver: SppDriver) {
        driver.sendNowait(
            HuaweiSppPackage(
                commandId = HuaweiSppCommand.DUAL_CONNECT_ENABLED_READ,
                responseId = byteArrayOf(),
                parameters = mutableMapOf(1 to byteArrayOf()),
            )
        )
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        when {
            pkg.commandId.contentEquals(HuaweiSppCommand.DUAL_CONNECT_ENABLED_READ) -> {
                val enabled = pkg.findParam(1).firstOrNull()?.toInt() == 1
                driver.putProperty("dual_connect", "enabled", enabled.asString())
            }
            pkg.commandId.contentEquals(HuaweiSppCommand.DUAL_CONNECT_ENUMERATE) -> {
                val row = parseRow(pkg) ?: return
                expectedCount = pkg.findParam(2).toPositiveIntOrNull() ?: expectedCount
                val index = pkg.findParam(3).toPositiveIntOrNull() ?: pendingDevices.size
                pendingDevices[index] = row
                com.freebuds.controller.util.LogBuffer.i(
                    "SPP",
                    "dual_connect row received index=$index expected=$expectedCount address=${row.address} connected=${row.connected}"
                )
                publishRows(driver)
            }
        }
    }

    private suspend fun waitForEnumeration(driver: SppDriver) {
        val completed = withTimeoutOrNull(1_000) {
            while (pendingDevices.isEmpty() || pendingDevices.size < expectedCount) {
                delay(80)
            }
            true
        } == true
        if (!completed && pendingDevices.isNotEmpty()) {
            com.freebuds.controller.util.LogBuffer.i(
                "SPP",
                "dual_connect accepted partial enumeration rows=${pendingDevices.size} expected=$expectedCount"
            )
        }
        publishRows(driver)
    }

    private suspend fun publishRows(driver: SppDriver) {
        val ordered = pendingDevices.toSortedMap().values.toList()
        val rows = ordered.joinToString("|") { row ->
            listOf(
                row.address,
                row.name.encodeForList(),
                row.autoConnect?.asString() ?: "",
                row.preferred.asString(),
                row.connected.asString(),
                row.playing.asString(),
            ).joinToString(";")
        }
        val preferred = ordered.firstOrNull { it.preferred }?.address.orEmpty()
        driver.putProperty("dual_connect", "devices", rows)
        driver.putProperty("dual_connect", "preferred_device", preferred)
        if (rows.isNotEmpty()) {
            com.freebuds.controller.util.LogBuffer.d("SPP", "dual_connect published ${ordered.size} devices")
        }
    }

    private fun parseRow(pkg: HuaweiSppPackage): DualConnectRow? {
        val address = pkg.findParam(4).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        if (address.length < 12) return null
        val connState = pkg.findParam(5).firstOrNull()?.toInt() ?: 0
        val preferred = pkg.findParam(7).firstOrNull()?.toInt() == 1
        val autoConnect = if (wAutoConnect) pkg.findParam(8).firstOrNull()?.let { it.toInt() == 1 } else null
        val name = String(pkg.findParam(9), Charsets.UTF_8).ifBlank { address }
        return DualConnectRow(
            address = address,
            name = name,
            autoConnect = autoConnect,
            preferred = preferred,
            connected = connState > 0,
            playing = connState == 9,
        )
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        when {
            prop == "enabled" -> {
                driver.putProperty(group, prop, value)
                driver.sendPackage(
                    HuaweiSppPackage.changeRequest(HuaweiSppCommand.DUAL_CONNECT_ENABLED_WRITE, 1 to b(if (value == "true") 1 else 0)),
                    timeout = 1000,
                )
                readEnabled(driver)
            }
            prop == "preferred_device" -> {
                val address = value.normalizeMacHex() ?: return
                driver.putProperty(group, prop, address)
                driver.sendNowait(HuaweiSppPackage.changeRequestNoWait(HuaweiSppCommand.DUAL_CONNECT_PREFERRED_WRITE, 1 to address.hexToBytes()))
                onInit(driver)
            }
            prop.endsWith(":auto_connect") -> {
                val address = prop.substringBefore(":").normalizeMacHex() ?: return
                val cmd = if (value == "true") 4 else 5
                driver.sendNowait(HuaweiSppPackage.changeRequestNoWait(HuaweiSppCommand.DUAL_CONNECT_EXECUTE, cmd to address.hexToBytes()))
                onInit(driver)
            }
            prop.endsWith(":connected") -> {
                val address = prop.substringBefore(":").normalizeMacHex() ?: return
                val cmd = if (value == "true") 1 else 2
                driver.sendNowait(HuaweiSppPackage.changeRequestNoWait(HuaweiSppCommand.DUAL_CONNECT_EXECUTE, cmd to address.hexToBytes()))
                onInit(driver)
            }
            prop.endsWith(":name") && value.isBlank() -> {
                val address = prop.substringBefore(":").normalizeMacHex() ?: return
                driver.sendNowait(HuaweiSppPackage.changeRequestNoWait(HuaweiSppCommand.DUAL_CONNECT_EXECUTE, 3 to address.hexToBytes()))
                onInit(driver)
            }
            prop == "refresh" -> onInit(driver)
            else -> com.freebuds.controller.util.LogBuffer.w("SPP", "Unknown dual_connect.$prop=$value")
        }
    }

    private data class DualConnectRow(
        val address: String,
        val name: String,
        val autoConnect: Boolean?,
        val preferred: Boolean,
        val connected: Boolean,
        val playing: Boolean,
    )

    private fun ByteArray.toPositiveIntOrNull(): Int? {
        if (isEmpty()) return null
        return fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
    }

    private fun String.encodeForList(): String = replace("%", "%25").replace(";", "%3B").replace("|", "%7C")

    private fun String.normalizeMacHex(): String? {
        val normalized = filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }.lowercase()
        return normalized.takeIf { it.length == 12 }
    }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
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
            driver.sendPackage(HuaweiSppPackage(b(0x2b, 0x2a), parameters = mutableMapOf(1 to byteArrayOf())))
        }
    }
}

class AncHandler(
    private val wCancelLevel: Boolean = true,
    private val wCancelDynamic: Boolean = true,
    private val wVoiceBoost: Boolean = true,
) : HuaweiDeviceHandler {
    override val id = "anc_global"
    override val commandIds = listOf(b(0x2b, 0x2a), b(0x2b, 0x2c))
    override val ignoreCommandIds = listOf(b(0x2b, 0x04))
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
        driver.sendPackage(HuaweiSppPackage.readRequest(b(0x2b, 0x2a), 1, 2))?.let { onPackage(it, driver) }
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
        driver.sendNowait(HuaweiSppPackage.changeRequest(b(0x2b, 0x04), 1 to data))
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
    override val capabilities = listOf(HuaweiCapability.ACTION_LONG_TAP, HuaweiCapability.ACTION_LONG_TAP_SPLIT)
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
