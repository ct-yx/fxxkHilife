package com.freebuds.controller.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freebuds.controller.bluetooth.ScannedDevice
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.data.SavedDeviceConnection
import com.freebuds.controller.i18n.i18n
import com.freebuds.controller.ui.state.ConnectionSummary
import com.freebuds.controller.ui.foundation.components.Material3Card
import com.freebuds.controller.ui.foundation.components.CardIconContainer
import com.freebuds.controller.ui.foundation.components.Material3Banner
import com.freebuds.controller.ui.foundation.components.Material3BannerTone
import com.freebuds.controller.ui.foundation.components.ConnectionBanner
import com.freebuds.controller.ui.foundation.components.AppTopBar
import com.freebuds.controller.ui.foundation.assets.UiAssetCatalog
import com.freebuds.controller.ui.foundation.surface.SurfaceRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DeviceViewModel,
    displayMode: UiDisplayMode,
    onDeviceClick: (address: String) -> Unit,
    onRemoveDevice: (address: String) -> Unit,
    onSettings: () -> Unit,
    onScan: () -> Unit,
) {
    val controlChannelState by viewModel.controlChannelState.collectAsStateWithLifecycle()
    val connection = remember(controlChannelState) { ConnectionSummary.from(controlChannelState) }
    val savedConnections by viewModel.savedDeviceConnections.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(connection.address) {
        viewModel.refreshSavedDeviceConnections()
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            AppTopBar(
                title = i18n("app.name"),
                displayMode = displayMode,
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = i18n("common.settings"))
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = listState,
        ) {
            // 连接摘要只消费稳定 typed state，不再从连接命令返回值猜测页面状态。
            item {
                val summary = connection
                if (summary.systemConnected || summary.stage != com.freebuds.controller.data.ControlChannelStage.Idle) {
                    Spacer(Modifier.height(8.dp))
                    ConnectionBanner(
                        summary = summary,
                        displayMode = displayMode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
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

            if (savedConnections.isEmpty()) {
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
                items(
                    items = savedConnections,
                    key = { it.address },
                ) { connection ->
                    SavedDeviceItem(
                        connection = connection,
                        displayMode = displayMode,
                        onClick = { onDeviceClick(connection.address) },
                        onRemove = { onRemoveDevice(connection.address) }
                    )
                }
            }

            // 扫描卡片 → 导航到独立扫描页
            item {
                Spacer(Modifier.height(8.dp))
                Material3Card(
                    displayMode = displayMode,
                    role = SurfaceRole.FeatureCard,
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
    connection: SavedDeviceConnection,
    displayMode: UiDisplayMode,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val bondedLabel = i18n("home.bonded")

    Material3Card(
        displayMode = displayMode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EarbudsListIcon(connected = connection.isSystemConnected || connection.isControlConnected)
            Column(Modifier.weight(1f)) {
                Text(connection.name, fontWeight = FontWeight.Medium)
                Text(
                    buildString {
                        append(connection.address)
                        if (connection.isBonded) append(" · ").append(bondedLabel)
                        append(" · ")
                        append(
                            when {
                                connection.isControlConnected -> i18n("home.control_connected")
                                connection.isSystemConnected -> i18n("home.system_connected")
                                else -> i18n("home.not_connected")
                            }
                        )
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

@Composable
private fun EarbudsListIcon(connected: Boolean) {
    val color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    CardIconContainer {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(UiAssetCatalog.device(UiAssetCatalog.DeviceVisual.EarbudCase)),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = color,
            )
            if (!connected) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.18f, size.height * 0.84f),
                        end = Offset(size.width * 0.84f, size.height * 0.18f),
                        strokeWidth = 3.2f,
                    )
                }
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
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

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
                    key(device.device.address) {
                        ScanDeviceItem(device) { onConnectClick(device.device) }
                    }
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
                    key(device.device.address) {
                        ScanDeviceItem(device) { onConnectClick(device.device) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanDeviceItem(device: ScannedDevice, onClick: () -> Unit) {
    val bondedLabel = i18n("home.bonded")
    ListItem(
        leadingContent = {
            Icon(
                painter = painterResource(UiAssetCatalog.device(UiAssetCatalog.DeviceVisual.EarbudCase)),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
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
private fun StatusBanner(
    text: String,
    displayMode: UiDisplayMode,
    isError: Boolean = false,
    isConnected: Boolean = false,
) {
    Material3Banner(
        displayMode = displayMode,
        tone = when {
            isError -> Material3BannerTone.Error
            isConnected -> Material3BannerTone.Success
            else -> Material3BannerTone.Info
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
