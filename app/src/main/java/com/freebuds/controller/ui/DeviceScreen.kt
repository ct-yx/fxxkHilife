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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.data.DeviceProps
import com.freebuds.controller.data.DeviceViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

// ── 中文映射（DeviceScreen 专用）──────────────────────────────────────────

fun chineseAncMode(raw: String?): String = when (raw) {
    "normal" -> "关闭"
    "cancellation" -> "降噪"
    "awareness" -> "透传"
    else -> raw ?: "未知"
}

fun chineseAncLevel(raw: String?): String = when (raw) {
    "comfort" -> "舒适"
    "normal" -> "透传模式"
    "ultra" -> "深度"
    "dynamic" -> "动态"
    "voice_boost" -> "人声增强"
    else -> raw ?: "未知"
}

fun chineseSoundQuality(raw: String?): String = when (raw) {
    "sqp_connectivity" -> "连接优先"
    "sqp_quality" -> "音质优先"
    else -> raw ?: "未知"
}

// 手势映射已移至 GestureScreen.kt（chineseTap/chineseSwipe/chineseLongTap）

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onOpenTerminal: () -> Unit,
    onGesture: () -> Unit,
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

            // ── ANC ─────────────────────────────────────────────────────────
            if (props.ancMode != null) {
                item { SettingsGroupHeader("ANC模式") }
                // haze 模糊滑块切换器
                item {
                    AncModeSlider(
                        current = props.ancMode ?: "normal",
                        options = props.ancModeOptions,
                        onSelect = { viewModel.setProperty("anc", "mode", it) },
                    )
                }
                if (props.ancLevel != null) {
                    item {
                        DeviceOptionItem(
                            icon = Icons.Default.Tune,
                            title = "降噪强度",
                            current = chineseAncLevel(props.ancLevel),
                            options = props.ancLevelOptions.map(::chineseAncLevel),
                            rawOptions = props.ancLevelOptions,
                            onSelect = { viewModel.setProperty("anc", "level", it) }
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
                        DeviceOptionItem(
                            icon = Icons.Default.GraphicEq,
                            title = "音质偏好",
                            current = chineseSoundQuality(props.soundQuality),
                            options = props.soundQualityOptions.map(::chineseSoundQuality),
                            rawOptions = props.soundQualityOptions,
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

            // ── 手势（下移到页面底部）────────────────────────────────────────
            val hasGesture = props.doubleTapLeft != null || props.longTap != null || props.swipeGesture != null
            if (hasGesture) {
                item { SettingsGroupHeader("手势") }
                item {
                    ListItem(
                        headlineContent = { Text("手势设置") },
                        supportingContent = { Text("双击 / 三击 / 滑动 / 长按") },
                        leadingContent = { Icon(Icons.Default.TouchApp, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onGesture)
                    )
                    HorizontalDivider()
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
private fun DeviceOptionItem(
    icon: ImageVector,
    title: String,
    current: String?,
    options: List<String>,
    rawOptions: List<String>,
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
        OptionsDialog(title, options, rawOptions, onDismiss = { expanded = false }, onSelect = {
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
private fun OptionsDialog(title: String, options: List<String>, rawOptions: List<String>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { idx, opt ->
                    Text(
                        opt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(rawOptions[idx]) }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("取消") }
            }
        },
        confirmButton = { } // 取消已移到内容区
    )
}

// ── ANC 模式 haze 模糊滑块 ──────────────────────────────────────────────

@Composable
private fun AncModeSlider(
    current: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    val hazeState = remember { HazeState() }
    val selectedIndex = options.indexOf(current).coerceAtLeast(0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.medium)
    ) {
        // 模糊背景层
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .haze(state = hazeState)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEachIndexed { idx, raw ->
                    val label = chineseAncMode(raw)
                    val isSelected = idx == selectedIndex
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(raw) },
                        shape = MaterialTheme.shapes.small,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(vertical = 12.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // haze 子层：模糊 + 折射
        Box(
            modifier = Modifier
                .matchParentSize()
                .hazeChild(state = hazeState) {
                    style = HazeStyle(
                        blurRadius = 10.dp,
                        tint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
                    )
                }
        )
    }
}
