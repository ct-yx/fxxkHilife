package com.freebuds.controller.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.freebuds.controller.ui.glass.AdaptiveCard
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: DeviceViewModel,
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    onBack: () -> Unit,
    onDeviceSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    val scanState by viewModel.scanState.collectAsState()
    val connState by viewModel.connectionState.collectAsState()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("扫描设备") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (displayMode == UiDisplayMode.LIQUID_GLASS) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 已连接提示（返回扫描页时不断连）
            if (connState is ConnectionState.Connected) {
                StatusBanner("已连接 ${(connState as ConnectionState.Connected).deviceName}",
                    isConnected = true)
            }
            if (connState is ConnectionState.Connecting) {
                StatusBanner("正在连接 ${(connState as ConnectionState.Connecting).deviceName}…")
            }
            if (connState is ConnectionState.Failed) {
                StatusBanner("连接失败：${(connState as ConnectionState.Failed).reason}", isError = true)
            }

            // 扫描按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "附近设备",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (scanState.isScanning) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("扫描中", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    TextButton(onClick = { viewModel.startScan(context) }) { Text("重新扫描") }
                }
            }

            HorizontalDivider()

            if (scanState.devices.isEmpty() && !scanState.isScanning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("未发现设备", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                val huawei = scanState.devices.filter { it.isHuaweiOrHonor }
                val others = scanState.devices.filter { !it.isHuaweiOrHonor }

                LazyColumn {
                    if (huawei.isNotEmpty()) {
                        item {
                            SectionHeader("华为 / 荣耀设备")
                        }
                        items(huawei) { DeviceItem(it, displayMode, hazeState) { onDeviceSelected(it.device.address) } }
                    }
                    if (others.isNotEmpty()) {
                        item { SectionHeader("其他设备") }
                        items(others) { DeviceItem(it, displayMode, hazeState) { onDeviceSelected(it.device.address) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
}

@Composable
private fun DeviceItem(device: ScannedDevice, displayMode: UiDisplayMode, hazeState: HazeState?, onClick: () -> Unit) {
    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Text(device.displayName, fontWeight = FontWeight.Medium)
        Text(
            buildString {
                append(device.address)
                if (device.isBonded) append(" · 已配对")
                if (device.rssi != 0) append(" · ${device.rssi} dBm")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
    }
}

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
