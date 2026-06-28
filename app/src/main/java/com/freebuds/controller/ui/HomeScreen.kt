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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DeviceViewModel,
    onDeviceClick: (address: String) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val connState by viewModel.connectionState.collectAsState()
    val savedAddresses by remember { mutableStateOf(viewModel.getSavedAddresses()) }

    var showScan by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("fxxkHilife") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
                        StatusBanner("已连接 ${s.deviceName}", isConnected = true)
                    }
                    is ConnectionState.Connecting -> {
                        StatusBanner("正在连接 ${s.deviceName}…")
                    }
                    is ConnectionState.Failed -> {
                        StatusBanner("连接失败：${s.reason}", isError = true)
                    }
                    else -> {}
                }
            }

            // 已保存设备列表
            item {
                Text(
                    "已保存的设备",
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
                            Text("尚未保存任何设备", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "扫描并连接后会自动保存",
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
                        onClick = { onDeviceClick(addr) }
                    )
                }
            }

            // 扫描折叠区
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showScan = !showScan }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "扫描新设备",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        if (showScan) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showScan) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                HorizontalDivider()
            }

            item {
                AnimatedVisibility(visible = showScan) {
                    ScanSection(
                        viewModel = viewModel,
                        context = context,
                        onConnectClick = { device ->
                            viewModel.connect(device)
                        }
                    )
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
    onClick: () -> Unit,
) {
    val device = remember(address) { adapter?.getRemoteDevice(address) }
    val name = device?.name ?: address
    val isBonded = remember(address) {
        try { device?.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED } catch (_: Exception) { false }
    }

    ListItem(
        headlineContent = { Text(name, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text(
                buildString {
                    append(address)
                    if (isBonded) append(" · 已配对")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        },
        leadingContent = {
            Icon(
                Icons.Default.Headphones,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
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
                "附近设备",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (scanState.isScanning) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("扫描中", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                TextButton(onClick = { viewModel.startScan(context) }) {
                    Text("开始扫描")
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
                    "点击\"开始扫描\"发现附近设备",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        } else {
            val huawei = scanState.devices.filter { it.isHuaweiOrHonor }
            val others = scanState.devices.filter { !it.isHuaweiOrHonor }

            if (huawei.isNotEmpty()) {
                Text(
                    "华为 / 荣耀设备",
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
                    "其他设备",
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
    ListItem(
        headlineContent = { Text(device.displayName, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text(
                buildString {
                    append(device.address)
                    if (device.isBonded) append(" · 已配对")
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
