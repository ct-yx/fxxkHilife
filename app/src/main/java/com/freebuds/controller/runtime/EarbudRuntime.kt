package com.freebuds.controller.runtime

import com.freebuds.controller.core.capability.EarbudCapability
import com.freebuds.controller.core.state.EarbudState
import com.freebuds.controller.data.ConnectionCommandResult
import com.freebuds.controller.data.ControlChannelState
import com.freebuds.controller.data.DeviceControlApi
import com.freebuds.controller.data.ListeningStats
import com.freebuds.controller.data.SavedDeviceConnection
import kotlinx.coroutines.flow.StateFlow

/**
 * Commands understood by the application runtime.
 *
 * These commands describe user intent. Vendor property groups and protocol keys remain inside
 * [DeviceControlApi] and the adapter implementation.
 */
sealed interface RuntimeCommand {
    data class Connect(val address: String) : RuntimeCommand
    data object Disconnect : RuntimeCommand
    data object RefreshSavedDevices : RuntimeCommand
    data class SetAncMode(val value: String) : RuntimeCommand
    data class SetAncLevel(val value: String) : RuntimeCommand
    data class SetSoundQuality(val value: String) : RuntimeCommand
    data class SetEqualizerPreset(val value: String) : RuntimeCommand
    data class SetAutoPause(val enabled: Boolean) : RuntimeCommand
    data class SetLowLatency(val enabled: Boolean) : RuntimeCommand
    data class SetDualConnect(val enabled: Boolean) : RuntimeCommand
    data class SetPreferredDevice(val address: String) : RuntimeCommand
    data class SetDualDeviceOption(
        val address: String,
        val option: DeviceControlApi.DualDeviceOption,
        val enabled: Boolean,
    ) : RuntimeCommand
    data class SetGesture(
        val target: DeviceControlApi.GestureTarget,
        val value: String,
    ) : RuntimeCommand
}

sealed interface RuntimeCommandResult {
    data class Attempt(val attemptId: String?) : RuntimeCommandResult
    data object Completed : RuntimeCommandResult
    data class Rejected(val reason: String) : RuntimeCommandResult
}

/** Canonical state shared by the Flutter UI, foreground service and system tile. */
data class RuntimeSnapshot(
    val connection: ControlChannelState = ControlChannelState(),
    val device: EarbudState = EarbudState(),
    val capabilities: Set<EarbudCapability> = emptySet(),
    val savedDevices: List<SavedDeviceConnection> = emptyList(),
    val listening: ListeningStats = ListeningStats(),
)

/** Stable application seam for connection and device commands. */
interface EarbudRuntime {
    val snapshot: StateFlow<RuntimeSnapshot>

    fun submit(command: RuntimeCommand): RuntimeCommandResult

    fun setForeground(value: Boolean)
}

internal fun ConnectionCommandResult.toRuntimeResult(): RuntimeCommandResult = when (this) {
    is ConnectionCommandResult.Attempt -> RuntimeCommandResult.Attempt(attemptId)
    is ConnectionCommandResult.Accepted ->
        if (value) RuntimeCommandResult.Attempt(attemptId)
        else RuntimeCommandResult.Rejected("connection request was not accepted")
    ConnectionCommandResult.Completed -> RuntimeCommandResult.Completed
}
