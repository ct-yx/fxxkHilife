package com.freebuds.controller.data

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.core.content.FileProvider
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
)

data class RegressionFeatureCheck(
    val name: String,
    val result: RegressionResult,
    val detail: String,
)

data class BluetoothRegressionState(
    val running: Boolean = false,
    val scenario: RegressionScenario? = null,
    val iteration: Int = 0,
    val totalIterations: Int = 10,
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
                file.writeText(report)
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
        val originalMaxLogLines = LogBuffer.getMaxLines()
        LogBuffer.setMaxLines(REGRESSION_MAX_LOG_LINES)
        val originalAutoLowLatency = appContext
            .getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getBoolean(AUTO_LOW_LATENCY_KEY, true)
        val device = repository.getRegressionDevice()

        LogBuffer.putMetadata("hardwareRegressionIterations", iterations.toString())
        LogBuffer.putMetadata("hardwareRegressionStartedAt", startedAt.toString())
        LogBuffer.i("HwTest", "START iterations=$iterations")

        if (device == null) {
            val detail = "No saved or currently connected Bluetooth device"
            LogBuffer.e("HwTest", "SKIP $detail")
            val report = buildReport(startedAt, null, attempts, featureChecks, detail)
            LogBuffer.setMaxLines(originalMaxLogLines)
            finish(report, failed = 1, message = I18n.t("terminal.regression.no_device"))
            return
        }

        try {
            LogBuffer.putMetadata("hardwareRegressionDevice", device.name ?: device.address)
            LogBuffer.putMetadata("hardwareRegressionAddress", device.address)
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
                                repository.autoConnectKnownSystemConnected(
                                    device,
                                    trigger = ConnectionTrigger.HardwareRegression,
                                )
                            },
                        )
                        RegressionScenario.B -> runConnectionAttempt(
                            device,
                            scenario,
                            iteration,
                            ConnectionTrigger.HardwareRegression,
                            autoLowLatency = true,
                            requestConnection = {
                                repository.autoConnectKnownSystemConnected(
                                    device,
                                    trigger = ConnectionTrigger.HardwareRegression,
                                )
                            },
                        )
                        RegressionScenario.C -> runConnectionAttempt(
                            device,
                            scenario,
                            iteration,
                            ConnectionTrigger.AclConnected,
                            autoLowLatency = originalAutoLowLatency,
                            requestConnection = {
                                repository.autoConnectKnownSystemConnected(
                                    device,
                                    trigger = ConnectionTrigger.AclConnected,
                                )
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

            ensureDisconnected()
            featureChecks += runAncCheck(device)
            ensureDisconnected()
            featureChecks += runLowLatencyCheck(device)
            ensureDisconnected()
            featureChecks += runTriggerDeduplicationCheck(device)
        } catch (e: CancellationException) {
            LogBuffer.setMaxLines(originalMaxLogLines)
            LogBuffer.w("HwTest", "CANCELLED")
            throw e
        } catch (e: Exception) {
            LogBuffer.e("HwTest", "Runner failed: ${e.javaClass.simpleName}: ${e.message}")
            featureChecks += RegressionFeatureCheck(
                name = "runner",
                result = RegressionResult.FAIL,
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
        }

        val report = buildReport(startedAt, device, attempts, featureChecks, null)
        LogBuffer.setMaxLines(originalMaxLogLines)
        val failures = attempts.count { it.result == RegressionResult.FAIL } +
            featureChecks.count { it.result == RegressionResult.FAIL }
        finish(
            report = report,
            failed = failures,
            message = I18n.t("terminal.regression.finished", attempts.size, failures),
        )
    }

    private suspend fun runConnectionAttempt(
        device: BluetoothDevice,
        scenario: RegressionScenario,
        iteration: Int,
        trigger: ConnectionTrigger,
        autoLowLatency: Boolean,
        requestConnection: () -> Boolean = {
            repository.connect(device, trigger)
            true
        },
    ): RegressionAttempt {
        val started = android.os.SystemClock.elapsedRealtime()
        setAutoLowLatencyPreference(autoLowLatency)
        LogBuffer.i(
            "HwTest",
            "CASE=${scenario.id} iteration=$iteration trigger=${trigger.name} " +
                "autoLowLatency=$autoLowLatency endpoint=${repository.getRegressionEndpoint()}",
        )
        if (!requestConnection()) {
            return attempt(
                scenario,
                iteration,
                RegressionResult.FAIL,
                started,
                "connection trigger did not find a system-connected saved device",
            )
        }
        val connected = waitForConnected(CONNECTION_TIMEOUT_MS)
        if (!connected) {
            return attempt(
                scenario,
                iteration,
                RegressionResult.FAIL,
                started,
                "control channel did not connect state=${repository.connectionState.value}",
            )
        }

        // The original bug appears after the first ten seconds. Keep the complete window in
        // every baseline sample instead of treating TransportReady as a successful init.
        delay(INITIALIZATION_OBSERVATION_MS)
        val props = repository.props.value
        val ready = repository.isCoreStateReady()
        val endpoint = repository.getRegressionEndpoint()
        val detail = "ready=$ready lowLatency=${props.lowLatency} anc=${props.ancMode} " +
            "pending=${props.pendingInitHandlers} endpoint=$endpoint"
        LogBuffer.i("HwTest", "CASE=${scenario.id} iteration=$iteration $detail")
        return attempt(
            scenario,
            iteration,
            if (ready) RegressionResult.PASS else RegressionResult.FAIL,
            started,
            detail,
        )
    }

    private suspend fun runDiscoveryAttempt(
        savedDevice: BluetoothDevice,
        iteration: Int,
    ): RegressionAttempt {
        val started = android.os.SystemClock.elapsedRealtime()
        LogBuffer.i("HwTest", "CASE=D iteration=$iteration starting discovery")
        val discovered = discover(savedDevice.address)
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
            discovered,
            RegressionScenario.D,
            iteration,
            ConnectionTrigger.ScanCompleted,
            autoLowLatency = currentAutoLowLatency(),
        )
        return result.copy(detail = "discovery=found; ${result.detail}")
    }

    private suspend fun runRetryAttempt(
        device: BluetoothDevice,
        iteration: Int,
        autoLowLatency: Boolean,
    ): RegressionAttempt {
        val started = android.os.SystemClock.elapsedRealtime()
        setAutoLowLatencyPreference(autoLowLatency)
        LogBuffer.i("HwTest", "CASE=E iteration=$iteration natural-failure-retry")
        repository.connect(device, ConnectionTrigger.HardwareRegression)
        val firstOutcome = waitForConnectedOrFailed(CONNECTION_TIMEOUT_MS)
        if (firstOutcome is ConnectionState.Failed) {
            LogBuffer.w("HwTest", "CASE=E iteration=$iteration first attempt failed; retrying")
            ensureDisconnected()
            repository.clearManualDisconnectSuppression()
            repository.connect(device, ConnectionTrigger.HardwareRegression)
            if (!waitForConnected(CONNECTION_TIMEOUT_MS)) {
                return attempt(RegressionScenario.E, iteration, RegressionResult.FAIL, started, "first failure + retry failed")
            }
        } else if (firstOutcome !is ConnectionState.Connected) {
            return attempt(RegressionScenario.E, iteration, RegressionResult.FAIL, started, "first attempt timed out")
        } else {
            LogBuffer.i("HwTest", "CASE=E iteration=$iteration first attempt passed; retry gate not needed")
        }
        delay(INITIALIZATION_OBSERVATION_MS)
        val props = repository.props.value
        val detail = "firstAttempt=${firstOutcome::class.simpleName} ready=${repository.isCoreStateReady()} " +
            "pending=${props.pendingInitHandlers} endpoint=${repository.getRegressionEndpoint()}"
        return attempt(
            RegressionScenario.E,
            iteration,
            if (repository.isCoreStateReady()) RegressionResult.PASS else RegressionResult.FAIL,
            started,
            detail,
        )
    }

    private suspend fun runManualDisconnectAttempt(
        device: BluetoothDevice,
        iteration: Int,
        autoLowLatency: Boolean,
    ): RegressionAttempt {
        val started = android.os.SystemClock.elapsedRealtime()
        setAutoLowLatencyPreference(autoLowLatency)
        repository.connect(device, ConnectionTrigger.HardwareRegression)
        if (!waitForConnected(CONNECTION_TIMEOUT_MS)) {
            return attempt(RegressionScenario.F, iteration, RegressionResult.FAIL, started, "initial connect failed")
        }
        repository.disconnect()
        val disconnected = waitForDisconnected(DISCONNECT_TIMEOUT_MS)
        val suppressed = !repository.autoConnectSaved(
            device.address,
            logMisses = true,
            trigger = ConnectionTrigger.PeriodicCheck,
        )
        repository.clearManualDisconnectSuppression()
        repository.connect(device, ConnectionTrigger.HardwareRegression)
        val reconnected = waitForConnected(CONNECTION_TIMEOUT_MS)
        repository.regressionSimulateAclDisconnect()
        val aclDisconnected = waitForDisconnected(DISCONNECT_TIMEOUT_MS)
        repository.clearManualDisconnectSuppression()
        repository.connect(device, ConnectionTrigger.AclConnected)
        val aclReconnected = waitForConnected(CONNECTION_TIMEOUT_MS)
        val detail = "disconnected=$disconnected suppression=$suppressed reconnected=$reconnected " +
            "aclDisconnected=$aclDisconnected aclReconnected=$aclReconnected " +
            "endpoint=${repository.getRegressionEndpoint()}"
        return attempt(
            RegressionScenario.F,
            iteration,
            if (disconnected && suppressed && reconnected && aclDisconnected && aclReconnected) {
                RegressionResult.PASS
            } else {
                RegressionResult.FAIL
            },
            started,
            detail,
        )
    }

    private suspend fun runAncCheck(device: BluetoothDevice): RegressionFeatureCheck {
        val name = "ANC read / switch / read-back"
        repository.clearManualDisconnectSuppression()
        repository.connect(device, ConnectionTrigger.HardwareRegression)
        if (!waitForConnected(CONNECTION_TIMEOUT_MS)) {
            return RegressionFeatureCheck(name, RegressionResult.FAIL, "control channel did not connect")
        }
        delay(1_000)
        val before = repository.props.value.ancMode
        val options = repository.props.value.ancModeOptions
        val target = options.firstOrNull { it != before } ?: options.firstOrNull()
        if (target == null) {
            return RegressionFeatureCheck(name, RegressionResult.SKIPPED, "device returned no ANC mode options")
        }
        repository.setProperty("anc", "mode", target)
        val switched = waitUntil(READ_BACK_TIMEOUT_MS) { repository.props.value.ancMode == target }
        val after = repository.props.value.ancMode
        LogBuffer.i("HwTest", "FEATURE=anc before=$before target=$target after=$after readBack=$switched")
        return RegressionFeatureCheck(
            name,
            if (switched) RegressionResult.PASS else RegressionResult.FAIL,
            "before=$before target=$target after=$after readBack=$switched",
        )
    }

    private suspend fun runLowLatencyCheck(device: BluetoothDevice): RegressionFeatureCheck {
        val name = "automatic low-latency write / ACK / read-back"
        repository.clearManualDisconnectSuppression()
        repository.connect(device, ConnectionTrigger.HardwareRegression)
        if (!waitForConnected(CONNECTION_TIMEOUT_MS)) {
            return RegressionFeatureCheck(name, RegressionResult.FAIL, "control channel did not connect")
        }
        val readBefore = repository.props.value.lowLatency
        repository.setProperty("config", "low_latency", "false")
        val offReadBack = waitUntil(READ_BACK_TIMEOUT_MS) { repository.props.value.lowLatency == false }
        repository.setProperty("config", "low_latency", "true")
        val onReadBack = waitUntil(READ_BACK_TIMEOUT_MS) { repository.props.value.lowLatency == true }
        val detail = "before=$readBefore offReadBack=$offReadBack onReadBack=$onReadBack final=${repository.props.value.lowLatency}"
        LogBuffer.i("HwTest", "FEATURE=low_latency $detail")
        return RegressionFeatureCheck(
            name,
            if (offReadBack && onReadBack) RegressionResult.PASS else RegressionResult.FAIL,
            detail,
        )
    }

    private suspend fun runTriggerDeduplicationCheck(device: BluetoothDevice): RegressionFeatureCheck {
        val name = "Service / Tile / ACL trigger deduplication"
        repository.clearManualDisconnectSuppression()
        repository.connect(device, ConnectionTrigger.HardwareRegression)
        if (!waitForConnected(CONNECTION_TIMEOUT_MS)) {
            return RegressionFeatureCheck(name, RegressionResult.FAIL, "control channel did not connect")
        }
        repository.disconnect()
        waitForDisconnected(DISCONNECT_TIMEOUT_MS)
        repository.clearManualDisconnectSuppression()
        val serviceRequested = repository.autoConnectLastSaved(ConnectionTrigger.ServiceCommand)
        val serviceConnected = waitForConnected(CONNECTION_TIMEOUT_MS)
        val serviceAttempt = repository.getRegressionAttemptId()
        repository.autoConnectLastSaved(ConnectionTrigger.TileAction)
        delay(1_000)
        val tileWhileConnectedSameAttempt = serviceAttempt != null && serviceAttempt == repository.getRegressionAttemptId()
        repository.disconnect()
        waitForDisconnected(DISCONNECT_TIMEOUT_MS)
        repository.clearManualDisconnectSuppression()
        val tileRequested = repository.autoConnectLastSaved(ConnectionTrigger.TileAction)
        val tileConnected = waitForConnected(CONNECTION_TIMEOUT_MS)
        val tileAttempt = repository.getRegressionAttemptId()
        val hasDistinctEntryAttempts = serviceAttempt != null && tileAttempt != null && serviceAttempt != tileAttempt
        val sameAttempt = serviceRequested && serviceConnected && tileWhileConnectedSameAttempt &&
            tileRequested && tileConnected && hasDistinctEntryAttempts
        LogBuffer.i(
            "HwTest",
            "FEATURE=trigger_dedup serviceRequested=$serviceRequested tileRequested=$tileRequested " +
                "serviceConnected=$serviceConnected tileConnected=$tileConnected " +
                "sameAttemptWhileConnected=$tileWhileConnectedSameAttempt distinctEntryAttempts=$hasDistinctEntryAttempts " +
                "aclBroadcast=covered_by_receiver_test_hook",
        )
        return RegressionFeatureCheck(
            name,
            if (sameAttempt) RegressionResult.PASS else RegressionResult.FAIL,
            "serviceRequested=$serviceRequested tileRequested=$tileRequested " +
                "serviceConnected=$serviceConnected tileConnected=$tileConnected " +
                "sameAttemptWhileConnected=$tileWhileConnectedSameAttempt distinctEntryAttempts=$hasDistinctEntryAttempts; " +
                "ACL receiver path is covered by live broadcasts and F synthetic cleanup",
        )
    }

    private suspend fun discover(address: String): BluetoothDevice? = suspendCancellableCoroutine { continuation ->
        val scanner = BluetoothScanner(appContext)
        continuation.invokeOnCancellation { scanner.stopScan() }
        scanner.startScan { success ->
            val result = if (success) scanner.found.firstOrNull { it.address == address }?.device else null
            scanner.stopScan()
            if (continuation.isActive) continuation.resume(result)
        }
    }

    private suspend fun ensureDisconnected() {
        if (repository.connectionState.value !is ConnectionState.Disconnected) {
            repository.disconnect()
            waitForDisconnected(DISCONNECT_TIMEOUT_MS)
        }
        repository.clearManualDisconnectSuppression()
    }

    private suspend fun waitForConnected(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) {
            repository.connectionState.filter { it is ConnectionState.Connected }.first()
            true
        } ?: false

    private suspend fun waitForDisconnected(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) {
            repository.connectionState.filter { it is ConnectionState.Disconnected }.first()
            true
        } ?: false

    private suspend fun waitForConnectedOrFailed(timeoutMs: Long): ConnectionState =
        withTimeoutOrNull(timeoutMs) {
            repository.connectionState.filter {
                it is ConnectionState.Connected || it is ConnectionState.Failed
            }.first()
        } ?: repository.connectionState.value

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
    ): RegressionAttempt {
        val elapsed = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        LogBuffer.i("HwTest", "RESULT case=${scenario.id} iteration=$iteration result=$result elapsed=${elapsed}ms $detail")
        return RegressionAttempt(scenario, iteration, result, elapsed, detail)
    }

    private fun buildReport(
        startedAt: Long,
        device: BluetoothDevice?,
        attempts: List<RegressionAttempt>,
        features: List<RegressionFeatureCheck>,
        earlyFailure: String?,
    ): String = buildString {
        appendLine("fxxkHilife hardware regression report")
        appendLine("format=1")
        appendLine("startedAt=$startedAt")
        appendLine("finishedAt=${System.currentTimeMillis()}")
        appendLine("device=${device?.name ?: "unknown"}")
        appendLine("address=${device?.address ?: "unknown"}")
        appendLine("endpoint=${repository.getRegressionEndpoint()}")
        appendLine("iterations=${_state.value.totalIterations}")
        earlyFailure?.let { appendLine("earlyFailure=$it") }
        appendLine()
        appendLine("## scenario statistics")
        RegressionScenario.entries.forEach { scenario ->
            val values = attempts.filter { it.scenario == scenario }
            val durations = values.filter { it.result == RegressionResult.PASS }.map { it.elapsedMs }
            appendLine(
                "${scenario.id}\t${scenario.title}\tsamples=${values.size}" +
                    "\tpass=${values.count { it.result == RegressionResult.PASS }}" +
                    "\tfail=${values.count { it.result == RegressionResult.FAIL }}" +
                    "\tp50=${RegressionMetrics.percentile(durations, 0.50)}ms" +
                    "\tp95=${RegressionMetrics.percentile(durations, 0.95)}ms" +
                    "\tmax=${durations.maxOrNull() ?: 0}ms",
            )
        }
        appendLine()
        appendLine("## attempts")
        attempts.forEach {
            appendLine("${it.scenario.id}\t${it.iteration}\t${it.result}\t${it.elapsedMs}ms\t${it.detail}")
        }
        appendLine()
        appendLine("## feature checks")
        features.forEach { appendLine("${it.result}\t${it.name}\t${it.detail}") }
        appendLine()
        appendLine("## diagnostic report")
        append(LogBuffer.getDiagnosticReport())
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
        private const val SETTINGS_PREFS = "settings"
        private const val AUTO_LOW_LATENCY_KEY = "auto_low_latency"
        private const val CONNECTION_TIMEOUT_MS = 30_000L
        private const val DISCONNECT_TIMEOUT_MS = 5_000L
        private const val INITIALIZATION_OBSERVATION_MS = 10_000L
        private const val READ_BACK_TIMEOUT_MS = 8_000L
        private const val REGRESSION_MAX_LOG_LINES = 50_000
    }
}
