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
    private val connectionManager = repo.connectionManager

    // ── 透传 Repository 的 Flow ───────────────────────────────────────────────
    val connectionState: StateFlow<ConnectionState> = repo.connectionState
    val controlChannelState: StateFlow<ControlChannelState> = repo.controlChannelState
    val props: StateFlow<DeviceProps> = repo.props
    val listeningStats: StateFlow<ListeningStats> = repo.listeningStats
    val savedDeviceConnections: StateFlow<List<SavedDeviceConnection>> = repo.savedDeviceConnections

    fun isCoreStateReady(): Boolean = repo.isCoreStateReady()

    // ── 扫描状态（只在 ViewModel 层维护，与连接无关） ─────────────────────────
    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private var scanner: BluetoothScanner? = null

    // ── 连接操作 ──────────────────────────────────────────────────────────────
    fun connect(device: BluetoothDevice): String? =
        (connectionManager.submit(ConnectionCommand.Connect(device)) as ConnectionCommandResult.Attempt).attemptId

    fun disconnect() {
        connectionManager.submit(ConnectionCommand.Disconnect)
    }

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
    fun refreshSavedDeviceConnections() {
        connectionManager.submit(ConnectionCommand.RefreshSavedDeviceConnections)
    }

    // ── 前后台感知 ────────────────────────────────────────────────────────────
    fun setAppInForeground(foreground: Boolean) = repo.setAppInForeground(foreground)

    // ── 自动连接已保存设备 ────────────────────────────────────────────────────
    fun autoConnectSaved(address: String): Boolean =
        (connectionManager.submit(ConnectionCommand.AutoConnectSaved(address)) as ConnectionCommandResult.Accepted).value

    fun autoConnectLast(context: Context): Boolean =
        (connectionManager.submit(ConnectionCommand.AutoConnectLastSaved()) as ConnectionCommandResult.Accepted).value

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
                    connectionManager.submit(
                        ConnectionCommand.Connect(huawei.device, ConnectionTrigger.ScanCompleted)
                    )
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
