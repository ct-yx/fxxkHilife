package com.freebuds.controller.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.freebuds.controller.bluetooth.ScannedDevice
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.i18n.i18n
import com.freebuds.controller.ui.glass.AdaptiveCard
import com.freebuds.controller.ui.glass.AdaptiveGlassBanner
import com.freebuds.controller.ui.glass.GlassBannerTone
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
                title = { Text(i18n("scan.title")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = i18n("common.back"))
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
                StatusBanner(i18n("scan.connected_to", (connState as ConnectionState.Connected).deviceName),
                    displayMode = displayMode,
                    hazeState = hazeState,
                    isConnected = true)
            }
            if (connState is ConnectionState.Connecting) {
                StatusBanner(i18n("scan.connecting_to", (connState as ConnectionState.Connecting).deviceName), displayMode, hazeState)
            }
            if (connState is ConnectionState.Failed) {
                StatusBanner(i18n("scan.connection_failed", (connState as ConnectionState.Failed).reason), displayMode, hazeState, isError = true)
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

                LazyColumn {
                    if (huawei.isNotEmpty()) {
                        item {
                            SectionHeader(i18n("scan.huawei_honor_devices"))
                        }
                        items(huawei) { DeviceItem(it, displayMode, hazeState) { onDeviceSelected(it.device.address) } }
                    }
                    if (others.isNotEmpty()) {
                        item { SectionHeader(i18n("scan.other_devices")) }
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
    val bondedLabel = i18n("home.bonded")
    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(
                painter = painterResource(com.freebuds.controller.R.drawable.ic_earbuds_case),
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
    hazeState: HazeState?,
    isError: Boolean = false,
    isConnected: Boolean = false,
) {
    AdaptiveGlassBanner(
        displayMode = displayMode,
        hazeState = hazeState,
        tone = when {
            isError -> GlassBannerTone.Error
            isConnected -> GlassBannerTone.Success
            else -> GlassBannerTone.Info
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
