package com.freebuds.controller.data

/**
 * Pure state transition boundary for the typed control-channel contract.
 *
 * Repository callbacks can arrive after a replacement attempt has already started. Every
 * attempt-bound transition therefore carries its attempt id and stale transitions are ignored
 * here instead of being reimplemented at each callback site.
 */
class ControlChannelStateReducer {
    fun beginAttempt(
        state: ControlChannelState,
        deviceName: String,
        address: String,
        attemptId: String,
        trigger: ConnectionTrigger,
        systemLink: SystemLinkState,
    ): ControlChannelState = ControlChannelState(
        systemLink = systemLink,
        stage = ControlChannelStage.ConnectingTransport,
        deviceName = deviceName,
        address = address,
        attemptId = attemptId,
        trigger = trigger,
    )

    fun stage(
        state: ControlChannelState,
        attemptId: String,
        stage: ControlChannelStage,
        pendingHandlers: Set<String> = state.pendingHandlers,
        failedHandlers: Set<String> = state.failedHandlers,
        reason: String? = null,
    ): ControlChannelState {
        if (state.attemptId != attemptId) return state
        return state.copy(
            stage = stage,
            attemptId = attemptId,
            pendingHandlers = pendingHandlers,
            failedHandlers = failedHandlers,
            reason = reason,
        )
    }

    fun updateHandlers(
        state: ControlChannelState,
        attemptId: String?,
        pendingHandlers: Set<String>,
        failedHandlers: Set<String>,
    ): ControlChannelState {
        if (attemptId != null && state.attemptId != null && state.attemptId != attemptId) return state
        return state.copy(
            pendingHandlers = pendingHandlers,
            failedHandlers = failedHandlers,
        )
    }

    fun updateSystemLink(
        state: ControlChannelState,
        address: String,
        systemLink: SystemLinkState,
    ): ControlChannelState {
        if (state.address != null && state.address != address) return state
        return state.copy(address = address, systemLink = systemLink)
    }

    fun reset(systemLink: SystemLinkState): ControlChannelState = ControlChannelState(
        systemLink = systemLink,
    )
}
