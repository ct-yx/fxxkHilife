package com.freebuds.controller.data

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.core.content.FileProvider
import com.freebuds.controller.BuildConfig
import com.freebuds.controller.bluetooth.BluetoothScanner
import com.freebuds.controller.i18n.I18n
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/** The six connection situations collected for the one-click hardware regression run. */
enum class RegressionScenario(
    val id: String,
    val title: String,
) {
    A("A", "system-connected / discovery-off / auto-low-latency-off"),
    B("B", "system-connected / discovery-off / auto-low-latency-on"),
    C("C", "ACL-connected entry"),
    D("D", "discovery-completed entry"),
    E("E", "first-attempt failure and retry"),
    F("F", "manual disconnect suppression and reconnect"),
}

enum class RegressionResult {
    PASS,
    FAIL,
    SKIPPED,
}

data class RegressionAttempt(
    val scenario: RegressionScenario,
    val iteration: Int,
    val result: RegressionResult,
    val elapsedMs: Long,
    val detail: String,
    val timing: ConnectionTimingSnapshot? = null,
)

data class RegressionFeatureCheck(
    val name: String,
    val iteration: Int,
    val result: RegressionResult,
    val elapsedMs: Long,
    val detail: String,
)

data class BluetoothRegressionState(
    val running: Boolean = false,
    val scenario: RegressionScenario? = null,
    val iteration: Int = 0,
    val totalIterations: Int = 10,
    val totalOperations: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val reportReady: Boolean = false,
    val message: String = "",
)

/**
 * Runs the real-device connection baseline from inside the app.
 *
 * This is intentionally above [DeviceRepository], so every attempt uses the same production
 * connection path as the app, Service, Tile and discovery callbacks. It does not manufacture
 * protocol responses: ANC and low-latency checks pass only after a device read-back is observed.
 */
class BluetoothRegressionRunner(
    private val context: Context,
    private val repository: DeviceRepository,
) {
    private val appContext = context.applicationContext
    private val connectionManager = repository.connectionManager
    private val _state = MutableStateFlow(BluetoothRegressionState())
    private var runJob: Job? = null
    private var lastReport: String? = null

    val state: StateFlow<BluetoothRegressionState> = _state.asStateFlow()

    fun start(scope: CoroutineScope, iterations: Int = DEFAULT_ITERATIONS): Boolean {
        if (runJob?.isActive == true) return false
        val count = iterations.coerceIn(1, MAX_ITERATIONS)
        lastReport = null
        _state.value = BluetoothRegressionState(
            running = true,
            totalIterations = count,
            totalOperations = count * (RegressionScenario.entries.size + FEATURE_CHECK_NAMES.size),
            message = I18n.t("terminal.regression.starting"),
        )
        runJob = scope.launch(Dispatchers.IO) { run(count) }
        return true
    }

    fun cancel() {
        runJob?.cancel()
        runJob = null
        _state.value = _state.value.copy(
            running = false,
            message = I18n.t("terminal.regression.cancelled"),
        )
    }

    fun isRunning(): Boolean = runJob?.isActive == true

    fun shareLastReport(chooserContext: Context) {
        val report = lastReport ?: run {
            LogBuffer.w("HwTest", "No completed hardware regression report to share")
            return
        }
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val dir = File(chooserContext.cacheDir, "logs")
                dir.mkdirs()
                val file = File(dir, "fxxkHilife_hardware_regression_${System.currentTimeMillis()}.txt")
                val boundedReport = limitReportBytes(report, REGRESSION_REPORT_MAX_BYTES)
                val reportBytes = boundedReport.toByteArray(Charsets.UTF_8)
                check(reportBytes.size <= REGRESSION_REPORT_MAX_BYTES) {
                    "regression report exceeds byte budget: ${reportBytes.size}"
                }
                file.outputStream().use { it.write(reportBytes) }
                check(file.length() <= REGRESSION_REPORT_MAX_BYTES) {
                    "written regression report exceeds byte budget: ${file.length()}"
                }
                val uri = FileProvider.getUriForFile(
                    chooserContext,
                    "${chooserContext.packageName}.fileprovider",
                    file,
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContextMain(chooserContext) {
                    chooserContext.startActivity(
                        android.content.Intent.createChooser(
                            intent,
                            I18n.t("terminal.regression.share_title"),
                        )
                    )
                }
            } catch (e: Exception) {
                LogBuffer.e("HwTest", "Failed to share regression report: ${e.message}")
            }
        }
    }

    private suspend fun run(iterations: Int) {
        val startedAt = System.currentTimeMillis()
        val attempts = mutableListOf<RegressionAttempt>()
        val featureChecks = mutableListOf<RegressionFeatureCheck>()
        val captureToken = LogBuffer.beginBoundedCapture(REGRESSION_CAPTURE_MAX_BYTES)
        // A regression report must describe this run only; do not mix it with an older terminal
        // session. The byte budget, rather than a line count, is the active test-mode limit.
        LogBuffer.clear()
        try {
            val originalAutoLowLatency = appContext
                .getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                .getBoolean(AUTO_LOW_LATENCY_KEY, true)
            val device = repository.getRegressionDevice()

            LogBuffer.putMetadata("hardwareRegressionIterations", iterations.toString())
            LogBuffer.putMetadata("hardwareRegressionStartedAt", startedAt.toString())
            LogBuffer.putMetadata("hardwareRegressionLogCaptureBytes", REGRESSION_CAPTURE_MAX_BYTES.toString())
            LogBuffer.putMetadata("hardwareRegressionReportMaxBytes", REGRESSION_REPORT_MAX_BYTES.toString())
            LogBuffer.i("HwTest", "START iterations=$iterations logLines=unlimited captureBytes=$REGRESSION_CAPTURE_MAX_BYTES")

            if (device == null) {
                val detail = "No saved or currently connected Bluetooth device"
                LogBuffer.e("HwTest", "SKIP $detail")
                val report = buildReport(startedAt, null, attempts, featureChecks, detail)
                finish(report, failed = 1, message = I18n.t("terminal.regression.no_device"))
                return
            }

            LogBuffer.putMetadata("hardwareRegressionDevice", device.name ?: device.address)
            LogBuffer.putMetadata("hardwareRegressionAddress", device.address)
            try {
                connectionManager.setHardwareRegressionActive(true)
                for (scenario in RegressionScenario.entries) {
                    _state.value = _state.value.copy(scenario = scenario, iteration = 0, message = scenario.title)
                    for (iteration in 1..iterations) {
                        ensureDisconnected()
                        _state.value = _state.value.copy(scenario = scenario, iteration = iteration)
                        val attempt = when (scenario) {
                            RegressionScenario.A -> runConnectionAttempt(
                                device,
                                scenario,
                                iteration,
                                ConnectionTrigger.HardwareRegression,
                                autoLowLatency = false,
                                requestConnection = {
                                    submitKnownAutoConnect(device, ConnectionTrigger.HardwareRegression)
                                },
                            )
                            RegressionScenario.B -> runConnectionAttempt(
                                device,
                                scenario,
                                iteration,
                                ConnectionTrigger.HardwareRegression,
                                autoLowLatency = true,
                                requestConnection = {
                                    submitKnownAutoConnect(device, ConnectionTrigger.HardwareRegression)
                                },
                            )
                            RegressionScenario.C -> runConnectionAttempt(
                                device,
                                scenario,
                                iteration,
                                ConnectionTrigger.AclConnected,
                                autoLowLatency = originalAutoLowLatency,
                                requestConnection = {
                                    submitKnownAutoConnect(device, ConnectionTrigger.AclConnected)
                                },
                            )
                            RegressionScenario.D -> runDiscoveryAttempt(device, iteration)
                            RegressionScenario.E -> runRetryAttempt(device, iteration, originalAutoLowLatency)
                            RegressionScenario.F -> runManualDisconnectAttempt(device, iteration, originalAutoLowLatency)
                        }
                        attempts += attempt
                        val completed = attempts.size
                        _state.value = _state.value.copy(
                            completed = completed,
                            failed = attempts.count { it.result == RegressionResult.FAIL },
                            message = "${scenario.id} $iteration/$iterations: ${attempt.result}",
                        )
                    }
                }

                // The feature items are part of the fixed regression matrix as well.  Each one
                // runs the requested number of rounds instead of being a single smoke check
                // after the A-F connection samples.
                val featureRunners: List<suspend (BluetoothDevice, Int) -> RegressionFeatureCheck> = listOf(
                    ::runInitializationProgressCheck,
                    ::runAncCheck,
                    ::runLowLatencyCheck,
                    ::runTriggerDeduplicationCheck,
                )
                featureRunners.forEach { runner ->
                    for (iteration in 1..iterations) {
                        ensureDisconnected()
                        val check = runFeatureCheckSafely(runner, device, iteration)
                        featureChecks += check
                        recordFeatureProgress(check)
                    }
                }
                ensureDisconnected()
            } catch (e: CancellationException) {
                LogBuffer.w("HwTest", "CANCELLED")
                throw e
            } catch (e: Exception) {
                LogBuffer.e("HwTest", "Runner failed: ${e.javaClass.simpleName}: ${e.message}")
                featureChecks += RegressionFeatureCheck(
                    name = "runner",
                    iteration = featureChecks.size + 1,
                    result = RegressionResult.FAIL,
                    elapsedMs = 0L,
                    detail = "${e.javaClass.simpleName}: ${e.message}",
                )
            } finally {
                appContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(AUTO_LOW_LATENCY_KEY, originalAutoLowLatency)
                    .apply()
                try {
                    ensureDisconnected()
                } catch (e: Exception) {
                    LogBuffer.w("HwTest", "Final disconnect cleanup failed: ${e.message}")
                }
                connectionManager.setHardwareRegressionActive(false)
            }

            val report = buildReport(startedAt, device, attempts, featureChecks, null)
            val failures = attempts.count { it.result == RegressionResult.FAIL } +
                featureChecks.count { it.result == RegressionResult.FAIL }
            finish(
                report = report,
                failed = failures,
                message = I18n.t("terminal.regression.finished", attempts.size, featureChecks.size, failures),
            )
        } finally {
            // Restore the normal line/byte policy on success, cancellation, no-device and error
            // paths. The current run has already built its report before this restoration.
            LogBuffer.endBoundedCapture(captureToken)
        }
    }

    private suspend fun runFeatureCheckSafely(
        runner: suspend (BluetoothDevice, Int) -> RegressionFeatureCheck,
        device: BluetoothDevice,
        iteration: Int,
    ): RegressionFeatureCheck = try {
        runner(device, iteration)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LogBuffer.e(
            "HwTest",
            "FEATURE check failed iteration=$iteration " +
                "${e.javaClass.simpleName}:${e.message}",
        )
        RegressionFeatureCheck(
            name = "feature-runner",
            iteration = iteration,
            result = RegressionResult.FAIL,
            elapsedMs = 0L,
            detail = "${e.javaClass.simpleName}: ${e.message}",
        )
    }

    private fun recordFeatureProgress(check: RegressionFeatureCheck) {
        val current = _state.value
        _state.value = current.copy(
            completed = current.completed + 1,
            failed = current.failed + if (check.result == RegressionResult.FAIL) 1 else 0,
            message = "${check.name} ${check.iteration}/${current.totalIterations}: ${check.result}",
        )
    }

    private suspend fun runConnectionAttempt(
        device: BluetoothDevice,
        scenario: RegressionScenario,
        iteration: Int,
        trigger: ConnectionTrigger,
        autoLowLatency: Boolean,
        requestConnection: () -> ConnectionRequestResult = {
            val attemptId = submitConnect(device, trigger)
            ConnectionRequestResult(attemptId != null, attemptId)
        },
    ): RegressionAttempt {
        val started = android.os.SystemClock.elapsedRealtime()
        setAutoLowLatencyPreference(autoLowLatency)
        LogBuffer.i(
            "HwTest",
            "CASE=${scenario.id} iteration=$iteration trigger=${trigger.name} " +
                "autoLowLatency=$autoLowLatency endpoint=${repository.getRegressionEndpoint()}",
        )
        val request = requestConnection()
        if (!request.accepted || request.attemptId == null) {
            return attempt(
                scenario,
                iteration,
                RegressionResult.FAIL,
                started,
                "connection trigger did not create an attempt accepted=${request.accepted} " +
                    "attemptId=${request.attemptId}",
            )
        }
        // Use the id returned by the command boundary. Reading the repository after the request
        // is racy with the periodic Service check and previously made a 1-second connection look
        // like a 30-second timeout or attributed it to the wrong trigger.
        val expectedAttemptId = request.attemptId
        val connected = waitForConnected(CONNECTION_TIMEOUT_MS, expectedAttemptId)
        if (!connected) {
            return attempt(
                scenario,
                iteration,
                RegressionResult.FAIL,
                started,
                "control channel did not connect expectedAttempt=$expectedAttemptId state=${repository.connectionState.value}",
            )
        }

        // The original bug appears after the first ten seconds. Keep the complete window in
        // every baseline sample instead of treating TransportReady as a successful init.
        delay(INITIALIZATION_OBSERVATION_MS)
        val props = repository.props.value
        val ready = repository.isCoreStateReady()
        val endpoint = repository.getRegressionEndpoint()
        val actualAttemptId = (repository.connectionState.value as? ConnectionState.Connected)?.attemptId
        val timing = repository.getRegressionTiming(expectedAttemptId)
        val detail = "ready=$ready lowLatency=${props.lowLatency} anc=${props.ancMode} " +
            "pending=${props.pendingInitHandlers} endpoint=$endpoint " +
            "expectedAttempt=$expectedAttemptId actualAttempt=$actualAttemptId " +
            timingDetail(timing)
        LogBuffer.i("HwTest", "CASE=${scenario.id} iteration=$iteration $detail")
        return attempt(
            scenario,
            iteration,
            if (ready) RegressionResult.PASS else RegressionResult.FAIL,
            started,
            detail,
            timing,
        )
    }

    private suspend fun runDiscoveryAttempt(
        savedDevice: BluetoothDevice,
        iteration: Int,
    ): RegressionAttempt {
        val started = android.os.SystemClock.elapsedRealtime()
        LogBuffer.i("HwTest", "CASE=D iteration=$iteration starting discovery")
        val discovered = discover(savedDevice.address, savedDevice)
        if (discovered == null) {
            return attempt(
                RegressionScenario.D,
                iteration,
                RegressionResult.FAIL,
                started,
                "discovery did not return saved device",
            )
        }
        val result = runConnectionAttempt(
            discovered.device,
            RegressionScenario.D,
            iteration,
            ConnectionTrigger.ScanCompleted,
            autoLowLatency = currentAutoLowLatency(),
        )
        return result.copy(detail = "discovery=${discovered.source}; ${result.detail}")
    }

    private suspend fun runRetryAttempt(
        device: BluetoothDevice,
        iteration: Int,
        autoLowLatency: Boolean,
    ): RegressionAttempt {
        val started = android.os.SystemClock.elapsedRealtime()
        setAutoLowLatencyPreference(autoLowLatency)
        LogBuffer.i("HwTest", "CASE=E iteration=$iteration natural-failure-retry")
        val firstAttemptId = submitConnect(device, ConnectionTrigger.HardwareRegression)
        val firstOutcome = waitForConnectedOrFailed(CONNECTION_TIMEOUT_MS, firstAttemptId)
        if (firstOutcome is ConnectionState.Failed) {
            LogBuffer.w("HwTest", "CASE=E iteration=$iteration first attempt failed; retrying")
            ensureDisconnected()
            clearManualDisconnectSuppression()
            val retryAttemptId = submitConnect(device, ConnectionTrigger.HardwareRegression)
            if (!waitForConnected(CONNECTION_TIMEOUT_MS, retryAttemptId)) {
                return attempt(RegressionScenario.E, iteration, RegressionResult.FAIL, started, "first failure + retry failed")
            }
            delay(INITIALIZATION_OBSERVATION_MS)
            val props = repository.props.value
            val timing = repository.getRegressionTiming(retryAttemptId)
            val detail = "firstAttempt=${firstOutcome::class.simpleName} ready=${repository.isCoreStateReady()} " +
                "pending=${props.pendingInitHandlers} endpoint=${repository.getRegressionEndpoint()} " +
                timingDetail(timing)
            return attempt(
                RegressionScenario.E,
                iteration,
                if (repository.isCoreStateReady()) RegressionResult.PASS else RegressionResult.FAIL,
                started,
                detail,
                timing,
            )
        } else if (firstOutcome !is ConnectionState.Connected) {
            return attempt(RegressionScenario.E, iteration, RegressionResult.FAIL, started, "first attempt timed out")
        } else {
            LogBuffer.i("HwTest", "CASE=E iteration=$iteration first attempt passed; retry gate not needed")
        }
        delay(INITIALIZATION_OBSERVATION_MS)
        val props = repository.props.value
        val timing = repository.getRegressionTiming(firstAttemptId)
        val detail = "firstAttempt=${firstOutcome::class.simpleName} ready=${repository.isCoreStateReady()} " +
            "pending=${props.pendingInitHandlers} endpoint=${repository.getRegressionEndpoint()} " +
            timingDetail(timing)
        return attempt(
            RegressionScenario.E,
            iteration,
            if (repository.isCoreStateReady()) RegressionResult.PASS else RegressionResult.FAIL,
            started,
            detail,
            timing,
        )
    }

    private suspend fun runManualDisconnectAttempt(
        device: BluetoothDevice,
        iteration: Int,
        autoLowLatency: Boolean,
    ): RegressionAttempt {
        val started = android.os.SystemClock.elapsedRealtime()
        setAutoLowLatencyPreference(autoLowLatency)
        val initialAttemptId = submitConnect(device, ConnectionTrigger.HardwareRegression)
        if (!waitForConnected(CONNECTION_TIMEOUT_MS, initialAttemptId)) {
            return attempt(RegressionScenario.F, iteration, RegressionResult.FAIL, started, "initial connect failed")
        }
        if (!waitForReady(CONNECTION_READY_TIMEOUT_MS, initialAttemptId)) {
            return attempt(
                RegressionScenario.F,
                iteration,
                RegressionResult.FAIL,
                started,
                "initial control channel did not reach core-ready attempt=$initialAttemptId",
            )
        }
        disconnect()
        val disconnected = waitForDisconnected(DISCONNECT_TIMEOUT_MS)
        val suppressed = !submitAutoConnectSaved(
            device.address,
            logMisses = true,
            trigger = ConnectionTrigger.PeriodicCheck,
        ).accepted
        clearManualDisconnectSuppression()
        val reconnectAttemptId = submitConnect(device, ConnectionTrigger.HardwareRegression)
        val reconnected = waitForConnected(CONNECTION_TIMEOUT_MS, reconnectAttemptId)
        val reconnectedReady = reconnected && waitForReady(CONNECTION_READY_TIMEOUT_MS, reconnectAttemptId)
        connectionManager.submit(ConnectionCommand.RegressionSimulateAclDisconnect)
        val aclDisconnected = waitForDisconnected(DISCONNECT_TIMEOUT_MS)
        clearManualDisconnectSuppression()
        val aclReconnectRequest = submitAutoConnectKnown(device, ConnectionTrigger.AclConnected)
        val aclReconnectAttemptId = aclReconnectRequest.attemptId
        val aclReconnected = waitForConnected(CONNECTION_TIMEOUT_MS, aclReconnectAttemptId)
        val aclReconnectedReady = aclReconnected && waitForReady(CONNECTION_READY_TIMEOUT_MS, aclReconnectAttemptId)
        val timing = repository.getRegressionTiming(aclReconnectAttemptId)
        val detail = "disconnected=$disconnected suppression=$suppressed reconnected=$reconnected " +
            "reconnectedReady=$reconnectedReady " +
            "aclDisconnected=$aclDisconnected aclRequest=${aclReconnectRequest.accepted} " +
            "aclAttempt=$aclReconnectAttemptId aclReconnected=$aclReconnected " +
            "aclReconnectedReady=$aclReconnectedReady " +
            "endpoint=${repository.getRegressionEndpoint()} ${timingDetail(timing)}"
        return attempt(
            RegressionScenario.F,
            iteration,
            if (disconnected && suppressed && reconnectedReady && aclDisconnected &&
                aclReconnectRequest.accepted && aclReconnectedReady
            ) {
                RegressionResult.PASS
            } else {
                RegressionResult.FAIL
            },
            started,
            detail,
            timing,
        )
    }

    private suspend fun runInitializationProgressCheck(
        device: BluetoothDevice,
        iteration: Int,
    ): RegressionFeatureCheck {
        val name = "application entry / 10s initialization progression"
        val started = android.os.SystemClock.elapsedRealtime()
        clearManualDisconnectSuppression()
        setAutoLowLatencyPreference(currentAutoLowLatency())
        val expectedAttemptId = submitConnect(device, ConnectionTrigger.UserAction)
        if (!waitForConnected(CONNECTION_TIMEOUT_MS, expectedAttemptId)) {
            return featureCheck(
                name,
                iteration,
                RegressionResult.FAIL,
                started,
                "application entry did not connect expectedAttempt=$expectedAttemptId",
            )
        }

        // Keep the complete ten-second window so the report shows whether initialization kept
        // moving after the first transport/core result.
        val before = repository.controlChannelState.value
        val beforePending = before.pendingHandlers
        delay(INITIALIZATION_OBSERVATION_MS)
        val after = repository.controlChannelState.value
        val props = repository.props.value
        val progressed = after.stage in setOf(
            ControlChannelStage.CoreReady,
            ControlChannelStage.InitializingDeferred,
            ControlChannelStage.Ready,
            ControlChannelStage.Degraded,
        )
        val pendingChanged = beforePending != after.pendingHandlers
        val coreReady = repository.isCoreStateReady()
        val detail = "expectedAttempt=$expectedAttemptId actualAttempt=${after.attemptId} " +
            "beforeStage=${before.stage} afterStage=${after.stage} " +
            "beforePending=$beforePending afterPending=${after.pendingHandlers} " +
            "failed=${after.failedHandlers} coreReady=$coreReady pendingChanged=$pendingChanged " +
            "propsPending=${props.pendingInitHandlers}"
        LogBuffer.i("HwTest", "FEATURE=initialization_progress iteration=$iteration $detail")
        return featureCheck(
            name,
            iteration,
            if (progressed || coreReady || pendingChanged) RegressionResult.PASS else RegressionResult.FAIL,
            started,
            detail,
        )
    }

    private suspend fun runAncCheck(
        device: BluetoothDevice,
        iteration: Int,
    ): RegressionFeatureCheck {
        val name = "ANC read / switch / read-back"
        val started = android.os.SystemClock.elapsedRealtime()
        clearManualDisconnectSuppression()
        val attemptId = submitConnect(device, ConnectionTrigger.HardwareRegression)
        if (!waitForConnected(CONNECTION_TIMEOUT_MS, attemptId)) {
            return featureCheck(name, iteration, RegressionResult.FAIL, started, "control channel did not connect")
        }
        if (!waitForCoreReady(CONNECTION_READY_TIMEOUT_MS, attemptId)) {
            return featureCheck(name, iteration, RegressionResult.FAIL, started, "control channel did not reach core-ready")
        }
        delay(1_000)
        val before = repository.props.value.ancMode
        val options = repository.props.value.ancModeOptions
        val target = options.firstOrNull { it != before } ?: options.firstOrNull()
        if (target == null) {
            return featureCheck(name, iteration, RegressionResult.SKIPPED, started, "device returned no ANC mode options")
        }
        repository.setProperty("anc", "mode", target)
        val switched = waitUntil(READ_BACK_TIMEOUT_MS) { repository.props.value.ancMode == target }
        val after = repository.props.value.ancMode
        val detail = "before=$before target=$target after=$after readBack=$switched"
        LogBuffer.i("HwTest", "FEATURE=anc iteration=$iteration $detail")
        return featureCheck(
            name,
            iteration,
            if (switched) RegressionResult.PASS else RegressionResult.FAIL,
            started,
            detail,
        )
    }

    private suspend fun runLowLatencyCheck(
        device: BluetoothDevice,
        iteration: Int,
    ): RegressionFeatureCheck {
        val name = "automatic low-latency write / ACK / read-back"
        val started = android.os.SystemClock.elapsedRealtime()
        clearManualDisconnectSuppression()
        val attemptId = submitConnect(device, ConnectionTrigger.HardwareRegression)
        if (!waitForConnected(CONNECTION_TIMEOUT_MS, attemptId)) {
            return featureCheck(name, iteration, RegressionResult.FAIL, started, "control channel did not connect")
        }
        if (!waitForCoreReady(CONNECTION_READY_TIMEOUT_MS, attemptId)) {
            return featureCheck(name, iteration, RegressionResult.FAIL, started, "control channel did not reach core-ready")
        }
        val readBefore = repository.props.value.lowLatency
        repository.clearRegressionCommandExchange("low_latency.write_readback")
        repository.setProperty("config", "low_latency", "false")
        val offReadBack = waitUntil(READ_BACK_TIMEOUT_MS) { repository.props.value.lowLatency == false }
        val offExchange = repository.getRegressionCommandExchange("low_latency.write_readback")
        val offAck = offExchange?.ack != null
        val offPhase = offExchange?.phase

        repository.clearRegressionCommandExchange("low_latency.write_readback")
        repository.setProperty("config", "low_latency", "true")
        val onReadBack = waitUntil(READ_BACK_TIMEOUT_MS) { repository.props.value.lowLatency == true }
        val onExchange = repository.getRegressionCommandExchange("low_latency.write_readback")
        val onAck = onExchange?.ack != null
        val onPhase = onExchange?.phase
        val finalState = repository.props.value.lowLatency
        val detail = "before=$readBefore " +
            "offAck=$offAck offPhase=$offPhase offReadBack=$offReadBack " +
            "onAck=$onAck onPhase=$onPhase onReadBack=$onReadBack final=$finalState"
        LogBuffer.i("HwTest", "FEATURE=low_latency iteration=$iteration $detail")
        return featureCheck(
            name,
            iteration,
            if (offAck && offReadBack && onAck && onReadBack && finalState == true) {
                RegressionResult.PASS
            } else {
                RegressionResult.FAIL
            },
            started,
            detail,
        )
    }

    private suspend fun runTriggerDeduplicationCheck(
        device: BluetoothDevice,
        iteration: Int,
    ): RegressionFeatureCheck {
        val name = "app / Service / Tile / ACL trigger deduplication"
        val started = android.os.SystemClock.elapsedRealtime()
        clearManualDisconnectSuppression()
        val appAttempt = submitConnect(device, ConnectionTrigger.UserAction)
        if (!waitForConnected(CONNECTION_TIMEOUT_MS, appAttempt)) {
            return featureCheck(name, iteration, RegressionResult.FAIL, started, "application entry did not connect")
        }
        if (!waitForCoreReady(CONNECTION_READY_TIMEOUT_MS, appAttempt)) {
            return featureCheck(name, iteration, RegressionResult.FAIL, started, "application entry did not reach core-ready")
        }

        val serviceWhileConnected = submitAutoConnectKnown(device, ConnectionTrigger.ServiceCommand)
        val tileWhileConnected = submitAutoConnectLast(ConnectionTrigger.TileAction)
        val aclWhileConnected = submitAutoConnectKnown(device, ConnectionTrigger.AclConnected)
        val serviceSame = serviceWhileConnected.accepted && appAttempt == serviceWhileConnected.attemptId
        val tileSame = tileWhileConnected.accepted && appAttempt == tileWhileConnected.attemptId
        val aclSame = aclWhileConnected.accepted && appAttempt == aclWhileConnected.attemptId

        disconnect()
        waitForDisconnected(DISCONNECT_TIMEOUT_MS)
        clearManualDisconnectSuppression()
        val serviceRequest = submitAutoConnectLast(ConnectionTrigger.ServiceCommand)
        val serviceRequested = serviceRequest.accepted
        val serviceAttempt = serviceRequest.attemptId
        val serviceConnected = waitForConnected(CONNECTION_TIMEOUT_MS, serviceAttempt)

        disconnect()
        waitForDisconnected(DISCONNECT_TIMEOUT_MS)
        clearManualDisconnectSuppression()
        val tileRequest = submitAutoConnectLast(ConnectionTrigger.TileAction)
        val tileRequested = tileRequest.accepted
        val tileAttempt = tileRequest.attemptId
        val tileConnected = waitForConnected(CONNECTION_TIMEOUT_MS, tileAttempt)

        disconnect()
        waitForDisconnected(DISCONNECT_TIMEOUT_MS)
        clearManualDisconnectSuppression()
        val aclRequest = submitAutoConnectKnown(device, ConnectionTrigger.AclConnected)
        val aclRequested = aclRequest.accepted
        val aclAttempt = aclRequest.attemptId
        val aclConnected = waitForConnected(CONNECTION_TIMEOUT_MS, aclAttempt)

        val distinctEntryAttempts = serviceAttempt != null && tileAttempt != null && aclAttempt != null &&
            serviceAttempt != tileAttempt && tileAttempt != aclAttempt && serviceAttempt != aclAttempt
        val passed = serviceSame && tileSame && aclSame &&
            serviceRequested && serviceConnected && tileRequested && tileConnected &&
            aclRequested && aclConnected && distinctEntryAttempts
        val detail = "appAttempt=$appAttempt serviceSame=$serviceSame tileSame=$tileSame aclSame=$aclSame " +
            "serviceRequested=$serviceRequested tileRequested=$tileRequested aclRequested=$aclRequested " +
            "serviceConnected=$serviceConnected tileConnected=$tileConnected aclConnected=$aclConnected " +
            "distinctEntryAttempts=$distinctEntryAttempts scanEntry=covered_by_D"
        LogBuffer.i("HwTest", "FEATURE=trigger_dedup iteration=$iteration $detail")
        return featureCheck(
            name,
            iteration,
            if (passed) RegressionResult.PASS else RegressionResult.FAIL,
            started,
            detail,
        )
    }

    private fun featureCheck(
        name: String,
        iteration: Int,
        result: RegressionResult,
        startedAt: Long,
        detail: String,
    ): RegressionFeatureCheck = RegressionFeatureCheck(
        name = name,
        iteration = iteration,
        result = result,
        elapsedMs = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
        detail = detail,
    )

    private data class DiscoveryResult(
        val device: BluetoothDevice,
        val source: String,
    )

    private suspend fun discover(
        address: String,
        savedDevice: BluetoothDevice,
    ): DiscoveryResult? = suspendCancellableCoroutine { continuation ->
        val scanner = BluetoothScanner(appContext)
        continuation.invokeOnCancellation { scanner.stopScan() }
        scanner.startScan { success ->
            val scanned = if (success) {
                scanner.found.firstOrNull { it.address.equals(address, ignoreCase = true) }?.device
            } else {
                null
            }
            val result = scanned?.let { DiscoveryResult(it, "found") }
                ?: savedDevice.takeIf { success }?.let {
                    LogBuffer.w("HwTest", "Discovery completed without ACTION_FOUND; using saved-device fallback")
                    DiscoveryResult(it, "saved-fallback")
                }
            scanner.stopScan()
            if (continuation.isActive) continuation.resume(result)
        }
    }

    private fun submitConnect(device: BluetoothDevice, trigger: ConnectionTrigger): String? =
        (connectionManager.submit(ConnectionCommand.Connect(device, trigger)) as ConnectionCommandResult.Attempt).attemptId

    private fun submitKnownAutoConnect(
        device: BluetoothDevice,
        trigger: ConnectionTrigger,
    ): ConnectionRequestResult {
        val result = connectionManager.submit(
            ConnectionCommand.AutoConnectKnownSystemConnected(device, trigger = trigger)
        ) as ConnectionCommandResult.Accepted
        return ConnectionRequestResult(result.value, result.attemptId)
    }

    private fun submitAutoConnectKnown(device: BluetoothDevice, trigger: ConnectionTrigger): ConnectionRequestResult =
        submitKnownAutoConnect(device, trigger)

    private fun submitAutoConnectSaved(
        address: String,
        logMisses: Boolean,
        trigger: ConnectionTrigger,
    ): ConnectionRequestResult {
        val result = connectionManager.submit(
            ConnectionCommand.AutoConnectSaved(address, logMisses, trigger)
        ) as ConnectionCommandResult.Accepted
        return ConnectionRequestResult(result.value, result.attemptId)
    }

    private fun submitAutoConnectLast(trigger: ConnectionTrigger): ConnectionRequestResult {
        val result = connectionManager.submit(
            ConnectionCommand.AutoConnectLastSaved(trigger)
        ) as ConnectionCommandResult.Accepted
        return ConnectionRequestResult(result.value, result.attemptId)
    }

    private fun clearManualDisconnectSuppression() {
        connectionManager.submit(ConnectionCommand.ClearManualDisconnectSuppression)
    }

    private fun disconnect() {
        connectionManager.submit(ConnectionCommand.Disconnect)
    }

    private suspend fun ensureDisconnected() {
        if (repository.connectionState.value !is ConnectionState.Disconnected) {
            disconnect()
            waitForDisconnected(DISCONNECT_TIMEOUT_MS)
        }
        clearManualDisconnectSuppression()
    }

    private suspend fun waitForConnected(timeoutMs: Long, expectedAttemptId: String? = null): Boolean {
        // A null id means the command was rejected or lost its reservation. Never accept a
        // later periodic/background connection as the result of this sample.
        if (expectedAttemptId == null) return false
        return withTimeoutOrNull(timeoutMs) {
            repository.connectionState
                .map { connectionAttemptWaitResult(it, expectedAttemptId) }
                .filter { it != ConnectionAttemptWaitResult.Pending }
                .first() == ConnectionAttemptWaitResult.Connected
        } ?: false
    }

    private suspend fun waitForReady(timeoutMs: Long, expectedAttemptId: String?): Boolean {
        return waitForControlStage(
            timeoutMs = timeoutMs,
            expectedAttemptId = expectedAttemptId,
            acceptedStages = setOf(
                ControlChannelStage.Ready,
                ControlChannelStage.Degraded,
            ),
        )
    }

    private suspend fun waitForCoreReady(timeoutMs: Long, expectedAttemptId: String?): Boolean {
        return waitForControlStage(
            timeoutMs = timeoutMs,
            expectedAttemptId = expectedAttemptId,
            acceptedStages = setOf(
                ControlChannelStage.CoreReady,
                ControlChannelStage.InitializingDeferred,
                ControlChannelStage.Ready,
                ControlChannelStage.Degraded,
            ),
        )
    }

    private suspend fun waitForControlStage(
        timeoutMs: Long,
        expectedAttemptId: String?,
        acceptedStages: Set<ControlChannelStage>,
    ): Boolean {
        if (expectedAttemptId == null) return false
        return withTimeoutOrNull(timeoutMs) {
            val terminalStage = repository.controlChannelState
                .filter { state ->
                    state.attemptId == expectedAttemptId &&
                        (state.stage in acceptedStages || state.stage == ControlChannelStage.Failed)
                }
                .first()
            terminalStage.stage in acceptedStages
        } ?: false
    }

    private suspend fun waitForDisconnected(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) {
            repository.connectionState.filter { it is ConnectionState.Disconnected }.first()
            true
        } ?: false

    private suspend fun waitForConnectedOrFailed(
        timeoutMs: Long,
        expectedAttemptId: String? = null,
    ): ConnectionState {
        if (expectedAttemptId == null) {
            return ConnectionState.Failed("connection request did not create an attempt")
        }
        return withTimeoutOrNull(timeoutMs) {
            repository.connectionState.filter {
                (it is ConnectionState.Connected || it is ConnectionState.Failed) &&
                    when (it) {
                        is ConnectionState.Connected -> it.attemptId == expectedAttemptId
                        is ConnectionState.Failed -> it.attemptId == expectedAttemptId
                    }
            }.first()
        } ?: ConnectionState.Failed(
            reason = "connection attempt timed out",
            attemptId = expectedAttemptId,
        )
    }

    private suspend fun waitUntil(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return true
            delay(200)
        }
        return predicate()
    }

    private fun setAutoLowLatencyPreference(enabled: Boolean) {
        appContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(AUTO_LOW_LATENCY_KEY, enabled)
            .apply()
        LogBuffer.i("HwTest", "auto_low_latency_preference=$enabled")
    }

    private fun currentAutoLowLatency(): Boolean = appContext
        .getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getBoolean(AUTO_LOW_LATENCY_KEY, true)

    private fun attempt(
        scenario: RegressionScenario,
        iteration: Int,
        result: RegressionResult,
        startedAt: Long,
        detail: String,
        timing: ConnectionTimingSnapshot? = null,
    ): RegressionAttempt {
        val elapsed = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        LogBuffer.i("HwTest", "RESULT case=${scenario.id} iteration=$iteration result=$result elapsed=${elapsed}ms $detail")
        return RegressionAttempt(scenario, iteration, result, elapsed, detail, timing)
    }

    private fun timingDetail(timing: ConnectionTimingSnapshot?): String =
        "transportConnect=${timing?.transportConnectMs ?: 0}ms " +
            "transportToCore=${timing?.transportToCoreReadyMs ?: 0}ms " +
            "coreToReady=${timing?.coreToReadyMs ?: 0}ms"

    private fun buildReport(
        startedAt: Long,
        device: BluetoothDevice?,
        attempts: List<RegressionAttempt>,
        features: List<RegressionFeatureCheck>,
        earlyFailure: String?,
    ): String = buildString {
        appendLine("fxxkHilife hardware regression report")
        appendLine("format=2")
        appendLine("startedAt=$startedAt")
        appendLine("finishedAt=${System.currentTimeMillis()}")
        appendLine("appVersion=${BuildConfig.VERSION_NAME}")
        appendLine("versionCode=${BuildConfig.VERSION_CODE}")
        appendLine("device=${device?.name ?: "unknown"}")
        appendLine("address=${device?.address ?: "unknown"}")
        appendLine("endpoint=${repository.getRegressionEndpoint()}")
        appendLine("iterations=${_state.value.totalIterations}")
        appendLine("scenarioSamples=${attempts.size}")
        appendLine("featureSamples=${features.size}")
        earlyFailure?.let { appendLine("earlyFailure=$it") }
        appendLine()
        appendLine("## scenario statistics")
        RegressionScenario.entries.forEach { scenario ->
            val values = attempts.filter { it.scenario == scenario }
            val durations = values.map { it.elapsedMs }
            val transportConnect = values.mapNotNull { it.timing?.transportConnectMs }
            val transportToCore = values.mapNotNull { it.timing?.transportToCoreReadyMs }
            val coreToReady = values.mapNotNull { it.timing?.coreToReadyMs }
            appendLine(
                "${scenario.id}\t${scenario.title}\tsamples=${values.size}" +
                    "\tpass=${values.count { it.result == RegressionResult.PASS }}" +
                    "\tfail=${values.count { it.result == RegressionResult.FAIL }}" +
                    "\tp50=${RegressionMetrics.percentile(durations, 0.50)}ms" +
                    "\tp95=${RegressionMetrics.percentile(durations, 0.95)}ms" +
                    "\tmax=${durations.maxOrNull() ?: 0}ms" +
                    "\ttransportConnectP50=${RegressionMetrics.percentile(transportConnect, 0.50)}ms" +
                    "\ttransportConnectP95=${RegressionMetrics.percentile(transportConnect, 0.95)}ms" +
                    "\ttransportToCoreP50=${RegressionMetrics.percentile(transportToCore, 0.50)}ms" +
                    "\ttransportToCoreP95=${RegressionMetrics.percentile(transportToCore, 0.95)}ms" +
                    "\tcoreToReadyP50=${RegressionMetrics.percentile(coreToReady, 0.50)}ms" +
                    "\tcoreToReadyP95=${RegressionMetrics.percentile(coreToReady, 0.95)}ms",
            )
        }
        appendLine()
        appendLine("## attempts")
        attempts.forEach {
            appendLine("${it.scenario.id}\t${it.iteration}\t${it.result}\t${it.elapsedMs}ms\t${it.detail}")
        }
        appendLine()
        appendLine("## feature statistics")
        features.groupBy { it.name }.forEach { (name, values) ->
            val durations = values.map { it.elapsedMs }
            appendLine(
                "$name\tsamples=${values.size}" +
                    "\tpass=${values.count { it.result == RegressionResult.PASS }}" +
                    "\tfail=${values.count { it.result == RegressionResult.FAIL }}" +
                    "\tskipped=${values.count { it.result == RegressionResult.SKIPPED }}" +
                    "\tp50=${RegressionMetrics.percentile(durations, 0.50)}ms" +
                    "\tp95=${RegressionMetrics.percentile(durations, 0.95)}ms" +
                    "\tmax=${durations.maxOrNull() ?: 0}ms",
            )
        }
        appendLine()
        appendLine("## feature checks")
        features.forEach {
            appendLine("${it.name}\t${it.iteration}\t${it.result}\t${it.elapsedMs}ms\t${it.detail}")
        }
        appendLine()
        appendLine("## diagnostic report")
        append(LogBuffer.getDiagnosticReport())
    }

    private fun limitReportBytes(report: String, maxBytes: Long): String {
        val bytes = report.toByteArray(Charsets.UTF_8)
        if (bytes.size.toLong() <= maxBytes) return report

        val limit = maxBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val marker = "\n\n[report truncated to ${maxBytes} bytes]\n"
        val markerBytes = marker.toByteArray(Charsets.UTF_8)
        if (markerBytes.size >= limit) return String(bytes, 0, limit, Charsets.UTF_8)

        val remaining = limit - markerBytes.size
        val headLimit = remaining / 2
        val tailLimit = remaining - headLimit
        val head = decodeUtf8Prefix(bytes, headLimit)
        val tail = decodeUtf8Suffix(bytes, tailLimit)
        return head + marker + tail
    }

    private fun decodeUtf8Prefix(bytes: ByteArray, maxBytes: Int): String {
        var end = maxBytes.coerceIn(0, bytes.size)
        while (end > 0) {
            val value = String(bytes, 0, end, Charsets.UTF_8)
            if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value
            end--
        }
        return ""
    }

    private fun decodeUtf8Suffix(bytes: ByteArray, maxBytes: Int): String {
        var start = (bytes.size - maxBytes).coerceAtLeast(0)
        while (start < bytes.size) {
            val value = String(bytes, start, bytes.size - start, Charsets.UTF_8)
            if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value
            start++
        }
        return ""
    }

    private fun finish(report: String, failed: Int, message: String) {
        lastReport = report
        LogBuffer.putMetadata("hardwareRegressionReportReady", "true")
        LogBuffer.i("HwTest", "FINISH failed=$failed")
        _state.value = _state.value.copy(
            running = false,
            completed = _state.value.completed,
            failed = failed,
            reportReady = true,
            message = message,
        )
        runJob = null
    }

    private suspend fun withContextMain(context: Context, block: () -> Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { block() }
    }

    companion object {
        const val DEFAULT_ITERATIONS = 10
        const val MAX_ITERATIONS = 10
        private val FEATURE_CHECK_NAMES = listOf(
            "application entry / 10s initialization progression",
            "ANC read / switch / read-back",
            "automatic low-latency write / ACK / read-back",
            "app / Service / Tile / ACL trigger deduplication",
        )
        private const val SETTINGS_PREFS = "settings"
        private const val AUTO_LOW_LATENCY_KEY = "auto_low_latency"
        private const val CONNECTION_TIMEOUT_MS = 30_000L
        private const val CONNECTION_READY_TIMEOUT_MS = 12_000L
        private const val DISCONNECT_TIMEOUT_MS = 5_000L
        private const val INITIALIZATION_OBSERVATION_MS = 10_000L
        private const val READ_BACK_TIMEOUT_MS = 8_000L
        // The test mode has no line-count cap. Keep a headroom below the 200 MB share/file limit
        // so the report header, scenario tables and UTF-8 encoding remain processable.
        private const val REGRESSION_CAPTURE_MAX_BYTES = 160_000_000L
        private const val REGRESSION_REPORT_MAX_BYTES = 190_000_000L
    }
}
