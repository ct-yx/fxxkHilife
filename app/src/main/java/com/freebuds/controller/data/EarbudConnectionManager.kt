package com.freebuds.controller.data

import android.bluetooth.BluetoothDevice
import com.freebuds.controller.core.session.EarbudSession
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

/**
 * Typed commands accepted by every Android connection entry point.
 *
 * The repository remains the compatibility implementation for this migration step, but Service,
 * Tile, ViewModel and the regression runner no longer need to know which repository method owns
 * a particular trigger.  The transport/session implementation can move behind this boundary in
 * the next BT-3 step without changing those callers.
 */
sealed interface ConnectionCommand {
    data class Connect(
        val device: BluetoothDevice,
        val trigger: ConnectionTrigger = ConnectionTrigger.UserAction,
    ) : ConnectionCommand

    data class AutoConnectSaved(
        val address: String,
        val logMisses: Boolean = true,
        val trigger: ConnectionTrigger = ConnectionTrigger.UserAction,
    ) : ConnectionCommand

    data class AutoConnectKnownSystemConnected(
        val device: BluetoothDevice,
        val logMisses: Boolean = true,
        val trigger: ConnectionTrigger = ConnectionTrigger.ServiceCommand,
    ) : ConnectionCommand

    data class AutoConnectLastSaved(
        val trigger: ConnectionTrigger = ConnectionTrigger.ServiceCommand,
    ) : ConnectionCommand

    data class AutoConnectSystemConnectedSaved(
        val logMisses: Boolean = true,
        val trigger: ConnectionTrigger = ConnectionTrigger.PeriodicCheck,
    ) : ConnectionCommand

    data class TriggerAutoConnect(
        val reason: String,
        val knownDevice: BluetoothDevice? = null,
        val force: Boolean = false,
        val logMisses: Boolean = true,
        val trigger: ConnectionTrigger = ConnectionTrigger.ServiceCommand,
    ) : ConnectionCommand

    data object ClearManualDisconnectSuppression : ConnectionCommand
    /** Real ACL loss; this must tear down the control session, not only update the UI flag. */
    data class SystemAclDisconnected(val address: String) : ConnectionCommand
    data object Disconnect : ConnectionCommand
    data object RefreshSavedDeviceConnections : ConnectionCommand

    /** Synthetic cleanup used only by the in-app regression runner. */
    data object RegressionSimulateAclDisconnect : ConnectionCommand
}

sealed interface ConnectionCommandResult {
    data class Attempt(val attemptId: String?) : ConnectionCommandResult
    data class Accepted(
        val value: Boolean,
        val attemptId: String? = null,
    ) : ConnectionCommandResult
    data object Completed : ConnectionCommandResult
}

/**
 * Command boundary for system Bluetooth, UI, Service, Tile and regression entry points.
 *
 * This is intentionally an adapter around [DeviceRepository] for the first extraction step;
 * keeping dispatch centralized now lets later steps move state/session ownership without another
 * cross-cutting call-site rewrite.
 */
class EarbudConnectionManager internal constructor(
    private val host: ConnectionManagerHost,
) {
    /** Serializes command-boundary decisions such as busy checks and auto-connect backoff. */
    private val commandLock = Any()
    private val lifecycleLock = Any()
    private val lifecycle = ConnectionLifecycle()
    private val autoConnectPolicy = ConnectionAutoConnectPolicy()
    /** Attempt identity is owned beside the session slot, not beside UI state in Repository. */
    private val attemptCoordinator = ConnectionAttemptCoordinator()
    /**
     * The manager owns the connection state, lifecycle jobs, identity gate and single live
     * protocol session. Repository remains the compatibility implementation for transport and
     * property side effects during this extraction step.
     */
    private val sessionRegistry = EarbudSessionRegistry()

    val connectionState: StateFlow<ConnectionState> = lifecycle.connectionState

    internal val connectedAddress: String?
        get() = lifecycle.connectedAddress()

    internal val connectedAt: Long
        get() = lifecycle.connectedAt()

    internal fun setConnectionState(state: ConnectionState) = lifecycle.setConnectionState(state)

    internal fun setConnectedAddress(address: String?) = lifecycle.setConnectedAddress(address)

    internal fun setConnectedAt(timestampMs: Long) = lifecycle.setConnectedAt(timestampMs)

    internal fun isConnectionBusy(): Boolean = lifecycle.isBusy()

    internal fun isInitializationActive(): Boolean = lifecycle.isInitializationActive()

    internal fun installConnectionJob(job: Job) = lifecycle.installConnectionJob(job)

    internal fun installInitializationJob(job: Job) = lifecycle.installInitializationJob(job)

    internal fun installBackgroundInitializationJob(job: Job) =
        lifecycle.installBackgroundInitializationJob(job)

    internal fun installPollingJob(job: Job) = lifecycle.installPollingJob(job)

    internal fun installFastPollingJob(job: Job) = lifecycle.installFastPollingJob(job)

    internal fun cancelConnectionJob() = lifecycle.cancelConnectionJob()

    internal fun cancelInitializationJobs() = lifecycle.cancelInitializationJobs()

    internal fun cancelBackgroundInitializationJob() =
        lifecycle.cancelBackgroundInitializationJob()

    internal fun cancelPollingJobs() = lifecycle.cancelPollingJobs()

    internal fun cancelPollingJob() = lifecycle.cancelPollingJob()

    internal fun cancelFastPollingJob() = lifecycle.cancelFastPollingJob()

    internal fun cancelConnectionWork() = lifecycle.cancelConnectionWork()

    internal fun armReconnectGuard(untilElapsedMs: Long) =
        lifecycle.armReconnectGuard(untilElapsedMs)

    internal fun reconnectDelayMs(nowElapsedMs: Long): Long =
        lifecycle.reconnectDelayMs(nowElapsedMs)

    internal fun suppressManualDisconnect(untilWallClockMs: Long) =
        lifecycle.suppressManualDisconnect(untilWallClockMs)

    internal fun clearManualDisconnectSuppression() = lifecycle.clearManualDisconnectSuppression()

    internal fun manualDisconnectSuppressionRemaining(nowWallClockMs: Long): Long =
        lifecycle.manualDisconnectSuppressionRemaining(nowWallClockMs)

    internal fun markSystemConnected(address: String) = lifecycle.markSystemConnected(address)

    internal fun markSystemDisconnected(address: String) = lifecycle.markSystemDisconnected(address)

    internal fun isSystemConnected(address: String): Boolean = lifecycle.isSystemConnected(address)

    internal val currentSession: EarbudSession?
        get() = sessionRegistry.currentSession()

    internal fun installSession(
        attemptId: String,
        address: String,
        session: EarbudSession,
    ): Boolean = sessionRegistry.install(attemptId, address, session)

    internal fun detachSession(): EarbudSession? = sessionRegistry.detach()?.session

    internal fun detachSessionIfCurrent(session: EarbudSession): Boolean =
        sessionRegistry.detachIfCurrent(session) != null

    internal fun isCurrentSession(session: EarbudSession?): Boolean =
        sessionRegistry.isCurrent(session)

    internal val currentAttempt: ConnectionAttemptTimeline?
        get() = attemptCoordinator.current()

    internal fun reserveAttempt(
        address: String,
        trigger: ConnectionTrigger,
        busy: Boolean,
    ): ConnectionAttemptReservation? = attemptCoordinator.reserve(address, trigger, busy)

    internal fun isCurrentAttempt(attempt: ConnectionAttemptTimeline): Boolean =
        attemptCoordinator.isCurrent(attempt)

    internal fun clearAttempt() {
        attemptCoordinator.clear()
    }

    internal fun setHardwareRegressionActive(active: Boolean) {
        host.setHardwareRegressionActive(active)
    }

    /** Serializes attempt, session and repository state hand-offs during a lifecycle transition. */
    internal inline fun <T> withLifecycleLock(block: () -> T): T =
        synchronized(lifecycleLock, block)

    fun submit(command: ConnectionCommand): ConnectionCommandResult = synchronized(commandLock) {
        when (command) {
        is ConnectionCommand.Connect ->
            ConnectionCommandResult.Attempt(host.connect(command.device, command.trigger))
        is ConnectionCommand.AutoConnectSaved ->
            host.autoConnectSavedRequest(command.address, command.logMisses, command.trigger)
                .asCommandResult()
        is ConnectionCommand.AutoConnectKnownSystemConnected ->
            host.autoConnectKnownSystemConnectedRequest(
                    command.device,
                    command.logMisses,
                    command.trigger,
                )
                .asCommandResult()
        is ConnectionCommand.AutoConnectLastSaved ->
            host.autoConnectLastSavedRequest(command.trigger).asCommandResult()
        is ConnectionCommand.AutoConnectSystemConnectedSaved ->
            host.autoConnectSystemConnectedSavedRequest(command.logMisses, command.trigger)
                .asCommandResult()
        is ConnectionCommand.TriggerAutoConnect ->
            triggerAutoConnect(command).asCommandResult()
        ConnectionCommand.ClearManualDisconnectSuppression -> {
            host.clearManualDisconnectSuppression()
            ConnectionCommandResult.Completed
        }
        is ConnectionCommand.SystemAclDisconnected -> {
            host.handleSystemBluetoothDisconnected(command.address)
            ConnectionCommandResult.Completed
        }
        ConnectionCommand.Disconnect -> {
            host.disconnect()
            ConnectionCommandResult.Completed
        }
        ConnectionCommand.RefreshSavedDeviceConnections -> {
            host.refreshSavedDeviceConnections()
            ConnectionCommandResult.Completed
        }
        ConnectionCommand.RegressionSimulateAclDisconnect -> {
            host.regressionSimulateAclDisconnect()
            ConnectionCommandResult.Completed
        }
        }
    }

    private fun triggerAutoConnect(command: ConnectionCommand.TriggerAutoConnect): ConnectionRequestResult {
        val now = System.currentTimeMillis()
        if (isConnectionBusy()) {
            val requestedAddress = command.knownDevice?.address
            val activeAttemptId = host.getActiveConnectionAttemptId(requestedAddress)
            if (activeAttemptId == null) {
                if (command.logMisses) {
                    LogBuffer.d(
                        "AutoConnect",
                        "Skip ${requestedAddress ?: "unknown"}: another device control channel is active",
                    )
                }
                return ConnectionRequestResult(accepted = false)
            }
            host.refreshSavedDeviceConnections()
            return ConnectionRequestResult(true, activeAttemptId)
        }
        if (!autoConnectPolicy.canAttempt(command.force)) {
            if (command.logMisses) {
                val remaining = (autoConnectPolicy.nextAttemptAt() - now).coerceAtLeast(0L)
                LogBuffer.d(
                    "AutoConnect",
                    "Backoff active; skip ${command.reason} for ${remaining / 1000}s",
                )
            }
            return ConnectionRequestResult(accepted = false)
        }

        val started = if (command.knownDevice != null) {
            host.autoConnectKnownSystemConnectedRequest(
                command.knownDevice,
                command.logMisses,
                command.trigger,
            )
        } else {
            host.autoConnectSystemConnectedSavedRequest(command.logMisses, command.trigger)
        }
        if (started.accepted) {
            LogBuffer.i("AutoConnect", "Auto control connect triggered by ${command.reason}")
            autoConnectPolicy.recordAccepted()
        } else {
            if (command.logMisses) {
                LogBuffer.d(
                    "AutoConnect",
                    "No eligible system-connected saved device after ${command.reason}; " +
                        "next check in ${autoConnectPolicy.currentBackoffMs() / 1000}s",
                )
            }
            autoConnectPolicy.recordRejected()
        }
        host.refreshSavedDeviceConnections()
        return started
    }

    private fun ConnectionRequestResult.asCommandResult(): ConnectionCommandResult.Accepted =
        ConnectionCommandResult.Accepted(accepted, attemptId)
}
