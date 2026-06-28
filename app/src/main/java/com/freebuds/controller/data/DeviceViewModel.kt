package com.freebuds.controller.data

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freebuds.controller.HilifeApplication
import com.freebuds.controller.bluetooth.BluetoothScanner
import com.freebuds.controller.bluetooth.ScannedDevice
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ScanState(
    val isScanning: Boolean = false,
    val devices: List<ScannedDevice> = emptyList(),
)

class DeviceViewModel : ViewModel() {

    private val repo = HilifeApplication.instance.deviceRepository

    // ── 透传 Repository 的 Flow ───────────────────────────────────────────────
    val connectionState: StateFlow<ConnectionState> = repo.connectionState
    val props: StateFlow<DeviceProps> = repo.props

    // ── 扫描状态（只在 ViewModel 层维护，与连接无关） ─────────────────────────
    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private var scanner: BluetoothScanner? = null

    // ── 连接操作 ──────────────────────────────────────────────────────────────
    fun connect(device: BluetoothDevice) = repo.connect(device)
    fun disconnect() = repo.disconnect()

    // ── 属性写入 ──────────────────────────────────────────────────────────────
    fun setProperty(group: String, prop: String, value: String) {
        viewModelScope.launch { repo.setProperty(group, prop, value) }
    }

    // ── 扫描 ──────────────────────────────────────────────────────────────────
    fun startScan(context: android.content.Context) {
        scanner?.stopScan()
        _scanState.value = ScanState(isScanning = true)
        scanner = BluetoothScanner(context).also { s ->
            s.startScan { success ->
                _scanState.value = ScanState(
                    isScanning = false,
                    devices = if (success) s.found.toList() else emptyList()
                )
            }
            // 立即同步已配对设备
            _scanState.value = _scanState.value.copy(devices = s.found.toList())
        }
    }

    fun stopScan() {
        scanner?.stopScan()
        scanner = null
        _scanState.value = _scanState.value.copy(isScanning = false)
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}
