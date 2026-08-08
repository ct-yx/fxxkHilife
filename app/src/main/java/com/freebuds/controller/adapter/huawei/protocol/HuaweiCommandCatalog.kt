package com.freebuds.controller.adapter.huawei.protocol

import com.freebuds.controller.protocol.HuaweiSppPackage

/**
 * Canonical description of one Huawei SPP command family.
 *
 * Handlers used to construct command byte arrays inline.  That made it easy for a read, write,
 * notification and response route to drift apart.  The catalog is deliberately small and
 * protocol-specific: it is the source of truth for command ids and read parameters, while a
 * feature still owns how the returned parameters become state.
 */
class HuaweiCommandSpec(
    val key: String,
    val readCommand: ByteArray,
    val readParameters: IntArray = intArrayOf(),
    val writeCommand: ByteArray? = null,
    val notificationCommands: List<ByteArray> = emptyList(),
) {
    val incomingCommandIds: List<ByteArray>
        get() = buildList {
            add(readCommand)
            notificationCommands.forEach { notification ->
                if (none { it.contentEquals(notification) }) add(notification)
            }
        }

    fun readRequest(): HuaweiSppPackage = HuaweiSppPackage.readRequest(readCommand, *readParameters)

    fun readRequestNoWait(): HuaweiSppPackage = HuaweiSppPackage(
        commandId = readCommand,
        responseId = byteArrayOf(),
        parameters = readParameters.associateWith { byteArrayOf() }.toMutableMap(),
    )

    fun writeRequest(vararg parameters: Pair<Int, ByteArray>): HuaweiSppPackage =
        HuaweiSppPackage.changeRequest(requireWriteCommand(), *parameters)

    fun writeRequestNoWait(vararg parameters: Pair<Int, ByteArray>): HuaweiSppPackage =
        HuaweiSppPackage.changeRequestNoWait(requireWriteCommand(), *parameters)

    private fun requireWriteCommand(): ByteArray =
        writeCommand ?: error("Command $key does not define a write command")
}

/**
 * Command directory for the OpenFreebuds-derived Huawei protocol.
 *
 * The remaining payload semantics stay in feature handlers for this migration step.  Keeping all
 * ids here lets the scheduler/client and future feature modules share the exact same command
 * definitions without exposing raw byte literals in every handler.
 */
object HuaweiCommandCatalog {
    val logs = HuaweiCommandSpec(
        key = "logs",
        readCommand = cmd(0x0a, 0x0d),
    )

    val deviceInfo = HuaweiCommandSpec(
        key = "device_info",
        readCommand = cmd(0x01, 0x07),
        readParameters = intArrayOf(3, 7, 9, 10, 15),
    )

    val battery = HuaweiCommandSpec(
        key = "battery",
        readCommand = cmd(0x01, 0x08),
        readParameters = intArrayOf(1, 2, 3),
        notificationCommands = listOf(cmd(0x01, 0x27)),
    )

    val inEar = HuaweiCommandSpec(
        key = "in_ear",
        readCommand = cmd(0x2b, 0x03),
    )

    val autoPause = HuaweiCommandSpec(
        key = "auto_pause",
        readCommand = cmd(0x2b, 0x11),
        readParameters = intArrayOf(1),
        writeCommand = cmd(0x2b, 0x10),
    )

    val lowLatency = HuaweiCommandSpec(
        key = "low_latency",
        readCommand = cmd(0x2b, 0x6c),
        readParameters = intArrayOf(2),
        writeCommand = cmd(0x2b, 0x6c),
    )

    val soundQuality = HuaweiCommandSpec(
        key = "sound_quality",
        readCommand = cmd(0x2b, 0xa3),
        readParameters = intArrayOf(1),
        writeCommand = cmd(0x2b, 0xa2),
    )

    val equalizerPreset = HuaweiCommandSpec(
        key = "equalizer_preset",
        readCommand = cmd(0x2b, 0x4a),
        readParameters = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
        writeCommand = cmd(0x2b, 0x49),
    )

    val doubleTap = HuaweiCommandSpec(
        key = "double_tap",
        readCommand = cmd(0x01, 0x20),
        readParameters = intArrayOf(1, 2),
        writeCommand = cmd(0x01, 0x1f),
    )

    val tripleTap = HuaweiCommandSpec(
        key = "triple_tap",
        readCommand = cmd(0x01, 0x26),
        readParameters = intArrayOf(1, 2),
        writeCommand = cmd(0x01, 0x25),
    )

    val longTap = HuaweiCommandSpec(
        key = "long_tap",
        readCommand = cmd(0x2b, 0x17),
        readParameters = intArrayOf(1, 2),
        writeCommand = cmd(0x2b, 0x16),
    )

    val swipe = HuaweiCommandSpec(
        key = "swipe",
        readCommand = cmd(0x2b, 0x1f),
        readParameters = intArrayOf(1, 2),
        writeCommand = cmd(0x2b, 0x1e),
    )

    val dualConnectEnabled = HuaweiCommandSpec(
        key = "dual_connect_enabled",
        readCommand = cmd(0x2b, 0x2f),
        readParameters = intArrayOf(1),
        writeCommand = cmd(0x2b, 0x2e),
    )

    val dualConnectEnumeration = HuaweiCommandSpec(
        key = "dual_connect_enumeration",
        readCommand = cmd(0x2b, 0x31),
        writeCommand = cmd(0x2b, 0x31),
        notificationCommands = listOf(cmd(0x2b, 0x36)),
    )

    val dualConnectPreferred = HuaweiCommandSpec(
        key = "dual_connect_preferred",
        readCommand = cmd(0x2b, 0x32),
        writeCommand = cmd(0x2b, 0x32),
    )

    val dualConnectExecute = HuaweiCommandSpec(
        key = "dual_connect_execute",
        readCommand = cmd(0x2b, 0x33),
        writeCommand = cmd(0x2b, 0x33),
    )

    val anc = HuaweiCommandSpec(
        key = "anc",
        readCommand = cmd(0x2b, 0x2a),
        readParameters = intArrayOf(1, 2),
        writeCommand = cmd(0x2b, 0x04),
        notificationCommands = listOf(cmd(0x2b, 0x2c)),
    )

    val voiceLanguage = HuaweiCommandSpec(
        key = "voice_language",
        readCommand = cmd(0x0c, 0x02),
        readParameters = intArrayOf(1, 2),
        writeCommand = cmd(0x0c, 0x01),
    )

    val ancLegacyChange = HuaweiCommandSpec(
        key = "anc_legacy_change",
        readCommand = cmd(0x2b, 0x03),
    )

    val all: List<HuaweiCommandSpec> = listOf(
        deviceInfo,
        logs,
        inEar,
        battery,
        autoPause,
        lowLatency,
        soundQuality,
        equalizerPreset,
        doubleTap,
        tripleTap,
        longTap,
        swipe,
        dualConnectEnabled,
        dualConnectEnumeration,
        dualConnectPreferred,
        dualConnectExecute,
        anc,
        voiceLanguage,
        ancLegacyChange,
    )

    private fun cmd(high: Int, low: Int): ByteArray = byteArrayOf(high.toByte(), low.toByte())
}
