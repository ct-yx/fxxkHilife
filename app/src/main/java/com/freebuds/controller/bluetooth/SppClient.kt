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
}

class SppClient(private val device: BluetoothDevice) : ISppClient {
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var job: Job? = null
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
            } finally {
                pending.remove(key)
            }
        } else {
            outputStream?.write(pkg.toBytes())
            outputStream?.flush()
            return null
        }
    }

    override fun registerHandler(cmdId: ByteArray, handler: suspend (SppPackage) -> Unit) {
        val key = cmdId.joinToString("") { "%02x".format(it) }
        handlerCommands[key] = handler
    }

    private fun startReceiveLoop() {
        job = scope.launch {
            val buffer = mutableListOf<Byte>()
            try {
                while (isActive) {
                    val b = inputStream?.read() ?: break
                    if (b == -1) break
                    buffer.add(b.toByte())
                    if (buffer.size >= 4 && buffer[0] == 0x5A.toByte()) {
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
        } catch (_: Exception) { }
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
        job?.cancel()
        try { socket?.close() } catch (_: Exception) { }
        _state.value = ConnectionState.DISCONNECTED
        _events.tryEmit(ConnectionEvent.Disconnected(reason))
        pending.values.forEach { it.completeExceptionally(CancellationException(reason)) }
        pending.clear()
    }

    fun destroy() { scope.cancel(); disconnect("Destroyed") }
}
