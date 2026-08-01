package com.freebuds.controller.core.transport

import android.bluetooth.BluetoothDevice
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val port: Int = DEFAULT_SPP_PORT,
    private val onDiscoveryChecked: ((wasDiscovering: Boolean) -> Unit)? = null,
) : EarbudTransport {

    override val id: String = "rfcomm_spp"

    @Volatile
    override var isConnected: Boolean = false
        private set

    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var socket: Any? = null
    private var closeMethod: Method? = null
    private var readJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val txMutex = Mutex()

    private var packetListener: (suspend (ByteArray) -> Unit)? = null
    private var disconnectListener: (() -> Unit)? = null

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (isConnected) return@withContext true
        try {
            LogBuffer.i("Transport", "Connecting RFCOMM SPP to ${device.name} (${device.address}) port=$port")
            scope.cancel()
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val connectedSocket = RfcommSocketBridge.connect(
                device = device,
                port = port,
                logTag = "Transport",
                onDiscoveryChecked = onDiscoveryChecked,
            )
            socket = connectedSocket.socket
            closeMethod = connectedSocket.closeMethod
            inputStream = connectedSocket.inputStream
            outputStream = connectedSocket.outputStream
            isConnected = true
            readJob = scope.launch { readLoop() }
            true
        } catch (e: Exception) {
            LogBuffer.e("Transport", "RFCOMM SPP connect failed: ${e.message}")
            closeSocket()
            isConnected = false
            false
        }
    }

    override fun disconnect() {
        readJob?.cancel()
        closeSocket()
        isConnected = false
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

    private suspend fun readLoop() {
        try {
            val input = inputStream ?: return
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
        } catch (e: Exception) {
            if (isConnected) {
                LogBuffer.w("Transport", "RFCOMM SPP read loop ended: ${e.message}")
                isConnected = false
                closeSocket()
                disconnectListener?.invoke()
            }
        }
    }

    private fun closeSocket() {
        runCatching { inputStream?.close() }
        runCatching { outputStream?.close() }
        runCatching { closeMethod?.invoke(socket) }
        inputStream = null
        outputStream = null
        socket = null
        closeMethod = null
    }

    companion object {
        const val DEFAULT_SPP_PORT = 1
        private const val DEFAULT_READ_BUFFER_SIZE = 4096
    }
}
