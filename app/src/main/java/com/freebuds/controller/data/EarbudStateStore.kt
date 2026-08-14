package com.freebuds.controller.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.freebuds.controller.core.capability.EarbudCapability
import com.freebuds.controller.core.state.EarbudState

data class EarbudSnapshot(
    val state: EarbudState = EarbudState(),
    val props: DeviceProps = DeviceProps(),
    val capabilities: Set<EarbudCapability> = emptySet(),
    /** The control-channel metadata captured with this state projection. */
    val controlChannel: ControlChannelState = ControlChannelState(),
)

/**
 * Owns the typed state exposed by the Bluetooth layer.
 *
 * The repository still performs transport/session side effects, but it no longer needs to own
 * the mutable state-flow primitives or repeat attempt-aware reducer calls.  This is the seam the
 * UI migration will consume later; [DeviceProps] remains a compatibility projection for now.
 */
class EarbudStateStore(
    private val reducer: ControlChannelStateReducer = ControlChannelStateReducer(),
) {
    /** Serializes the compatibility projections after the canonical snapshot update. */
    private val projectionLock = Any()

    private val _controlChannelState = MutableStateFlow(ControlChannelState())
    val controlChannelState: StateFlow<ControlChannelState> = _controlChannelState.asStateFlow()

    private val _props = MutableStateFlow(DeviceProps())
    val props: StateFlow<DeviceProps> = _props.asStateFlow()

    private val _earbudState = MutableStateFlow(EarbudState())
    val earbudState: StateFlow<EarbudState> = _earbudState.asStateFlow()

    private val _capabilities = MutableStateFlow<Set<EarbudCapability>>(emptySet())
    val capabilities: StateFlow<Set<EarbudCapability>> = _capabilities.asStateFlow()

    private val _snapshot = MutableStateFlow(EarbudSnapshot())
    val snapshot: StateFlow<EarbudSnapshot> = _snapshot.asStateFlow()

    fun beginAttempt(
        deviceName: String,
        address: String,
        attemptId: String,
        trigger: ConnectionTrigger,
        systemLink: SystemLinkState,
    ) {
        updateSnapshot { current ->
            val channel = reducer.beginAttempt(
                state = current.controlChannel,
                deviceName = deviceName,
                address = address,
                attemptId = attemptId,
                trigger = trigger,
                systemLink = systemLink,
            )
            // A new attempt must not expose the previous device's state while its first reads are
            // in flight. The channel and data projection are published as one contract snapshot.
            EarbudSnapshot(
                state = EarbudState(),
                props = DeviceProps(),
                capabilities = emptySet(),
                controlChannel = channel,
            )
        }
    }

    fun updateControlChannel(transform: (ControlChannelState) -> ControlChannelState) {
        // Connection callbacks, deferred initialization and property notifications can run on
        // different dispatcher threads. A read/transform/write assignment can therefore put an
        // older InitializingDeferred snapshot back after a newer Ready update. StateFlow.update
        // retries the transform against the latest state instead of losing the terminal stage.
        updateSnapshot { current ->
            current.copy(controlChannel = transform(current.controlChannel))
        }
    }

    fun updateEndpoint(address: String, endpoint: String) {
        updateControlChannel { state ->
            state.copy(address = address, endpoint = endpoint)
        }
    }

    fun updateStage(
        stage: ControlChannelStage,
        attempt: ConnectionAttemptTimeline? = null,
        addressOverride: String? = null,
        systemLink: SystemLinkState,
        pendingHandlers: Set<String>,
        failedHandlers: Set<String>,
        reason: String? = null,
    ) {
        updateSnapshot { currentSnapshot ->
            val current = currentSnapshot.controlChannel
            val address = attempt?.address ?: addressOverride ?: current.address
            val prepared = current.copy(
                systemLink = if (address != null) systemLink else current.systemLink,
                address = address,
                trigger = attempt?.trigger ?: current.trigger,
            )
            val channel = if (attempt == null) {
                prepared.copy(
                    stage = stage,
                    pendingHandlers = pendingHandlers,
                    failedHandlers = failedHandlers,
                    reason = reason,
                )
            } else {
                reducer.stage(
                    state = prepared,
                    attemptId = attempt.attemptId,
                    stage = stage,
                    pendingHandlers = pendingHandlers,
                    failedHandlers = failedHandlers,
                    reason = reason,
                )
            }
            currentSnapshot.copy(controlChannel = channel)
        }
    }

    fun updateHandlers(
        activeAttemptId: String?,
        pendingHandlers: Set<String>,
        failedHandlers: Set<String>,
    ) {
        updateSnapshot { current ->
            current.copy(
                controlChannel = reducer.updateHandlers(
                    state = current.controlChannel,
                    attemptId = activeAttemptId,
                    pendingHandlers = pendingHandlers,
                    failedHandlers = failedHandlers,
                )
            )
        }
    }

    fun updateSystemLink(address: String, systemLink: SystemLinkState) {
        updateSnapshot { current ->
            current.copy(
                controlChannel = reducer.updateSystemLink(
                    state = current.controlChannel,
                    address = address,
                    systemLink = systemLink,
                )
            )
        }
    }

    fun resetControlChannel(systemLink: SystemLinkState, reason: String? = null) {
        updateSnapshot { current ->
            current.copy(controlChannel = reducer.reset(systemLink).copy(reason = reason))
        }
    }

    fun replaceProps(props: DeviceProps) {
        updateSnapshot { current ->
            val state = EarbudStateMapper.fromDeviceProps(props)
            current.copy(
                state = state,
                props = props,
                controlChannel = current.controlChannel.withPendingHandlers(state.pendingInitHandlers),
            )
        }
    }

    fun replaceEarbudState(state: EarbudState) {
        updateSnapshot { current ->
            current.copy(
                state = state,
                props = EarbudStateMapper.toDeviceProps(state),
                controlChannel = current.controlChannel.withPendingHandlers(state.pendingInitHandlers),
            )
        }
    }

    /** Replaces the generic state, legacy projection and capability set as one contract update. */
    fun replaceContract(
        state: EarbudState,
        capabilities: Set<EarbudCapability>,
        pendingHandlers: Set<String>? = null,
        failedHandlers: Set<String>? = null,
        activeAttemptId: String? = null,
        systemLink: SystemLinkState? = null,
    ) {
        updateSnapshot { current ->
            var channel = current.controlChannel
            if (systemLink != null && channel.address != null) {
                channel = channel.copy(systemLink = systemLink)
            }
            channel = reducer.updateHandlers(
                state = channel,
                attemptId = activeAttemptId ?: channel.attemptId,
                pendingHandlers = pendingHandlers ?: state.pendingInitHandlers,
                failedHandlers = failedHandlers ?: channel.failedHandlers,
            )
            EarbudSnapshot(
                state = state,
                props = EarbudStateMapper.toDeviceProps(state),
                capabilities = capabilities.toSet(),
                controlChannel = channel,
            )
        }
    }

    fun resetContract(systemLink: SystemLinkState? = null, reason: String? = null) {
        updateSnapshot { current ->
            val channel = reducer.reset(systemLink ?: current.controlChannel.systemLink)
                .copy(reason = reason)
            EarbudSnapshot(controlChannel = channel)
        }
    }

    fun replaceCapabilities(capabilities: Set<EarbudCapability>) {
        updateSnapshot { current -> current.copy(capabilities = capabilities.toSet()) }
    }

    fun updateProps(transform: (DeviceProps) -> DeviceProps) {
        updateSnapshot { current ->
            val updated = transform(current.props)
            val state = EarbudStateMapper.fromDeviceProps(updated)
            current.copy(
                state = state,
                props = updated,
                controlChannel = current.controlChannel.withPendingHandlers(state.pendingInitHandlers),
            )
        }
    }

    private fun ControlChannelState.withPendingHandlers(
        pendingHandlers: Set<String>,
    ): ControlChannelState = reducer.updateHandlers(
        state = this,
        attemptId = attemptId,
        pendingHandlers = pendingHandlers,
        failedHandlers = failedHandlers,
    )

    private fun updateSnapshot(transform: (EarbudSnapshot) -> EarbudSnapshot) {
        _snapshot.update(transform)
        // These flows are compatibility projections. New code should consume snapshot, which is
        // the only surface that carries all four contract pieces in one value. A concurrent update
        // may commit another snapshot while this callback is waiting; read the latest value while
        // holding the projection lock so an older callback cannot restore stale projections.
        synchronized(projectionLock) {
            val current = _snapshot.value
            _controlChannelState.value = current.controlChannel
            _earbudState.value = current.state
            _props.value = current.props
            _capabilities.value = current.capabilities
        }
    }
}
