package com.freebuds.controller.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    private val _controlChannelState = MutableStateFlow(ControlChannelState())
    val controlChannelState: StateFlow<ControlChannelState> = _controlChannelState.asStateFlow()

    private val _props = MutableStateFlow(DeviceProps())
    val props: StateFlow<DeviceProps> = _props.asStateFlow()

    fun beginAttempt(
        deviceName: String,
        address: String,
        attemptId: String,
        trigger: ConnectionTrigger,
        systemLink: SystemLinkState,
    ) {
        _controlChannelState.update { state ->
            reducer.beginAttempt(
                state = state,
                deviceName = deviceName,
                address = address,
                attemptId = attemptId,
                trigger = trigger,
                systemLink = systemLink,
            )
        }
    }

    fun updateControlChannel(transform: (ControlChannelState) -> ControlChannelState) {
        // Connection callbacks, deferred initialization and property notifications can run on
        // different dispatcher threads. A read/transform/write assignment can therefore put an
        // older InitializingDeferred snapshot back after a newer Ready update. StateFlow.update
        // retries the transform against the latest state instead of losing the terminal stage.
        _controlChannelState.update(transform)
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
        _controlChannelState.update { current ->
            val address = attempt?.address ?: addressOverride ?: current.address
            val prepared = current.copy(
                systemLink = if (address != null) systemLink else current.systemLink,
                address = address,
                trigger = attempt?.trigger ?: current.trigger,
            )
            if (attempt == null) {
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
        }
    }

    fun updateHandlers(
        activeAttemptId: String?,
        pendingHandlers: Set<String>,
        failedHandlers: Set<String>,
    ) {
        _controlChannelState.update { state ->
            reducer.updateHandlers(
                state = state,
                attemptId = activeAttemptId,
                pendingHandlers = pendingHandlers,
                failedHandlers = failedHandlers,
            )
        }
    }

    fun updateSystemLink(address: String, systemLink: SystemLinkState) {
        _controlChannelState.update { state ->
            reducer.updateSystemLink(
                state = state,
                address = address,
                systemLink = systemLink,
            )
        }
    }

    fun resetControlChannel(systemLink: SystemLinkState, reason: String? = null) {
        _controlChannelState.update { reducer.reset(systemLink).copy(reason = reason) }
    }

    fun replaceProps(props: DeviceProps) {
        _props.value = props
    }

    fun updateProps(transform: (DeviceProps) -> DeviceProps) {
        _props.update(transform)
    }
}
