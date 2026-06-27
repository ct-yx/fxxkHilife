package com.freebuds.controller.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import com.freebuds.controller.protocol.HuaweiSppPackage
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.coroutines.resume

/**
 * BLE GATT 蓝牙驱动
 * 参照 OpenFreebuds OfbDriverHuaweiGeneric 的 Handler/包分发体系，
 * 底层用 Android BLE GATT 替代 RFCOMM SPP。
 *
 * 自动发现服务：找到第一个支持 NOTIFY + WRITE 的特征值作为数据通道。
 */
class GattDriver(private val device: BluetoothDevice) {

    companion object {
        // 华为 BLE 控制 Service UUID（优先匹配）
        private val HUAWEI_SERVICE_UUIDS = listOf(
            UUID.fromString("0000febf-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000fee0-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000fd00-0000-1000-8000-00805f9b34fb"),
        )
        private const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"
    }

    var isConnected: Boolean = false
        private set

    private var gatt: BluetoothGatt? = null
    private var txChar: BluetoothGattCharacteristic? = null  // WRITE
    private var rxChar: BluetoothGattCharacteristic? = null  // NOTIFY
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectJob: Job? = null

    // 待响应映射: responseId (hex) -> CompletableDeferred
    private val pendingResponses = mutableMapOf<String, CompletableDeferred<HuaweiSppPackage>>()
    private val pendingMutex = Mutex()

    // 已注册的 Handler
    private val handlers = mutableListOf<HuaweiDeviceHandler>()
    private val packageHandlers = mutableMapOf<String, HuaweiDeviceHandler>()

    // 连接结果桥接
    private var connectDeferred: CompletableDeferred<Boolean>? = null

    // 服务发现结果桥接
    private var discoverDeferred: CompletableDeferred<Boolean>? = null

    // 接收到的数据缓冲（来自 onCharacteristicChanged）
    private val rxBuffer = mutableListOf<Byte>()

    fun registerHandler(handler: HuaweiDeviceHandler) {
        handlers.add(handler)
        for (cmd in handler.commandIds) {
            packageHandlers[cmd.toHex()] = handler
        }
    }

    /** 发起 BLE GATT 连接 */
    suspend fun connect(): Boolean = suspendCancellableCoroutine { cont ->
        if (isConnected) {
            LogBuffer.i("Gatt", "Already connected, skipping")
            cont.resume(true)
            return@suspendCancellableCoroutine
        }

        LogBuffer.i("Gatt", ">> connect(): ${device.name} (${device.address}) via TRANSPORT_LE")
        connectDeferred = CompletableDeferred()
        connectJob = scope.launch {
            val result = connectDeferred?.await() ?: false
            LogBuffer.i("Gatt", "<< connection callback result=$result")
            if (result) {
                LogBuffer.i("Gatt", ">> discoverServices()...")
                discoverDeferred = CompletableDeferred()
                gatt?.discoverServices()
                val discResult = discoverDeferred?.await() ?: false
                val svcCount = gatt?.services?.size ?: 0
                LogBuffer.i("Gatt", "<< services discovered=$discResult ($svcCount services)")
                if (discResult) {
                    findCharacteristics()
                    if (txChar != null && rxChar != null) {
                        LogBuffer.i("Gatt", ">> enabling notifications on ${rxChar?.uuid}...")
                        if (enableNotifications()) {
                            isConnected = true
                            job = scope.launch { recvLoop() }
                            initHandlers()
                            LogBuffer.i("Gatt", ">> connect() complete, fully operational")
                            cont.resume(true)
                            return@launch
                        } else {
                            LogBuffer.e("Gatt", "Failed to enable notifications")
                        }
                    } else {
                        LogBuffer.e("Gatt", "No TX/RX char found (tx=${txChar != null}, rx=${rxChar != null})")
                        gatt?.services?.forEach { svc ->
                            val chars = svc.characteristics.map { "${it.uuid} props=0x${it.properties.toString(16)}" }
                            LogBuffer.i("Gatt", "  Svc ${svc.uuid}: $chars")
                        }
                    }
                } else {
                    LogBuffer.e("Gatt", "Service discovery failed")
                }
            } else {
                LogBuffer.e("Gatt", "BLE GATT connection failed")
            }
            cont.resume(false)
        }

        LogBuffer.i("Gatt", ">> calling connectGatt(TRANSPORT_LE)...")
        gatt = device.connectGatt(
            /* context = */ null,
            /* autoReconnect = */ false,
            /* callback = */ gattCallback,
            /* transport = */ BluetoothDevice.TRANSPORT_LE
        )
        LogBuffer.i("Gatt", ">> connectGatt returned gatt=${gatt != null}")
    }

    /** 初始化所有 Handler（参照 OpenFreebuds _start_all_handlers） */
    private suspend fun initHandlers() {
        for (handler in handlers) {
            try {
                withTimeout(5000) {
                    handler.onInit(this@GattDriver)
                }
                LogBuffer.i("GattDriver", "Init ${handler.id} success")
            } catch (e: Exception) {
                LogBuffer.w("GattDriver", "Init ${handler.id} failed: ${e.message}")
            }
        }
    }

    /** 发送包并等响应 */
    suspend fun sendPackage(pkg: HuaweiSppPackage, timeout: Long = 5000): HuaweiSppPackage? {
        val respId = pkg.responseId.toHex()
        if (respId.isEmpty()) {
            sendNowait(pkg)
            return null
        }

        val deferred = CompletableDeferred<HuaweiSppPackage>()
        pendingMutex.withLock {
            pendingResponses[respId]?.cancel()
            pendingResponses[respId] = deferred
        }

        try {
            sendNowait(pkg)
            return withTimeout(timeout) { deferred.await() }
        } catch (e: Exception) {
            LogBuffer.w("Gatt", "Timeout waiting for response to cmd=${pkg.commandId.toHex()}")
            return null
        } finally {
            pendingMutex.withLock { pendingResponses.remove(respId) }
        }
    }

    /** 发送包不等待 */
    suspend fun sendNowait(pkg: HuaweiSppPackage) = withContext(Dispatchers.IO) {
        val bytes = pkg.toBytes()
        LogBuffer.d("Gatt", "TX: cmd=${pkg.commandId.toHex()}")
        try {
            val char = txChar ?: throw IllegalStateException("TX characteristic not ready")
            char.value = bytes
            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt?.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    ?: BluetoothStatusCodes.ERROR_UNKNOWN
            } else {
                @Suppress("DEPRECATION")
                gatt?.writeCharacteristic(char) ?: false
            }
            if (success != BluetoothStatusCodes.SUCCESS && success != true) {
                LogBuffer.e("Gatt", "TX write failed")
            }
        } catch (e: Exception) {
            LogBuffer.e("Gatt", "TX send failed: ${e.message}")
            throw e
        }
    }

    /** 接收循环：从 rxBuffer 中组装 5A 包 */
    private suspend fun recvLoop() {
        try {
            while (currentCoroutineContext().isActive) {
                delay(50) // 轮询等待
                if (rxBuffer.isEmpty()) continue
                processIncoming()
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                LogBuffer.e("Gatt", "Recv loop error: ${e.message}")
            }
        }
    }

    /** 从缓冲区解析并分发 5A 包 */
    private suspend fun processIncoming() {
        val buf = rxBuffer.toMutableList()
        rxBuffer.clear()

        while (buf.size >= 7) {
            if (buf[0] != 0x5A.toByte()) {
                buf.removeAt(0)
                continue
            }
            val len = ((buf[1].toInt() and 0xFF) shl 8) or (buf[2].toInt() and 0xFF)
            val pktSize = len + 4
            if (buf.size < pktSize) {
                // 数据不完整，放回
                rxBuffer.addAll(buf)
                return
            }

            val pktBytes = buf.take(pktSize).toByteArray()
            buf.removeAll(buf.take(pktSize))
            handlePackage(pktBytes)
        }
        // 残余数据放回
        if (buf.isNotEmpty()) rxBuffer.addAll(buf)
    }

    /** 处理收到的包（参照 OpenFreebuds _handle_raw_pkg） */
    private suspend fun handlePackage(data: ByteArray) {
        val pkg = HuaweiSppPackage.fromBytes(data) ?: return
        val cmdKey = pkg.commandId.toHex()
        LogBuffer.d("Gatt", "RX: $pkg")

        pendingMutex.withLock {
            val deferred = pendingResponses[cmdKey]
            if (deferred != null && !deferred.isCompleted) {
                deferred.complete(pkg)
                return
            }
        }

        val handler = packageHandlers[cmdKey]
        if (handler != null) {
            handler.onPackage(pkg)
        } else {
            LogBuffer.d("Gatt", "No handler for cmd=${pkg.commandId.toHex()}")
        }
    }

    fun disconnect() {
        job?.cancel()
        connectJob?.cancel()
        scope.cancel()
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {}
        gatt = null
        txChar = null
        rxChar = null
        isConnected = false
        rxBuffer.clear()
        LogBuffer.i("Gatt", "Disconnected")
    }

    // ——— 私有帮助方法 ———

    /** 搜索特征值：优先华为 Service，否则回退到全量搜索 */
    private fun findCharacteristics() {
        val services = gatt?.services ?: return

        // 先尝试华为预设 Service
        for (uuid in HUAWEI_SERVICE_UUIDS) {
            val svc = services.find { it.uuid == uuid }
            if (svc != null) {
                for (char in svc.characteristics) {
                    val props = char.properties
                    if (txChar == null && (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                        txChar = char
                    }
                    if (rxChar == null && (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        rxChar = char
                    }
                }
                if (txChar != null && rxChar != null) {
                    LogBuffer.i("Gatt", "Found HW service $uuid: TX=${txChar?.uuid} RX=${rxChar?.uuid}")
                    return
                }
            }
        }

        // 回退：遍历所有 Service
        LogBuffer.i("Gatt", "Huawei service not found, scanning all services...")
        for (svc in services) {
            for (char in svc.characteristics) {
                val props = char.properties
                if (txChar == null && (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                    txChar = char
                }
                if (rxChar == null && (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    rxChar = char
                }
            }
        }

        if (txChar != null) LogBuffer.i("Gatt", "TX char: ${txChar?.uuid} on service ${txChar?.service?.uuid}")
        if (rxChar != null) LogBuffer.i("Gatt", "RX char: ${rxChar?.uuid} on service ${rxChar?.service?.uuid}")
    }

    /** 订阅 NOTIFY 特征值 */
    private fun enableNotifications(): Boolean {
        val char = rxChar ?: return false
        val gatt = gatt ?: return false

        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.setCharacteristicNotification(char, true)
        } else {
            @Suppress("DEPRECATION")
            gatt.setCharacteristicNotification(char, true)
        }
        if (!success) {
            LogBuffer.e("Gatt", "Failed to set notification")
            return false
        }

        // 写 CCCD
        val descriptor = char.getDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID))
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

        LogBuffer.i("Gatt", "Notifications enabled on ${char.uuid}")
        return true
    }

    /** GATT 回调 */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            LogBuffer.i("Gatt", "onConnectionStateChange: status=$status newState=$newState (CONNECTED=${BluetoothProfile.STATE_CONNECTED} DISCONNECTED=${BluetoothProfile.STATE_DISCONNECTED})")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    LogBuffer.i("Gatt", "Connected to ${device.name}")
                    connectDeferred?.complete(true)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    LogBuffer.i("Gatt", "Disconnected from ${device.name}")
                    isConnected = false
                    connectDeferred?.complete(false)
                    disconnect()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                LogBuffer.i("Gatt", "Services discovered")
                discoverDeferred?.complete(true)
            } else {
                LogBuffer.w("Gatt", "Service discovery failed: $status")
                discoverDeferred?.complete(false)
            }
        }

        @Deprecated("Deprecated in Java", ReplaceWith("onCharacteristicChanged(gatt, characteristic, value)"))
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            rxBuffer.addAll(value.toList())
            LogBuffer.d("Gatt", "RX raw: ${value.size} bytes")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            rxBuffer.addAll(value.toList())
            LogBuffer.d("Gatt", "RX raw: ${value.size} bytes")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                LogBuffer.w("Gatt", "Write failed: $status")
            }
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
