package com.freebuds.controller.ui

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freebuds.controller.bluetooth.ScannedDevice
import com.freebuds.controller.data.ControlChannelStage
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.i18n.i18n
import com.freebuds.controller.ui.foundation.components.Material3Card
import com.freebuds.controller.ui.foundation.components.Material3Banner
import com.freebuds.controller.ui.foundation.components.Material3BannerTone
import com.freebuds.controller.ui.foundation.components.AppTopBar
import com.freebuds.controller.ui.foundation.assets.UiAssetCatalog
import com.freebuds.controller.ui.state.ConnectionSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: DeviceViewModel,
    displayMode: UiDisplayMode,
    onBack: () -> Unit,
    onDeviceSelected: (BluetoothDevice) -> Unit,
) {
    val context = LocalContext.current
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val controlChannelState by viewModel.controlChannelState.collectAsStateWithLifecycle()
    val connection = remember(controlChannelState) { ConnectionSummary.from(controlChannelState) }
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            AppTopBar(
                title = i18n("scan.title"),
                displayMode = displayMode,
                onBack = onBack,
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 已连接提示（返回扫描页时不断连）
            when (connection.stage) {
                ControlChannelStage.Ready, ControlChannelStage.Degraded -> {
                    connection.deviceName?.let { name ->
                        StatusBanner(
                            i18n("scan.connected_to", name),
                            displayMode = displayMode,
                            isConnected = true,
                        )
                    }
                }
                ControlChannelStage.Failed -> {
                    StatusBanner(
                        i18n("scan.connection_failed", connection.reason ?: connection.stageLabel),
                        displayMode,
                        isError = true,
                    )
                }
                ControlChannelStage.Idle -> Unit
                else -> {
                    connection.deviceName?.let { name ->
                        StatusBanner(i18n("scan.connecting_to", name), displayMode)
                    }
                }
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
                    i18n("scan.nearby_devices"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (scanState.isScanning) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(i18n("common.scanning"), style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    TextButton(onClick = { viewModel.startScan(context) }) { Text(i18n("scan.rescan")) }
                }
            }

            HorizontalDivider()

            if (scanState.devices.isEmpty() && !scanState.isScanning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(i18n("scan.empty"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                val huawei = scanState.devices.filter { it.isHuaweiOrHonor }
                val others = scanState.devices.filter { !it.isHuaweiOrHonor }

                LazyColumn(state = listState) {
                    if (huawei.isNotEmpty()) {
                        item {
                            SectionHeader(i18n("scan.huawei_honor_devices"))
                        }
                        items(
                            items = huawei,
                            key = { it.device.address },
                        ) { device ->
                            DeviceItem(device, displayMode) { onDeviceSelected(device.device) }
                        }
                    }
                    if (others.isNotEmpty()) {
                        item { SectionHeader(i18n("scan.other_devices")) }
                        items(
                            items = others,
                            key = { it.device.address },
                        ) { device ->
                            DeviceItem(device, displayMode) { onDeviceSelected(device.device) }
                        }
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
private fun DeviceItem(device: ScannedDevice, displayMode: UiDisplayMode, onClick: () -> Unit) {
    val bondedLabel = i18n("home.bonded")
    Material3Card(
        displayMode = displayMode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(
                painter = painterResource(UiAssetCatalog.device(UiAssetCatalog.DeviceVisual.EarbudCase)),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = if (device.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(device.displayName, fontWeight = FontWeight.Medium)
                Text(
                    buildString {
                        append(device.address)
                        if (device.isBonded) append(" · ").append(bondedLabel)
                        if (device.rssi != 0) append(" · ${device.rssi} dBm")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
            Icon(
                if (device.isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = null,
                tint = if (device.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
