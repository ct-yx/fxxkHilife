package com.freebuds.controller.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.freebuds.controller.adapter.huawei.HuaweiOpenFreebudsAdapter
import com.freebuds.controller.util.LogBuffer

data class ScannedDevice(
    val device: BluetoothDevice,
    val rssi: Int = 0,
    val isBonded: Boolean = false,
    val isHuaweiOrHonor: Boolean = false,
    val isConnected: Boolean = false,
) {
    val displayName: String get() = runCatching { device.name }.getOrNull() ?: "?"
    val address: String get() = runCatching { device.address }.getOrNull() ?: ""

    companion object {
        fun isHuaweiOrHonorName(name: String?): Boolean = HuaweiOpenFreebudsAdapter.isHuaweiOrHonorName(name)
    }
}

class BluetoothScanner(private val context: Context) {
    private var callback: ((Boolean) -> Unit)? = null
    val found = mutableListOf<ScannedDevice>()
    private var receiver: BroadcastReceiver? = null

    fun startScan(complete: (Boolean) -> Unit) {
        this.callback = complete
        found.clear()

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            LogBuffer.w("Scan", "No Bluetooth adapter")
            complete(false)
            return
        }
        if (!adapter.isEnabled) {
            LogBuffer.w("Scan", "Bluetooth is disabled")
            complete(false)
            return
        }

        // 先列出已配对设备，标注真实系统连接状态。
        val connectedAddresses = connectedSystemAddresses()
        val bonded = runCatching { adapter.bondedDevices }.getOrNull()
        if (bonded != null) {
            for (device in bonded) {
                val address = safeAddress(device)
                val name = safeName(device)
                val isConnected = address in connectedAddresses || isDeviceConnected(device)
                found.add(
                    ScannedDevice(
                        device = device,
                        isBonded = true,
                        isConnected = isConnected,
                        isHuaweiOrHonor = ScannedDevice.isHuaweiOrHonorName(name),
                    )
                )
                val tag = if (ScannedDevice.isHuaweiOrHonorName(name)) "🔹 " else ""
                val state = if (isConnected) "paired+connected" else "paired"
                LogBuffer.i("Scan", "$tag$name  $address  [$state]")
            }
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        val name = device?.let { safeName(it) }
                        val address = device?.let { safeAddress(it) }
                        if (device != null && !name.isNullOrBlank() && !address.isNullOrBlank()) {
                            found.removeAll { it.address == address }
                            val isConnected = address in connectedAddresses || isDeviceConnected(device)
                            found.add(
                                ScannedDevice(
                                    device = device,
                                    rssi = rssi,
                                    isBonded = safeBondState(device) != BluetoothDevice.BOND_NONE,
                                    isConnected = isConnected,
                                    isHuaweiOrHonor = ScannedDevice.isHuaweiOrHonorName(name),
                                )
                            )
                            val tag = if (ScannedDevice.isHuaweiOrHonorName(name)) "🔹 " else ""
                            LogBuffer.i("Scan", "$tag$name  $address  RSSI:$rssi")
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        LogBuffer.i("Scan", "Scan finished, ${found.size} devices found")
                        complete(true)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        try {
            context.registerReceiver(receiver, filter)
            if (!adapter.startDiscovery()) {
                LogBuffer.w("Scan", "Bluetooth discovery did not start")
                stopScan()
                complete(false)
                return
            }
            LogBuffer.i("Scan", "Scanning for devices...")
        } catch (e: SecurityException) {
            LogBuffer.e("Scan", "Bluetooth scan permission denied: ${e.message}")
            stopScan()
            complete(false)
        }
    }

    fun stopScan() {
        runCatching { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() }
        receiver?.let { r -> runCatching { context.unregisterReceiver(r) } }
        receiver = null
    }

    private fun connectedSystemAddresses(): Set<String> {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return emptySet()
        return buildSet {
            addAll(runCatching { manager.getConnectedDevices(BluetoothProfile.HEADSET).mapNotNull { safeAddress(it).ifBlank { null } } }.getOrDefault(emptyList()))
            addAll(runCatching { manager.getConnectedDevices(BluetoothProfile.A2DP).mapNotNull { safeAddress(it).ifBlank { null } } }.getOrDefault(emptyList()))
        }
    }

    private fun safeName(device: BluetoothDevice): String = runCatching { device.name }.getOrNull().orEmpty()

    private fun safeAddress(device: BluetoothDevice): String = runCatching { device.address }.getOrNull().orEmpty()

    private fun safeBondState(device: BluetoothDevice): Int = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)

    private fun isDeviceConnected(device: BluetoothDevice): Boolean = runCatching {
        val method = device.javaClass.getMethod("isConnected")
        method.invoke(device) as? Boolean == true
    }.getOrDefault(false)
}
