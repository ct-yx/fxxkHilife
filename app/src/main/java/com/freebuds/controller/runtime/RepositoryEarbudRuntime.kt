package com.freebuds.controller.runtime

import android.bluetooth.BluetoothAdapter
import com.freebuds.controller.data.ConnectionCommand
import com.freebuds.controller.data.DeviceControlApi
import com.freebuds.controller.data.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Production adapter for the runtime seam.
 *
 * DeviceRepository remains the compatibility implementation while command dispatch, state
 * projection and the eventual repository extraction are kept behind one application interface.
 */
class RepositoryEarbudRuntime(
    private val repository: DeviceRepository,
) : EarbudRuntime {
    private val controls = DeviceControlApi(repository)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _snapshot = MutableStateFlow(buildSnapshot())

    override val snapshot: StateFlow<RuntimeSnapshot> = _snapshot.asStateFlow()

    init {
        observe(repository.controlChannelState)
        observe(repository.earbudState)
        observe(repository.capabilities)
        observe(repository.savedDeviceConnections)
        observe(repository.listeningStats)
    }

    override fun submit(command: RuntimeCommand): RuntimeCommandResult = when (command) {
        is RuntimeCommand.Connect -> connect(command.address)
        RuntimeCommand.Disconnect ->
            repository.connectionManager.submit(ConnectionCommand.Disconnect).toRuntimeResult()
        RuntimeCommand.RefreshSavedDevices ->
            repository.connectionManager
                .submit(ConnectionCommand.RefreshSavedDeviceConnections)
                .toRuntimeResult()
        is RuntimeCommand.SetAncMode -> dispatch { controls.setAncMode(command.value) }
        is RuntimeCommand.SetAncLevel -> dispatch { controls.setAncLevel(command.value) }
        is RuntimeCommand.SetSoundQuality -> dispatch { controls.setSoundQuality(command.value) }
        is RuntimeCommand.SetEqualizerPreset ->
            dispatch { controls.setEqualizerPreset(command.value) }
        is RuntimeCommand.SetAutoPause -> dispatch { controls.setAutoPause(command.enabled) }
        is RuntimeCommand.SetLowLatency -> dispatch { controls.setLowLatency(command.enabled) }
        is RuntimeCommand.SetDualConnect -> dispatch { controls.setDualConnect(command.enabled) }
        is RuntimeCommand.SetPreferredDevice ->
            dispatch { controls.setPreferredDevice(command.address) }
        is RuntimeCommand.SetDualDeviceOption -> dispatch {
            controls.setDualDeviceOption(command.address, command.option, command.enabled)
        }
        is RuntimeCommand.SetGesture -> dispatch {
            controls.setGesture(command.target, command.value)
        }
    }

    override fun setForeground(value: Boolean) {
        repository.setAppInForeground(value)
    }

    private fun connect(address: String): RuntimeCommandResult {
        val device = runCatching {
            BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)
        }.getOrNull() ?: return RuntimeCommandResult.Rejected("Bluetooth device not found")
        return repository.connectionManager
            .submit(ConnectionCommand.Connect(device))
            .toRuntimeResult()
    }

    private fun dispatch(block: suspend () -> Unit): RuntimeCommandResult {
        scope.launch { block() }
        return RuntimeCommandResult.Completed
    }

    private fun observe(flow: StateFlow<*>) {
        scope.launch { flow.collect { publishSnapshot() } }
    }

    private fun publishSnapshot() {
        _snapshot.value = buildSnapshot()
    }

    private fun buildSnapshot() = RuntimeSnapshot(
        connection = repository.controlChannelState.value,
        device = repository.earbudState.value,
        capabilities = repository.capabilities.value,
        savedDevices = repository.savedDeviceConnections.value,
        listening = repository.listeningStats.value,
    )
}
