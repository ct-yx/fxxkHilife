package com.freebuds.controller.bluetooth

import com.freebuds.controller.adapter.huawei.protocol.HuaweiCommandCatalog

/**
 * Defines which ANC packets are state and which are only change notifications.
 *
 * 2b2c is emitted in bursts while the earbuds are being inserted/removed.  Treating its
 * payload as a complete mode snapshot lets a transient value overwrite the stable 2b2a state.
 * The notification therefore only schedules an authoritative 2b2a refresh.
 */
internal enum class AncPacketAction {
    APPLY_STATE,
    REFRESH_STATE,
    IGNORE,
}

internal fun ancPacketAction(commandId: ByteArray): AncPacketAction = when {
    commandId.contentEquals(HuaweiCommandCatalog.anc.readCommand) -> AncPacketAction.APPLY_STATE
    HuaweiCommandCatalog.anc.notificationCommands.any { commandId.contentEquals(it) } ->
        AncPacketAction.REFRESH_STATE
    else -> AncPacketAction.IGNORE
}

internal data class AncReading(
    val mode: Int,
    val level: Int,
)

/** Decodes the p1 payload used by both the initial read and the authoritative refresh. */
internal fun decodeAncReading(data: ByteArray): AncReading? {
    if (data.isEmpty()) return null
    return if (data.size >= 2) {
        AncReading(
            mode = data[1].toInt() and 0xFF,
            level = data[0].toInt() and 0xFF,
        )
    } else {
        AncReading(mode = data[0].toInt() and 0xFF, level = 0)
    }
}
