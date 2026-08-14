package com.freebuds.controller.ui.state

import com.freebuds.controller.data.ConnectionTrigger

/**
 * Identifies one control-channel session.  The address prevents a replacement device from
 * inheriting the previous device's navigation decision, while attemptId prevents a reconnect of
 * the same device from inheriting a previous manual back action.
 */
data class DeviceConnectionSession(
    val address: String,
    val attemptId: String,
)

/**
 * Pure navigation intent state for the device detail route.
 *
 * Automatic navigation is a one-shot observation of a system-triggered ready event.  A user
 * opening the detail route is an explicit navigation intent and does not need a connection state
 * transition to work.
 */
data class DeviceNavigationState(
    val observedSession: DeviceConnectionSession? = null,
    val autoOpenedSession: DeviceConnectionSession? = null,
    val userLeftSession: DeviceConnectionSession? = null,
) {
    fun observeSession(session: DeviceConnectionSession?): DeviceNavigationState =
        if (observedSession == session) {
            this
        } else {
            copy(
                observedSession = session,
                autoOpenedSession = null,
                userLeftSession = null,
            )
        }

    fun markUserOpened(): DeviceNavigationState = copy(userLeftSession = null)

    fun markUserLeft(): DeviceNavigationState =
        observedSession?.let { copy(userLeftSession = it) } ?: this

    fun shouldAutoOpen(
        isReady: Boolean,
        trigger: ConnectionTrigger?,
    ): Boolean {
        val session = observedSession ?: return false
        return isReady &&
            trigger.isAutomaticNavigationTrigger() &&
            autoOpenedSession != session &&
            userLeftSession != session
    }

    fun markAutoOpened(): DeviceNavigationState =
        observedSession?.let { copy(autoOpenedSession = it) } ?: this
}

private fun ConnectionTrigger?.isAutomaticNavigationTrigger(): Boolean = when (this) {
    // These are explicit user/debug actions.  They either navigate directly or must not mutate
    // the app's route as a side effect of a test/debug command.
    null -> false
    ConnectionTrigger.UserAction,
    ConnectionTrigger.TileAction,
    ConnectionTrigger.HardwareRegression -> false

    ConnectionTrigger.ServiceCommand,
    ConnectionTrigger.AclConnected,
    ConnectionTrigger.AudioProfileConnected,
    ConnectionTrigger.PeriodicCheck,
    ConnectionTrigger.ScanCompleted -> true
}
