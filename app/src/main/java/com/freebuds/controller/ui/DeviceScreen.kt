package com.freebuds.controller.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.data.DeviceProps
import com.freebuds.controller.data.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onOpenTerminal: () -> Unit,
) {
    val connState by viewModel.connectionState.collectAsState()
    val props by viewModel.props.collectAsState()
    val deviceName = (connState as? ConnectionState.Connected)?.deviceName ?: "耳机"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deviceName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                    IconButton(onClick = { viewModel.disconnect() }) {
                        Icon(Icons.Default.Close, contentDescription = "断开")
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
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── 电池卡片 ─────────────────────────────────────────────────────
            item { BatteryCard(props) }

            // ── 降噪 ─────────────────────────────────────────────────────────
            if (props.ancMode != null) {
                item { SettingsGroupHeader("降噪") }
                item {
                    OptionSettingItem(
                        icon = Icons.Default.Hearing,
                        title = "降噪模式",
                        current = props.ancMode,
                        options = props.ancModeOptions,
                        onSelect = { viewModel.setProperty("anc", "mode", it) }
                    )
                }
                if (props.ancLevel != null) {
                    item {
                        OptionSettingItem(
                            icon = Icons.Default.Tune,
                            title = "降噪强度",
                            current = props.ancLevel,
                            options = props.ancLevelOptions,
                            onSelect = { viewModel.setProperty("anc", "level", it) }
                        )
                    }
                }
            }

            // ── 手势 ─────────────────────────────────────────────────────────
            val hasGesture = props.doubleTapLeft != null || props.longTap != null || props.swipeGesture != null
            if (hasGesture) {
                item { SettingsGroupHeader("手势") }
                if (props.doubleTapLeft != null) {
                    item {
                        OptionSettingItem(
                            icon = Icons.Default.TouchApp,
                            title = "双击 · 左",
                            current = props.doubleTapLeft,
                            options = props.doubleTapOptions,
                            onSelect = { viewModel.setProperty("action", "double_tap_left", it) }
                        )
                    }
                }
                if (props.doubleTapRight != null) {
                    item {
                        OptionSettingItem(
                            icon = Icons.Default.TouchApp,
                            title = "双击 · 右",
                            current = props.doubleTapRight,
                            options = props.doubleTapOptions,
                            onSelect = { viewModel.setProperty("action", "double_tap_right", it) }
                        )
                    }
                }
                if (props.tripleTapLeft != null) {
                    item {
                        OptionSettingItem(
                            icon = Icons.Default.TouchApp,
                            title = "三击 · 左",
                            current = props.tripleTapLeft,
                            options = props.tripleTapOptions,
                            onSelect = { viewModel.setProperty("action", "triple_tap_left", it) }
                        )
                    }
                }
                if (props.tripleTapRight != null) {
                    item {
                        OptionSettingItem(
                            icon = Icons.Default.TouchApp,
                            title = "三击 · 右",
                            current = props.tripleTapRight,
                            options = props.tripleTapOptions,
                            onSelect = { viewModel.setProperty("action", "triple_tap_right", it) }
                        )
                    }
                }
                if (props.longTap != null) {
                    item {
                        OptionSettingItem(
                            icon = Icons.Default.PanTool,
                            title = "长按",
                            current = props.longTap,
                            options = props.longTapOptions,
                            onSelect = { viewModel.setProperty("action", "long_tap", it) }
                        )
                    }
                }
                if (props.swipeGesture != null) {
                    item {
                        OptionSettingItem(
                            icon = Icons.Default.Swipe,
                            title = "滑动手势",
                            current = props.swipeGesture,
                            options = props.swipeGestureOptions,
                            onSelect = { viewModel.setProperty("action", "swipe_gesture", it) }
                        )
                    }
                }
            }

            // ── 音频 ─────────────────────────────────────────────────────────
            val hasAudio = props.soundQuality != null || props.autoPause != null || props.lowLatency != null
            if (hasAudio) {
                item { SettingsGroupHeader("音频") }
                if (props.soundQuality != null) {
                    item {
                        OptionSettingItem(
                            icon = Icons.Default.GraphicEq,
                            title = "音质偏好",
                            current = props.soundQuality,
                            options = props.soundQualityOptions,
                            onSelect = { viewModel.setProperty("sound", "quality_preference", it) }
                        )
                    }
                }
                if (props.autoPause != null) {
                    item {
                        SwitchSettingItem(
                            icon = Icons.Default.PauseCircle,
                            title = "摘下自动暂停",
                            checked = props.autoPause,
                            onCheckedChange = { viewModel.setProperty("config", "auto_pause", it.toString()) }
                        )
                    }
                }
                if (props.lowLatency != null) {
                    item {
                        SwitchSettingItem(
                            icon = Icons.Default.Speed,
                            title = "低延迟模式",
                            checked = props.lowLatency,
                            onCheckedChange = { viewModel.setProperty("config", "low_latency", it.toString()) }
                        )
                    }
                }
            }

            // ── 关于 / 调试 ───────────────────────────────────────────────────
            item { SettingsGroupHeader("关于") }
            if (props.deviceModel != null) {
                item {
                    InfoItem(Icons.Default.Info, "型号", props.deviceModel!!)
                }
            }
            if (props.firmwareVersion != null) {
                item {
                    InfoItem(Icons.Default.SystemUpdate, "固件版本", props.firmwareVersion!!)
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("调试终端") },
                    supportingContent = { Text("查看 SPP 原始日志 / 发送命令") },
                    leadingContent = { Icon(Icons.Default.Terminal, contentDescription = null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenTerminal)
                )
                HorizontalDivider()
            }
        }
    }
}

// ── 组件 ──────────────────────────────────────────────────────────────────────

@Composable
private fun BatteryCard(props: DeviceProps) {
    if (props.batteryGlobal == null && props.batteryLeft == null) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("电量", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (props.batteryLeft != null)
                    BatteryChip("左", props.batteryLeft)
                if (props.batteryRight != null)
                    BatteryChip("右", props.batteryRight)
                if (props.batteryCase != null)
                    BatteryChip("盒", props.batteryCase)
                if (props.batteryLeft == null && props.batteryGlobal != null)
                    BatteryChip("耳机", props.batteryGlobal)
            }
            if (props.isCharging == true)
                Text("充电中", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun BatteryChip(label: String, level: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$level%", style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

@Composable
private fun SettingsGroupHeader(text: String) {
    Column {
        HorizontalDivider()
        Text(
            text,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun OptionSettingItem(
    icon: ImageVector,
    title: String,
    current: String?,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { if (current != null) Text(current) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            if (options.isNotEmpty()) Icon(Icons.Default.ChevronRight, contentDescription = null)
        },
        modifier = Modifier.clickable(enabled = options.isNotEmpty()) { expanded = true }
    )
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
    if (expanded && options.isNotEmpty()) {
        OptionsDialog(title, options, onDismiss = { expanded = false }, onSelect = {
            onSelect(it)
            expanded = false
        })
    }
}

@Composable
private fun SwitchSettingItem(
    icon: ImageVector,
    title: String,
    checked: Boolean?,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(
                checked = checked ?: false,
                onCheckedChange = onCheckedChange,
                enabled = checked != null
            )
        },
        modifier = Modifier.clickable(enabled = checked != null) { onCheckedChange(!(checked ?: false)) }
    )
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
}

@Composable
private fun InfoItem(icon: ImageVector, label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        leadingContent = { Icon(icon, contentDescription = null) }
    )
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
}

@Composable
private fun OptionsDialog(title: String, options: List<String>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { opt ->
                    Text(
                        opt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(opt) }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
