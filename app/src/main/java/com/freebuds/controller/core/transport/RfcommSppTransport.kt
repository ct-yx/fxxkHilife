package com.freebuds.controller.core.transport

import android.bluetooth.BluetoothDevice
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method

/**
 * Android RFCOMM/SPP transport.
 *
 * This class deliberately knows nothing about Huawei package format, command ids or handlers.
 * It only opens a BluetoothSocket, writes raw bytes and forwards raw read chunks to the caller.
 *
 * The Huawei command driver sits above this transport so protocols can be layered as:
 *
 *     RfcommSppTransport -> VendorProtocol -> VendorAdapter -> Repository/UI
 */
class RfcommSppTransport(
    private val device: BluetoothDevice,
    val config: RfcommTransportConfig = RfcommTransportConfig.compatibilityFallback(),
    private val onDiscoveryChecked: ((wasDiscovering: Boolean) -> Unit)? = null,
) : EarbudTransport {

    override val id: String = "rfcomm_spp"

    @Volatile
    override var isConnected: Boolean = false
        private set

    val endpointDescription: String get() = config.endpointDescription()

    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var socket: Any? = null
    private var closeMethod: Method? = null
    private var readJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val txMutex = Mutex()
    private val lifecycleLock = Any()
    /**
     * A read loop may outlive the coroutine that started it while a blocking InputStream read is
     * being interrupted.  Generation guards ensure that a late exception from the old loop
     * cannot close or report the newly-created socket (the F reconnect race from the hardware
     * baseline).
     */
    private var lifecycleGeneration = 0L

    private var packetListener: (suspend (ByteArray) -> Unit)? = null
    private var disconnectListener: (() -> Unit)? = null

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (isConnected) return@withContext true
        val generation = beginConnect()
        try {
            LogBuffer.i(
                "Transport",
                "Connecting RFCOMM SPP to ${device.name} (${device.address}) " +
                    "${config.endpointDescription()} timeout=${config.connectTimeoutMs}ms"
            )
            val connectedSocket = try {
                withTimeout(config.connectTimeoutMs) {
                    runInterruptible(Dispatchers.IO) {
                        RfcommSocketBridge.connect(
                            device = device,
                            port = config.channel,
                            logTag = "Transport",
                            onDiscoveryChecked = onDiscoveryChecked,
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                LogBuffer.w(
                    "Transport",
                    "RFCOMM connect timeout after ${config.connectTimeoutMs}ms ${config.endpointDescription()}"
                )
                invalidateGeneration(generation)
                return@withContext false
            }
            val accepted = synchronized(lifecycleLock) {
                if (generation != lifecycleGeneration) {
                    false
                } else {
                    socket = connectedSocket.socket
                    closeMethod = connectedSocket.closeMethod
                    inputStream = connectedSocket.inputStream
                    outputStream = connectedSocket.outputStream
                    isConnected = true
                    readJob = scope.launch {
                        readLoop(generation, connectedSocket.inputStream)
                    }
                    true
                }
            }
            if (!accepted) {
                closeConnectedSocket(connectedSocket)
                return@withContext false
            }
            true
        } catch (e: CancellationException) {
            invalidateGeneration(generation)
            throw e
        } catch (e: Exception) {
            LogBuffer.e("Transport", "RFCOMM SPP connect failed: ${e.message}")
            invalidateGeneration(generation)
            false
        }
    }

    override fun disconnect() {
        synchronized(lifecycleLock) {
            lifecycleGeneration++
            readJob?.cancel()
            readJob = null
            closeSocketLocked()
            isConnected = false
        }
        LogBuffer.i("Transport", "RFCOMM SPP disconnected")
    }

    override suspend fun send(raw: ByteArray) = withContext(Dispatchers.IO) {
        txMutex.withLock {
            val output = outputStream ?: throw IllegalStateException("Transport is not connected")
            output.write(raw)
            output.flush()
        }
    }

    override fun setPacketListener(listener: (suspend (ByteArray) -> Unit)?) {
        packetListener = listener
    }

    override fun setDisconnectListener(listener: (() -> Unit)?) {
        disconnectListener = listener
    }

    private suspend fun readLoop(generation: Long, input: InputStream) {
        try {
            val buffer = ByteArray(DEFAULT_READ_BUFFER_SIZE)
            while (currentCoroutineContext().isActive) {
                val n = input.read(buffer)
                if (n == -1) throw EOFException("RFCOMM stream closed")
                if (n > 0) {
                    val chunk = buffer.copyOf(n)
                    LogBuffer.frame("RX", chunk)
                    packetListener?.invoke(chunk)
                }
            }
        } catch (e: CancellationException) {
            // Intentional disconnects cancel the old reader.  Do not turn that cancellation into
            // a disconnect callback, especially after a replacement generation has connected.
            throw e
        } catch (e: Exception) {
            val shouldNotify = synchronized(lifecycleLock) {
                if (generation != lifecycleGeneration || !isConnected) {
                    false
                } else {
                    isConnected = false
                    readJob = null
                    closeSocketLocked()
                    true
                }
            }
            if (shouldNotify) {
                LogBuffer.w("Transport", "RFCOMM SPP read loop ended: ${e.message}")
                disconnectListener?.invoke()
            }
        }
    }

    private fun beginConnect(): Long = synchronized(lifecycleLock) {
        lifecycleGeneration++
        readJob?.cancel()
        readJob = null
        closeSocketLocked()
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        lifecycleGeneration
    }

    private fun invalidateGeneration(generation: Long) {
        synchronized(lifecycleLock) {
            if (generation != lifecycleGeneration) return
            lifecycleGeneration++
            readJob?.cancel()
            readJob = null
            closeSocketLocked()
            isConnected = false
        }
    }

    private fun closeSocketLocked() {
        runCatching { inputStream?.close() }
        runCatching { outputStream?.close() }
        runCatching { closeMethod?.invoke(socket) }
        inputStream = null
        outputStream = null
        socket = null
        closeMethod = null
    }

    private fun closeConnectedSocket(connected: RfcommSocketBridge.ConnectedSocket) {
        runCatching { connected.inputStream.close() }
        runCatching { connected.outputStream.close() }
        runCatching { connected.closeMethod.invoke(connected.socket) }
    }

    companion object {
        private const val DEFAULT_READ_BUFFER_SIZE = 4096
    }
}
