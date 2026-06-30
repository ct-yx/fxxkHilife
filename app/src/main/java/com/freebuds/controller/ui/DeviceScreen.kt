package com.freebuds.controller.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freebuds.controller.R
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.data.DeviceProps
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.i18n.I18n
import com.freebuds.controller.i18n.i18n
import com.freebuds.controller.ui.glass.AdaptiveCard
import com.freebuds.controller.ui.glass.LiquidGlassPanel
import dev.chrisbanes.haze.HazeState

// ── 中文映射（DeviceScreen 专用）──────────────────────────────────────────

fun chineseAncMode(raw: String?): String = when (raw) {
    "normal" -> I18n.t("anc.mode.off")
    "cancellation" -> I18n.t("anc.mode.cancellation")
    "awareness" -> I18n.t("anc.mode.awareness")
    else -> raw ?: I18n.t("common.unknown")
}

fun chineseAncLevel(raw: String?, mode: String?): String = when (mode) {
    "cancellation" -> when (raw) {
        "comfort", "1" -> I18n.t("anc.level.comfort")
        "normal", "0" -> I18n.t("anc.level.normal")
        "ultra", "2" -> I18n.t("anc.level.ultra")
        "dynamic", "3" -> I18n.t("anc.level.dynamic")
        else -> raw ?: I18n.t("common.unknown")
    }
    "awareness" -> when (raw) {
        "normal", "0", "2" -> I18n.t("anc.awareness.normal")
        "voice_boost", "1" -> I18n.t("anc.awareness.voice_boost")
        else -> raw ?: I18n.t("common.unknown")
    }
    else -> raw ?: I18n.t("common.unknown")
}

private fun canonicalAncLevel(raw: String, mode: String?): String = when (mode) {
    "cancellation" -> when (raw) {
        "0" -> "normal"
        "1" -> "comfort"
        "2" -> "ultra"
        "3" -> "dynamic"
        else -> raw
    }
    "awareness" -> when (raw) {
        "0", "2" -> "normal"
        "1" -> "voice_boost"
        else -> raw
    }
    else -> raw
}

fun ancLevelTitle(mode: String?): String = when (mode) {
    "cancellation" -> I18n.t("anc.level_title.cancellation")
    "awareness" -> I18n.t("anc.level_title.awareness")
    else -> I18n.t("anc.level_title.default")
}

fun chineseSoundQuality(raw: String?): String = when (raw) {
    "sqp_connectivity" -> I18n.t("sound.quality.connectivity")
    "sqp_quality" -> I18n.t("sound.quality.quality")
    else -> raw ?: I18n.t("common.unknown")
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
    val deviceName = (connState as? ConnectionState.Connected)?.deviceName ?: I18n.t("device.battery.earbuds")
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
                        Icon(Icons.Default.ArrowBack, contentDescription = i18n("common.back"))
                    }
                },
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
                item { SettingsGroupHeader(i18n("device.group.anc")) }
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
                val normalizedAncLevelOptions = props.ancLevelOptions
                    .map { canonicalAncLevel(it, syncedAncMode) }
                    .distinct()
                if (props.ancLevel != null && syncedAncMode != "normal" && normalizedAncLevelOptions.isNotEmpty()) {
                    item {
                        DeviceOptionItem(
                            displayMode = displayMode,
                            hazeState = hazeState,
                            icon = Icons.Default.Tune,
                            title = ancLevelTitle(syncedAncMode),
                            current = chineseAncLevel(props.ancLevel, syncedAncMode),
                            options = normalizedAncLevelOptions.map { chineseAncLevel(it, syncedAncMode) },
                            rawOptions = normalizedAncLevelOptions,
                            onSelect = { viewModel.setProperty("anc", "level", it) }
                        )
                    }
                }
            }

            // ── 音频 ─────────────────────────────────────────────────────────
            val hasSoundQuality = props.soundQuality != null && props.soundQualityOptions.isNotEmpty()
            val hasAudio = hasSoundQuality || props.autoPause != null || props.lowLatency != null
            if (hasAudio) {
                item { SettingsGroupHeader(i18n("device.group.audio")) }
                if (hasSoundQuality) {
                    item {
                        DeviceOptionItem(
                            displayMode = displayMode,
                            hazeState = hazeState,
                            icon = Icons.Default.GraphicEq,
                            title = i18n("device.option.sound_quality"),
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
                            title = i18n("device.option.auto_pause"),
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
                            title = i18n("device.option.low_latency"),
                            checked = props.lowLatency,
                            onCheckedChange = { viewModel.setProperty("config", "low_latency", it.toString()) }
                        )
                    }
                }
            }

            // ── 手势（下移到页面底部）────────────────────────────────────────
            val hasGesture = props.doubleTapLeft != null || props.longTap != null || props.swipeGesture != null
            if (hasGesture) {
                item { SettingsGroupHeader(i18n("device.group.gestures")) }
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
                                Text(i18n("device.gesture_settings"))
                                Text(i18n("device.gesture_settings_desc"), style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }

            // ── 关于 / 调试 ───────────────────────────────────────────────────
            item { SettingsGroupHeader(i18n("device.group.about")) }
            if (props.deviceModel != null) {
                item {
                    InfoItem(displayMode, hazeState, Icons.Default.Info, i18n("device.model"), props.deviceModel!!)
                }
            }
            if (props.firmwareVersion != null) {
                item {
                    InfoItem(displayMode, hazeState, Icons.Default.SystemUpdate, i18n("device.firmware"), props.firmwareVersion!!)
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
                            Text(i18n("device.debug_terminal"))
                            Text(i18n("device.debug_terminal_desc"), style = MaterialTheme.typography.bodySmall)
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
    if (props.batteryGlobal == null && props.batteryLeft == null && props.batteryRight == null && props.batteryCase == null) return
    val leftLevel = props.batteryLeft ?: props.batteryGlobal
    val rightLevel = props.batteryRight ?: props.batteryGlobal
    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(i18n("device.battery"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(122.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BatteryEarbud(
                        label = i18n("device.battery.left"),
                        level = leftLevel,
                        disconnected = leftLevel == null || leftLevel == 0,
                        mirror = false,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 10.dp, y = 0.dp),
                    )
                    BatteryEarbud(
                        label = i18n("device.battery.right"),
                        level = rightLevel,
                        disconnected = rightLevel == null || rightLevel == 0,
                        mirror = true,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-10).dp, y = 0.dp),
                    )
                }
                Spacer(Modifier.width(18.dp))
                BatteryCaseBox(
                    level = props.batteryCase,
                    charging = props.isCharging == true,
                    modifier = Modifier.width(116.dp),
                )
            }
        }
    }
}

@Composable
private fun BatteryEarbud(
    label: String,
    level: Int?,
    disconnected: Boolean,
    mirror: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier.width(82.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(width = 62.dp, height = 70.dp), contentAlignment = Alignment.Center) {
            BatteryWaterCircle(
                level = level,
                modifier = Modifier
                    .size(38.dp)
                    .align(if (mirror) Alignment.TopEnd else Alignment.TopStart),
            )
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(42.dp)
                    .align(if (mirror) Alignment.BottomStart else Alignment.BottomEnd)
                    .background(color, RoundedCornerShape(12.dp)),
            )
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(11.dp)
                    .align(Alignment.Center)
                    .background(color, RoundedCornerShape(8.dp)),
            )
            if (disconnected) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.14f, size.height * 0.84f),
                        end = Offset(size.width * 0.86f, size.height * 0.16f),
                        strokeWidth = 5f,
                    )
                }
            }
        }
        Text(level?.let { "$it%" } ?: "--", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BatteryWaterCircle(level: Int?, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val fraction = ((level ?: 0).coerceIn(0, 100) / 100f)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction)
                .align(Alignment.BottomCenter)
                .background(color.copy(alpha = 0.48f)),
        )
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(4.dp)
                .align(Alignment.Center)
                .background(color.copy(alpha = 0.26f), RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
private fun BatteryCaseBox(
    level: Int?,
    charging: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(72.dp)
                .background(color.copy(alpha = 0.16f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(42.dp)
                    .background(color, RoundedCornerShape(18.dp)),
            )
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(6.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.28f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(((level ?: 0).coerceIn(0, 100) / 100f))
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(level?.let { "$it%" } ?: "--", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            if (charging) i18n("common.charging") else i18n("device.battery.case"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    val suffix = if (labels.size > 4) i18n("device.pending.more_suffix", labels.size) else ""
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
                Text(i18n("device.group.background_sync"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    i18n("device.pending.detail", preview, suffix),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun initHandlerLabel(id: String): String = when (id) {
    "device_info" -> I18n.t("device.pending.device_info")
    "anc_global" -> "ANC"
    "gesture_double" -> I18n.t("device.pending.gesture_double")
    "gesture_triple" -> I18n.t("device.pending.gesture_triple")
    "gesture_long" -> I18n.t("device.pending.gesture_long")
    "gesture_swipe" -> I18n.t("device.pending.gesture_swipe")
    "tws_auto_pause" -> I18n.t("device.pending.auto_pause")
    "config_sound_quality" -> I18n.t("device.pending.config_sound_quality")
    "voice_language" -> I18n.t("device.pending.voice_language")
    "tws_in_ear" -> I18n.t("device.pending.tws_in_ear")
    "battery" -> I18n.t("device.pending.battery")
    "low_latency" -> I18n.t("device.pending.low_latency")
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
            .animateContentSize(animationSpec = tween(durationMillis = 260))
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
        AnimatedVisibility(
            visible = expanded && options.isNotEmpty(),
            enter = fadeIn(tween(160)) + expandVertically(tween(240)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
        ) {
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
                val iconSize = when (raw) {
                    "normal" -> 23.dp
                    "cancellation" -> 22.dp
                    "awareness" -> 24.dp
                    else -> 22.dp
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
                                modifier = Modifier.size(iconSize),
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
