package com.freebuds.controller.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freebuds.controller.R
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.data.DeviceProps
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.ui.glass.AdaptiveCard
import com.freebuds.controller.ui.glass.LiquidGlassPanel
import dev.chrisbanes.haze.HazeState

// ── 中文映射（DeviceScreen 专用）──────────────────────────────────────────

fun chineseAncMode(raw: String?): String = when (raw) {
    "normal" -> "关闭"
    "cancellation" -> "降噪"
    "awareness" -> "透传"
    else -> raw ?: "未知"
}

fun chineseAncLevel(raw: String?, mode: String?): String = when (mode) {
    "cancellation" -> when (raw) {
        "comfort" -> "舒适"
        "normal" -> "均衡"
        "ultra" -> "深度"
        "dynamic" -> "动态"
        else -> raw ?: "未知"
    }
    "awareness" -> when (raw) {
        "normal" -> "普通透传"
        "voice_boost" -> "人声增强"
        else -> raw ?: "未知"
    }
    else -> raw ?: "未知"
}

fun ancLevelTitle(mode: String?): String = when (mode) {
    "cancellation" -> "降噪强度"
    "awareness" -> "通透模式"
    else -> "ANC 子模式"
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
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onOpenTerminal: () -> Unit,
    onGesture: () -> Unit,
) {
    val connState by viewModel.connectionState.collectAsState()
    val props by viewModel.props.collectAsState()
    val deviceName = (connState as? ConnectionState.Connected)?.deviceName ?: "耳机"
    var optimisticAncMode by remember { mutableStateOf<String?>(null) }
    val displayAncMode = optimisticAncMode ?: props.ancMode

    // 当 props 的实际值追上乐观值时清除乐观状态（含超时保护）
    LaunchedEffect(props.ancMode) {
        optimisticAncMode?.let { expected ->
            if (props.ancMode == expected) {
                optimisticAncMode = null
            } else {
                // 超时保护，防止卡死或乱跳
                delay(3000)
                if (optimisticAncMode == expected) optimisticAncMode = null
            }
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
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
                    containerColor = if (displayMode == UiDisplayMode.LIQUID_GLASS) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface
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
            item { BatteryCard(props, displayMode, hazeState) }
            if (props.pendingInitHandlers.isNotEmpty()) {
                item { BackgroundSyncCard(props.pendingInitHandlers, displayMode, hazeState) }
            }

            // ── ANC ─────────────────────────────────────────────────────────
            displayAncMode?.let { syncedAncMode ->
                item { SettingsGroupHeader("ANC模式") }
                // haze 模糊滑块切换器
                item {
                    LiquidGlassPanel(
                        displayMode = displayMode,
                        hazeState = hazeState,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        AncModeSlider(
                            current = syncedAncMode,
                            options = props.ancModeOptions.ifEmpty {
                                listOf("normal", "cancellation", "awareness")
                            },
                            onSelect = {
                                optimisticAncMode = it
                                viewModel.setProperty("anc", "mode", it)
                            },
                        )
                    }
                }
                if (props.ancLevel != null && syncedAncMode != "normal" && props.ancLevelOptions.isNotEmpty()) {
                    item {
                        DeviceOptionItem(
                            displayMode = displayMode,
                            hazeState = hazeState,
                            icon = Icons.Default.Tune,
                            title = ancLevelTitle(syncedAncMode),
                            current = chineseAncLevel(props.ancLevel, syncedAncMode),
                            options = props.ancLevelOptions.map { chineseAncLevel(it, syncedAncMode) },
                            rawOptions = props.ancLevelOptions,
                            onSelect = { viewModel.setProperty("anc", "level", it) }
                        )
                    }
                }
            }

            // ── 音频 ─────────────────────────────────────────────────────────
            val hasSoundQuality = props.soundQuality != null && props.soundQualityOptions.isNotEmpty()
            val hasAudio = hasSoundQuality || props.autoPause != null || props.lowLatency != null
            if (hasAudio) {
                item { SettingsGroupHeader("音频") }
                if (hasSoundQuality) {
                    item {
                        DeviceOptionItem(
                            displayMode = displayMode,
                            hazeState = hazeState,
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
                            displayMode = displayMode,
                            hazeState = hazeState,
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
                            displayMode = displayMode,
                            hazeState = hazeState,
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
                    AdaptiveCard(
                        displayMode = displayMode,
                        hazeState = hazeState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                            .clickable(onClick = onGesture),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Default.TouchApp, contentDescription = null)
                            Column(Modifier.weight(1f)) {
                                Text("手势设置")
                                Text("双击 / 三击 / 滑动 / 长按", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }

            // ── 关于 / 调试 ───────────────────────────────────────────────────
            item { SettingsGroupHeader("关于") }
            if (props.deviceModel != null) {
                item {
                    InfoItem(displayMode, hazeState, Icons.Default.Info, "型号", props.deviceModel!!)
                }
            }
            if (props.firmwareVersion != null) {
                item {
                    InfoItem(displayMode, hazeState, Icons.Default.SystemUpdate, "固件版本", props.firmwareVersion!!)
                }
            }
            item {
                AdaptiveCard(
                    displayMode = displayMode,
                    hazeState = hazeState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .clickable(onClick = onOpenTerminal),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Default.Terminal, contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            Text("调试终端")
                            Text("查看 SPP 原始日志 / 发送命令", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

// ── 组件 ──────────────────────────────────────────────────────────────────────

@Composable
private fun BatteryCard(props: DeviceProps, displayMode: UiDisplayMode, hazeState: HazeState?) {
    if (props.batteryGlobal == null && props.batteryLeft == null) return
    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun BackgroundSyncCard(
    pendingHandlers: List<String>,
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
) {
    val labels = pendingHandlers.map(::initHandlerLabel).distinct()
    val preview = labels.take(4).joinToString("、")
    val suffix = if (labels.size > 4) " 等 ${labels.size} 项" else ""
    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Sync, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("后台同步中", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "正在补齐：$preview$suffix。通常 15–45 秒内陆续完成，慢项会保持后台重试。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun initHandlerLabel(id: String): String = when (id) {
    "device_info" -> "设备信息"
    "anc_global" -> "ANC"
    "gesture_double" -> "双击手势"
    "gesture_triple" -> "三击手势"
    "gesture_long" -> "长按手势"
    "gesture_swipe" -> "滑动手势"
    "tws_auto_pause" -> "摘下暂停"
    "config_sound_quality" -> "音质偏好"
    "voice_language" -> "语音语言"
    "tws_in_ear" -> "佩戴状态"
    "battery" -> "电量"
    "low_latency" -> "低延迟"
    else -> id
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceOptionItem(
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    icon: ImageVector,
    title: String,
    current: String?,
    options: List<String>,
    rawOptions: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(false) }
    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(enabled = options.isNotEmpty()) { expanded = !expanded },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (current != null) Text(current, style = MaterialTheme.typography.bodySmall)
            }
            if (options.isNotEmpty()) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }
        }
        AnimatedVisibility(visible = expanded && options.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEachIndexed { index, label ->
                    val raw = rawOptions.getOrNull(index) ?: return@forEachIndexed
                    val selected = label == current
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onSelect(raw)
                            expanded = false
                        },
                        label = { Text(label) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchSettingItem(
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    icon: ImageVector,
    title: String,
    checked: Boolean?,
    onCheckedChange: (Boolean) -> Unit,
) {
    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(enabled = checked != null) { onCheckedChange(!(checked ?: false)) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null)
            Text(title, modifier = Modifier.weight(1f))
            Switch(
                checked = checked ?: false,
                onCheckedChange = onCheckedChange,
                enabled = checked != null,
            )
        }
    }
}

@Composable
private fun InfoItem(displayMode: UiDisplayMode, hazeState: HazeState?, icon: ImageVector, label: String, value: String) {
    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null)
            Column {
                Text(label)
                Text(value, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ── ANC 模式分段选择器 ──────────────────────────────────────────────

@Composable
private fun AncModeSlider(
    current: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    val visibleOptions = options.filter { it in setOf("normal", "cancellation", "awareness") }.ifEmpty {
        listOf("normal", "cancellation", "awareness")
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            visibleOptions.forEach { raw ->
                val label = chineseAncMode(raw)
                val isSelected = raw == current
                val iconId = when (raw) {
                    "normal" -> R.drawable.ic_anc_normal
                    "cancellation" -> R.drawable.ic_anc_cancellation
                    "awareness" -> R.drawable.ic_anc_awareness
                    else -> R.drawable.ic_anc_normal
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable { onSelect(raw) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(iconId),
                                contentDescription = label,
                                modifier = Modifier.size(20.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        label,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
