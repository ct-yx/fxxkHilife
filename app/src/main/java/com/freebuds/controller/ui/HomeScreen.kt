package com.freebuds.controller.ui

import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freebuds.controller.bluetooth.ScannedDevice
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.i18n.i18n
import com.freebuds.controller.ui.glass.AdaptiveCard
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DeviceViewModel,
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    onDeviceClick: (address: String) -> Unit,
    onRemoveDevice: (address: String) -> Unit,
    onSettings: () -> Unit,
    onScan: () -> Unit,
) {
    val context = LocalContext.current
    val connState by viewModel.connectionState.collectAsState()
    // 使用 key 强制重组：每次连接状态变化都刷新已保存设备列表
    var savedAddresses by remember { mutableStateOf(viewModel.getSavedAddresses()) }
    // 订阅连接状态变化来刷新列表
    LaunchedEffect(connState) {
        savedAddresses = viewModel.getSavedAddresses()
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(i18n("app.name")) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = i18n("common.settings"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (displayMode == UiDisplayMode.LIQUID_GLASS) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 连接状态横幅
            item {
                when (val s = connState) {
                    is ConnectionState.Connected -> {
                        StatusBanner(i18n("scan.connected_to", s.deviceName), isConnected = true)
                    }
                    is ConnectionState.Connecting -> {
                        StatusBanner(i18n("scan.connecting_to", s.deviceName))
                    }
                    is ConnectionState.Failed -> {
                        StatusBanner(i18n("scan.connection_failed", s.reason), isError = true)
                    }
                    else -> {}
                }
            }

            // 已保存设备列表
            item {
                Text(
                    i18n("home.saved_devices"),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item { HorizontalDivider() }

            if (savedAddresses.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(i18n("home.empty_saved_devices"), style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                i18n("home.saved_after_connect"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            } else {
                items(savedAddresses) { addr ->
                    SavedDeviceItem(
                        address = addr,
                        adapter = BluetoothAdapter.getDefaultAdapter(),
                        displayMode = displayMode,
                        hazeState = hazeState,
                        onClick = { onDeviceClick(addr) },
                        onRemove = { onRemoveDevice(addr) }
                    )
                }
            }

            // 扫描卡片 → 导航到独立扫描页
            item {
                Spacer(Modifier.height(8.dp))
                AdaptiveCard(
                    displayMode = displayMode,
                    hazeState = hazeState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = onScan),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(i18n("home.scan_new_device"), style = MaterialTheme.typography.titleSmall)
                            Text(
                                i18n("home.scan_nearby_huawei"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// 已保存设备列表项
// ============================================================
@Composable
private fun SavedDeviceItem(
    address: String,
    adapter: BluetoothAdapter?,
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val device = remember(address) { adapter?.getRemoteDevice(address) }
    val name = device?.name ?: address
    val bondedLabel = i18n("home.bonded")
    val isBonded = remember(address) {
        try { device?.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED } catch (_: Exception) { false }
    }

    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Default.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Medium)
                Text(
                    buildString {
                        append(address)
                        if (isBonded) append(" · ").append(bondedLabel)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = i18n("common.delete"), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ============================================================
// 扫描子面板（折叠区内）
// ============================================================
@Composable
private fun ScanSection(
    viewModel: DeviceViewModel,
    context: Context,
    onConnectClick: (android.bluetooth.BluetoothDevice) -> Unit,
) {
    val scanState by viewModel.scanState.collectAsState()

    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        // 扫描按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                i18n("scan.nearby_devices"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (scanState.isScanning) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(i18n("common.scanning"), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                TextButton(onClick = { viewModel.startScan(context) }) {
                    Text(i18n("home.start_scan"))
                }
            }
        }

        if (scanState.devices.isEmpty() && !scanState.isScanning) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    i18n("home.tap_start_scan"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        } else {
            val huawei = scanState.devices.filter { it.isHuaweiOrHonor }
            val others = scanState.devices.filter { !it.isHuaweiOrHonor }

            if (huawei.isNotEmpty()) {
                Text(
                    i18n("scan.huawei_honor_devices"),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                huawei.forEach { device ->
                    ScanDeviceItem(device) { onConnectClick(device.device) }
                }
            }
            if (others.isNotEmpty()) {
                Text(
                    i18n("scan.other_devices"),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                others.forEach { device ->
                    ScanDeviceItem(device) { onConnectClick(device.device) }
                }
            }
        }
    }
}

@Composable
private fun ScanDeviceItem(device: ScannedDevice, onClick: () -> Unit) {
    val bondedLabel = i18n("home.bonded")
    ListItem(
        headlineContent = { Text(device.displayName, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text(
                buildString {
                    append(device.address)
                    if (device.isBonded) append(" · ").append(bondedLabel)
                    if (device.rssi != 0) append(" · ${device.rssi} dBm")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
}

// ============================================================
// 状态横幅（复用）
// ============================================================
@Composable
private fun StatusBanner(text: String, isError: Boolean = false, isConnected: Boolean = false) {
    Surface(
        color = when {
            isError -> MaterialTheme.colorScheme.errorContainer
            isConnected -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
