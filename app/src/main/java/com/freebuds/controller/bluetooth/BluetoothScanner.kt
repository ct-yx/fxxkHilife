package com.freebuds.controller.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.freebuds.controller.util.LogBuffer

class BluetoothScanner(private val context: Context) {

    private var callback: ((List<BluetoothDevice>) -> Unit)? = null
    private val found = mutableSetOf<BluetoothDevice>()
    private var receiver: BroadcastReceiver? = null

    fun startScan(callback: (List<BluetoothDevice>) -> Unit) {
        this.callback = callback
        found.clear()

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            LogBuffer.w("Scan", "No Bluetooth adapter")
            return
        }
        if (!adapter.isEnabled) {
            LogBuffer.w("Scan", "Bluetooth is disabled")
            return
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (device != null && device.name != null) {
                            found.add(device)
                            LogBuffer.i("Scan", "Found: ${device.name} [${device.address}]")
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        LogBuffer.i("Scan", "Scan finished, ${found.size} devices found")
                        callback(found.toList())
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
