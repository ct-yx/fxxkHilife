package com.freebuds.controller.data

/** System Bluetooth and app control-channel state are intentionally separate. */
enum class SystemLinkState {
    Disconnected,
    Connected,
}

/** Typed state contract for the next UI migration; [ConnectionState] remains the compatibility API. */
enum class ControlChannelStage {
    Idle,
    ConnectingTransport,
    TransportReady,
    Settling,
    InitializingCore,
    CoreReady,
    InitializingDeferred,
    Ready,
    Degraded,
    Disconnecting,
    Failed,
}

data class ControlChannelState(
    val systemLink: SystemLinkState = SystemLinkState.Disconnected,
    val stage: ControlChannelStage = ControlChannelStage.Idle,
    val deviceName: String? = null,
    val address: String? = null,
    val attemptId: String? = null,
    val trigger: ConnectionTrigger? = null,
    val endpoint: String? = null,
    val pendingHandlers: Set<String> = emptySet(),
    val failedHandlers: Set<String> = emptySet(),
    val reason: String? = null,
)
