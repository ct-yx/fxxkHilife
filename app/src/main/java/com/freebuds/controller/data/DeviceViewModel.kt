package com.freebuds.controller.data

import android.bluetooth.BluetoothDevice
import android.content.Context
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
    val listeningStats: StateFlow<ListeningStats> = repo.listeningStats

    fun isCoreStateReady(): Boolean = repo.isCoreStateReady()

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

    // ── 分享日志 ──────────────────────────────────────────────────────────────
    fun shareLog(context: Context) = repo.shareLog(context)

    // ── 已保存设备地址 ────────────────────────────────────────────────────────
    fun getSavedAddresses(): List<String> = repo.getSavedAddresses()
    fun getSavedAddress(): String? = repo.getSavedAddress()
    fun removeSavedDevice(address: String) = repo.removeSavedDevice(address)

    // ── 前后台感知 ────────────────────────────────────────────────────────────
    fun setAppInForeground(foreground: Boolean) = repo.setAppInForeground(foreground)

    // ── 自动连接已保存设备 ────────────────────────────────────────────────────
    fun autoConnectSaved(address: String): Boolean = repo.autoConnectSaved(address)

    fun autoConnectLast(context: Context): Boolean = repo.autoConnectLastSaved()

    // ── 扫描 ──────────────────────────────────────────────────────────────────
    fun startScan(context: Context) {
        scanner?.stopScan()
        _scanState.value = ScanState(isScanning = true)
        scanner = BluetoothScanner(context).also { s ->
            s.startScan { success ->
                _scanState.value = ScanState(
                    isScanning = false,
                    devices = if (success) s.found.toList() else emptyList()
                )
                // 扫描完成后，尝试自动连接华为设备
                val huawei = s.found.firstOrNull { it.isHuaweiOrHonor }
                if (huawei != null && repo.connectionState.value !is ConnectionState.Connected) {
                    repo.connect(huawei.device)
                }
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
