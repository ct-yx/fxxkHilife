package com.freebuds.controller.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.freebuds.controller.util.LogBuffer

data class ScannedDevice(
    val device: BluetoothDevice,
    val rssi: Int = 0,
    val isBonded: Boolean = false,
    val isHuaweiOrHonor: Boolean = false,
    val isConnected: Boolean = false
) {
    val displayName: String get() = device.name ?: "?"
    val address: String get() = device.address

    companion object {
        private val HUAWEI_PREFIXES = listOf(
            "HUAWEI", "HONOR", "FreeBuds", "Freebuds", "freebuds",
            "华为", "荣耀", "Honor"
        )

        fun isHuaweiOrHonorName(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val upper = name.uppercase()
            return HUAWEI_PREFIXES.any { upper.contains(it.uppercase()) }
        }
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

        // 先列出已配对设备，标注连接状态
        val bonded = adapter.bondedDevices
        if (bonded != null) {
            for (device in bonded) {
                found.add(ScannedDevice(
                    device = device,
                    isBonded = true,
                    isConnected = true,
                    isHuaweiOrHonor = ScannedDevice.isHuaweiOrHonorName(device.name)
                ))
                val tag = if (ScannedDevice.isHuaweiOrHonorName(device.name)) "🔹 " else ""
                LogBuffer.i("Scan", "$tag${device.name}  ${device.address}  [paired+connected]")
            }
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        if (device != null && device.name != null) {
                            found.removeAll { it.device.address == device.address }
                            found.add(ScannedDevice(
                                device = device,
                                rssi = rssi,
                                isBonded = device.bondState != BluetoothDevice.BOND_NONE,
                                isHuaweiOrHonor = ScannedDevice.isHuaweiOrHonorName(device.name)
                            ))
                            val tag = if (ScannedDevice.isHuaweiOrHonorName(device.name)) "🔹 " else ""
                            LogBuffer.i("Scan", "$tag${device.name}  ${device.address}  RSSI:$rssi")
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
        context.registerReceiver(receiver, filter)

        adapter.startDiscovery()
        LogBuffer.i("Scan", "Scanning for devices...")
    }

    fun stopScan() {
        BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
    }
}
