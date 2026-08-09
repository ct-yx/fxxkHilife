package com.freebuds.controller.data

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/** User-facing compatibility state for the active control-channel lifecycle. */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data class Connecting(val deviceName: String, val attemptId: String = "") : ConnectionState()
    data class Connected(val deviceName: String, val attemptId: String = "") : ConnectionState()
    data class Failed(val reason: String, val attemptId: String? = null) : ConnectionState()
}

/**
 * Owns the mutable runtime slot for one control-channel connection.
 *
 * [DeviceRepository] still performs the legacy transport and property side effects during this
 * migration step, but it no longer owns the connection state, active address, lifecycle jobs or
 * system-link bookkeeping. Keeping those values together prevents a disconnect callback from
 * cancelling or overwriting a replacement attempt through a stale repository field.
 */
class ConnectionLifecycle {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val lock = Any()
    private var connectionJob: Job? = null
    private var initializationJob: Job? = null
    private var backgroundInitializationJob: Job? = null
    private var pollingJob: Job? = null
    private var fastPollingJob: Job? = null

    @Volatile
    private var activeAddress: String? = null
    @Volatile
    private var connectedAtMs: Long = 0L
    @Volatile
    private var reconnectNotBeforeMs: Long = 0L
    @Volatile
    private var manualDisconnectSuppressUntilMs: Long = 0L

    private val systemConnectedAddresses = ConcurrentHashMap.newKeySet<String>()

    fun setConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    fun connectedAddress(): String? = activeAddress

    fun setConnectedAddress(address: String?) {
        activeAddress = address
    }

    fun connectedAt(): Long = connectedAtMs

    fun setConnectedAt(timestampMs: Long) {
        connectedAtMs = timestampMs
    }

    fun isBusy(): Boolean = synchronized(lock) {
        connectionJob?.isActive == true ||
            _connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected
    }

    fun isInitializationActive(): Boolean = synchronized(lock) {
        initializationJob?.isActive == true || backgroundInitializationJob?.isActive == true
    }

    fun installConnectionJob(job: Job) {
        synchronized(lock) { connectionJob = job }
    }

    fun installInitializationJob(job: Job) {
        synchronized(lock) { initializationJob = job }
    }

    fun installBackgroundInitializationJob(job: Job) {
        synchronized(lock) { backgroundInitializationJob = job }
    }

    fun installPollingJob(job: Job) {
        synchronized(lock) { pollingJob = job }
    }

    fun installFastPollingJob(job: Job) {
        synchronized(lock) { fastPollingJob = job }
    }

    fun cancelConnectionJob() {
        val job = synchronized(lock) {
            connectionJob.also { connectionJob = null }
        }
        job?.cancel()
    }

    fun cancelInitializationJobs() {
        val jobs = synchronized(lock) {
            listOfNotNull(
                initializationJob,
                backgroundInitializationJob,
            ).also {
                initializationJob = null
                backgroundInitializationJob = null
            }
        }
        jobs.forEach(Job::cancel)
    }

    fun cancelBackgroundInitializationJob() {
        val job = synchronized(lock) {
            backgroundInitializationJob.also { backgroundInitializationJob = null }
        }
        job?.cancel()
    }

    fun cancelPollingJob() {
        val job = synchronized(lock) {
            pollingJob.also { pollingJob = null }
        }
        job?.cancel()
    }

    fun cancelFastPollingJob() {
        val job = synchronized(lock) {
            fastPollingJob.also { fastPollingJob = null }
        }
        job?.cancel()
    }

    fun cancelPollingJobs() {
        val jobs = synchronized(lock) {
            listOfNotNull(pollingJob, fastPollingJob).also {
                pollingJob = null
                fastPollingJob = null
            }
        }
        jobs.forEach(Job::cancel)
    }

    fun cancelConnectionWork() {
        cancelConnectionJob()
        cancelInitializationJobs()
        cancelPollingJobs()
    }

    fun armReconnectGuard(untilElapsedMs: Long) {
        reconnectNotBeforeMs = untilElapsedMs
    }

    fun reconnectDelayMs(nowElapsedMs: Long): Long =
        (reconnectNotBeforeMs - nowElapsedMs).coerceAtLeast(0L)

    fun suppressManualDisconnect(untilWallClockMs: Long) {
        manualDisconnectSuppressUntilMs = untilWallClockMs
    }

    fun clearManualDisconnectSuppression() {
        manualDisconnectSuppressUntilMs = 0L
    }

    fun manualDisconnectSuppressionRemaining(nowWallClockMs: Long): Long =
        (manualDisconnectSuppressUntilMs - nowWallClockMs).coerceAtLeast(0L)

    fun markSystemConnected(address: String) {
        systemConnectedAddresses.add(address)
    }

    fun markSystemDisconnected(address: String) {
        systemConnectedAddresses.remove(address)
    }

    fun isSystemConnected(address: String): Boolean = address in systemConnectedAddresses
}
