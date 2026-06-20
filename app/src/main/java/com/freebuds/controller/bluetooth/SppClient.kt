package com.freebuds.controller.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.freebuds.controller.util.DebugLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING
}

sealed class ConnectionEvent {
    data object Connected : ConnectionEvent()
    data class Disconnected(val reason: String) : ConnectionEvent()
    data class PackageReceived(val pkg: SppPackage) : ConnectionEvent()
    data class Error(val error: Throwable) : ConnectionEvent()
    /** Emitted when the heartbeat ping fails after HEARTBEAT_MAX_RETRIES consecutive failures */
    data class HeartbeatLost(val attempt: Int) : ConnectionEvent()
}

class SppClient(private val device: BluetoothDevice) : ISppClient {
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var job: Job? = null
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    private val _events = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ConnectionEvent> get() = _events

    private val pending = mutableMapOf<String, CompletableDeferred<SppPackage>>()
    private val handlerCommands = mutableMapOf<String, suspend (SppPackage) -> Unit>()

    companion object {
        const val TAG = "SppClient"
        var logEnabled = false  // toggled by PreferencesRepository.debugLog
        const val HEARTBEAT_INTERVAL_MS = 30_000L       // 30s between heartbeat pings
        const val HEARTBEAT_TIMEOUT_MS = 5_000L         // 5s timeout per ping
        const val HEARTBEAT_MAX_RETRIES = 2              // 2 consecutive failures → HeartbeatLost
    }

    suspend fun connect(timeoutMs: Long = 10000): Boolean {
        _state.value = ConnectionState.CONNECTING
        DebugLogger.d(TAG, "Connecting to ${device.name} (${device.address}) via SPP...")
        return try {
            socket = withTimeout(timeoutMs) {
                val sock = device.createRfcommSocketToServiceRecord(UUID.fromString(SppPackage.SPP_UUID))
                DebugLogger.d(TAG, "RFCOMM socket created, connecting...")
                sock.connect()
                sock
            }
            inputStream = socket?.inputStream
            outputStream = socket?.outputStream
            _state.value = ConnectionState.CONNECTED
            DebugLogger.i(TAG, "✅ SPP connected to ${device.name}")
            _events.tryEmit(ConnectionEvent.Connected)
            startReceiveLoop()
            startHeartbeat()
            true
        } catch (e: Exception) {
            _state.value = ConnectionState.DISCONNECTED
            DebugLogger.e(TAG, "❌ SPP connect failed: ${e.javaClass.simpleName} – ${e.message}", e)
            _events.tryEmit(ConnectionEvent.Error(e))
            false
        }
    }

    override suspend fun send(pkg: SppPackage, timeoutMs: Long): SppPackage? {
        val key = pkg.commandId.joinToString("") { "%02x".format(it) }
        if (pkg.responseId.isNotEmpty()) {
            pending[key]?.cancel()
            val deferred = CompletableDeferred<SppPackage>()
            pending[key] = deferred
            try {
                outputStream?.write(pkg.toBytes())
                outputStream?.flush()
                return withTimeout(timeoutMs) { deferred.await() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLogger.w(TAG, "send() failed for cmd=${pkg.commandId.contentToString()}: ${e.message}")
                return null
            } finally {
                pending.remove(key)
            }
        } else {
            try {
                outputStream?.write(pkg.toBytes())
                outputStream?.flush()
            } catch (e: Exception) {
                DebugLogger.w(TAG, "send(fire-and-forget) failed: ${e.message}")
            }
            return null
        }
    }

    override fun registerHandler(cmdId: ByteArray, handler: suspend (SppPackage) -> Unit) {
        val key = cmdId.joinToString("") { "%02x".format(it) }
        handlerCommands[key] = handler
    }

    /**
     * Starts connection health heartbeat: sends a BATTERY_READ ping every
     * HEARTBEAT_INTERVAL_MS.  After HEARTBEAT_MAX_RETRIES consecutive failures
     * (timeout or exception), emits [ConnectionEvent.HeartbeatLost].
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            var consecutiveFailures = 0
            while (isActive && _state.value == ConnectionState.CONNECTED) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (_state.value != ConnectionState.CONNECTED) break

                val ok = try {
                    withTimeout(HEARTBEAT_TIMEOUT_MS) {
                        sendBatteryPing() != null
                    }
                } catch (_: TimeoutCancellationException) {
                    DebugLogger.w(TAG, "Heartbeat ping timeout (attempt ${consecutiveFailures + 1})")
                    false
                } catch (e: Exception) {
                    DebugLogger.w(TAG, "Heartbeat ping error: ${e.message}")
                    false
                }

                if (ok) {
                    consecutiveFailures = 0
                } else {
                    consecutiveFailures++
                    DebugLogger.w(TAG, "Heartbeat failure $consecutiveFailures/$HEARTBEAT_MAX_RETRIES")
                    if (consecutiveFailures >= HEARTBEAT_MAX_RETRIES) {
                        DebugLogger.e(TAG, "Heartbeat lost — $consecutiveFailures consecutive failures")
                        _events.tryEmit(ConnectionEvent.HeartbeatLost(consecutiveFailures))
                        break
                    }
                }
            }
        }
    }

    /**
     * Sends a lightweight battery-read ping and waits for the matching response.
     */
    private suspend fun sendBatteryPing(): SppPackage? {
        val pkg = SppPackage.readRequest(SppCommand.BATTERY_READ, listOf(1))
        val key = pkg.commandId.joinToString("") { "%02x".format(it) }
        val deferred = CompletableDeferred<SppPackage>()
        pending[key]?.cancel()
        pending[key] = deferred
        return try {
            outputStream?.write(pkg.toBytes())
            outputStream?.flush()
            deferred.await()
        } catch (_: CancellationException) {
            throw CancellationException("Heartbeat cancelled")
        } catch (_: Exception) {
            null
        } finally {
            pending.remove(key)
        }
    }

    private fun startReceiveLoop() {
        job = scope.launch {
            val buffer = mutableListOf<Byte>()
            try {
                while (isActive) {
                    val b = inputStream?.read() ?: break
                    if (b == -1) break
                    buffer.add(b.toByte())
                    if (buffer.size >= 4 && buffer[0] == SppPackage.MAGIC) {
                        val len = ((buffer[1].toInt() and 0xFF) shl 8) or (buffer[2].toInt() and 0xFF)
                        val totalSize = len + 4
                        if (buffer.size >= totalSize) {
                            val data = buffer.take(totalSize).toByteArray()
                            buffer.clear()
                            handlePackage(data)
                        }
                    } else if (buffer.size > 2048) {
                        buffer.clear()
                    }
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                _events.tryEmit(ConnectionEvent.Error(e))
            } finally {
                heartbeatJob?.cancel()
                heartbeatJob = null
                disconnect("Receive loop ended")
            }
        }
    }

    private suspend fun handlePackage(data: ByteArray) {
        try {
            val pkg = parseFromBytes(data)
            val key = pkg.commandId.joinToString("") { "%02x".format(it) }
            val deferred = pending[key]
            if (deferred != null && !deferred.isCompleted) {
                deferred.complete(pkg)
            } else {
                handlerCommands[key]?.invoke(pkg)
                _events.tryEmit(ConnectionEvent.PackageReceived(pkg))
            }
        } catch (e: Exception) {
                DebugLogger.w(TAG, "handlePackage failed for ${data.take(6).joinToString("") { "%02x".format(it) }}: ${e.message}")
            }
    }

    private fun parseFromBytes(data: ByteArray): SppPackage {
        val cmdId = data.copyOfRange(4, 6)
        val pkg = SppPackage(commandId = cmdId)
        val len = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
        var pos = 6
        while (pos + 1 < len + 3) {
            val type = data[pos].toInt() and 0xFF
            val plen = data[pos + 1].toInt() and 0xFF
            if (plen > 0 && pos + 2 + plen <= data.size) {
                pkg.parameters[type] = data.copyOfRange(pos + 2, pos + 2 + plen)
            }
            pos += plen + 2
        }
        return pkg
    }

    fun disconnect(reason: String = "User requested") {
        _state.value = ConnectionState.DISCONNECTING
        heartbeatJob?.cancel()
        heartbeatJob = null
        job?.cancel()
        try { socket?.close() } catch (_: Exception) { }
        _state.value = ConnectionState.DISCONNECTED
        _events.tryEmit(ConnectionEvent.Disconnected(reason))
        pending.values.forEach { it.completeExceptionally(CancellationException(reason)) }
        pending.clear()
    }

    fun destroy() { scope.cancel(); disconnect("Destroyed") }
}