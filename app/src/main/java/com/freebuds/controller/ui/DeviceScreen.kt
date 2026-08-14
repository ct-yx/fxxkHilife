package com.freebuds.controller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.i18n.I18n
import com.freebuds.controller.i18n.i18n
import com.freebuds.controller.ui.glass.AdaptiveCard
import com.freebuds.controller.ui.glass.LiquidGlassPanel
import com.freebuds.controller.ui.state.DeviceEvent
import com.freebuds.controller.ui.state.DualDeviceProperty
import com.freebuds.controller.ui.state.ConnectionSummary
import com.freebuds.controller.ui.foundation.components.AppTopBar
import com.freebuds.controller.ui.foundation.assets.UiAssetCatalog

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

fun chineseEqualizerPreset(raw: String?): String = when (raw) {
    "equalizer_preset_default" -> I18n.t("eq.preset.default")
    "equalizer_preset_hardbass" -> I18n.t("eq.preset.hardbass")
    "equalizer_preset_treble" -> I18n.t("eq.preset.treble")
    "equalizer_preset_voices" -> I18n.t("eq.preset.voices")
    "equalizer_preset_symphony" -> I18n.t("eq.preset.symphony")
    "equalizer_preset_hi_fi_live" -> I18n.t("eq.preset.hifi_live")
    else -> raw ?: I18n.t("common.unknown")
}

// 手势映射已移至 GestureScreen.kt（chineseTap/chineseSwipe/chineseLongTap）

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    viewModel: DeviceViewModel,
    displayMode: UiDisplayMode,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onOpenTerminal: () -> Unit,
    onGesture: () -> Unit,
    onListeningStats: () -> Unit,
) {
    val controlChannelState by viewModel.controlChannelState.collectAsStateWithLifecycle()
    val connection = remember(controlChannelState) { ConnectionSummary.from(controlChannelState) }
    val props by viewModel.props.collectAsStateWithLifecycle()
    val deviceName = connection.deviceName
        ?: props.deviceModel
        ?: I18n.t("device.battery.earbuds")
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
            AppTopBar(
                title = deviceName,
                displayMode = displayMode,
                onBack = onBack,
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
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── 电池卡片 ─────────────────────────────────────────────────────
            item { BatteryCard(props, displayMode) }
            if (props.pendingInitHandlers.isNotEmpty()) {
                item { BackgroundSyncCard(props.pendingInitHandlers, displayMode) }
            }

            // ── ANC ─────────────────────────────────────────────────────────
            displayAncMode?.let { syncedAncMode ->
                item { SettingsGroupHeader(i18n("device.group.anc")) }
                // haze 模糊滑块切换器
                item {
                    LiquidGlassPanel(
                        displayMode = displayMode,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        AncModeSlider(
                            current = syncedAncMode,
                            options = props.ancModeOptions.ifEmpty {
                                listOf("normal", "cancellation", "awareness")
                            },
                            onSelect = {
                                optimisticAncMode = it
                                viewModel.onEvent(DeviceEvent.SetAncMode(it))
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
                                                icon = Icons.Default.Tune,
                            title = ancLevelTitle(syncedAncMode),
                            current = chineseAncLevel(props.ancLevel, syncedAncMode),
                            options = normalizedAncLevelOptions.map { chineseAncLevel(it, syncedAncMode) },
                            rawOptions = normalizedAncLevelOptions,
                            onSelect = { viewModel.onEvent(DeviceEvent.SetAncLevel(it)) }
                        )
                    }
                }
            }

            // ── 音频 ─────────────────────────────────────────────────────────
            val hasSoundQuality = props.soundQuality != null && props.soundQualityOptions.isNotEmpty()
            val hasEqualizer = props.equalizerPreset != null && props.equalizerPresetOptions.isNotEmpty()
            val hasAudio = hasSoundQuality || hasEqualizer || props.autoPause != null || props.lowLatency != null
            if (hasAudio) {
                item { SettingsGroupHeader(i18n("device.group.audio")) }
                if (hasSoundQuality) {
                    item {
                        DeviceOptionItem(
                            displayMode = displayMode,
                                                icon = Icons.Default.GraphicEq,
                            title = i18n("device.option.sound_quality"),
                            current = chineseSoundQuality(props.soundQuality),
                            options = props.soundQualityOptions.map(::chineseSoundQuality),
                            rawOptions = props.soundQualityOptions,
                            onSelect = { viewModel.onEvent(DeviceEvent.SetSoundQuality(it)) }
                        )
                    }
                }
                if (hasEqualizer) {
                    item {
                        DeviceOptionItem(
                            displayMode = displayMode,
                                                icon = Icons.Default.Equalizer,
                            title = i18n("device.option.equalizer_preset"),
                            current = chineseEqualizerPreset(props.equalizerPreset),
                            options = props.equalizerPresetOptions.map(::chineseEqualizerPreset),
                            rawOptions = props.equalizerPresetOptions,
                            onSelect = { viewModel.onEvent(DeviceEvent.SetEqualizerPreset(it)) }
                        )
                    }
                }
                if (props.equalizerRows.isNotEmpty() || props.equalizerMaxCustomModes > 0) {
                    item {
                        EqualizerStatusCard(
                            props = props,
                            displayMode = displayMode,
                                            )
                    }
                }
                if (props.autoPause != null) {
                    item {
                        SwitchSettingItem(
                            displayMode = displayMode,
                                                icon = Icons.Default.PauseCircle,
                            title = i18n("device.option.auto_pause"),
                            checked = props.autoPause,
                            onCheckedChange = { viewModel.onEvent(DeviceEvent.SetAutoPause(it)) }
                        )
                    }
                }
                if (props.lowLatency != null) {
                    item {
                        SwitchSettingItem(
                            displayMode = displayMode,
                                                icon = Icons.Default.Speed,
                            title = i18n("device.option.low_latency"),
                            checked = props.lowLatency,
                            onCheckedChange = { viewModel.onEvent(DeviceEvent.SetLowLatency(it)) }
                        )
                    }
                }
            }

            if (props.dualConnectEnabled != null || props.dualConnectDevices.isNotEmpty()) {
                item { SettingsGroupHeader(i18n("device.group.dual_connect")) }
                props.dualConnectEnabled?.let { enabled ->
                    item {
                        SwitchSettingItem(
                            displayMode = displayMode,
                                                icon = Icons.Default.Devices,
                            title = i18n("device.option.dual_connect"),
                            checked = enabled,
                            onCheckedChange = { viewModel.onEvent(DeviceEvent.SetDualConnect(it)) }
                        )
                    }
                }
                items(
                    items = props.dualConnectDevices,
                    key = { it.address },
                ) { device ->
                    DualConnectDeviceCard(
                        device = device,
                        preferredAddress = props.dualConnectPreferredDevice,
                        displayMode = displayMode,
                        onPreferred = { viewModel.onEvent(DeviceEvent.SetPreferredDevice(device.address)) },
                        onConnectionToggle = { target ->
                            viewModel.onEvent(DeviceEvent.SetDualDeviceOption(device.address, DualDeviceProperty.Connected, target))
                        },
                        onAutoToggle = { target ->
                            viewModel.onEvent(DeviceEvent.SetDualDeviceOption(device.address, DualDeviceProperty.AutoConnect, target))
                        },
                    )
                }
            }

            // ── 听音统计 ─────────────────────────────────────────────────────
            item { SettingsGroupHeader(i18n("stats.title")) }
            item {
                AdaptiveCard(
                    displayMode = displayMode,
                                modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .clickable(onClick = onListeningStats),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Default.QueryStats, contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            Text(i18n("stats.title"))
                            Text(i18n("stats.description"), style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
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
            props.deviceModel?.let { model ->
                item {
                    InfoItem(displayMode, Icons.Default.Info, i18n("device.model"), model)
                }
            }
            props.firmwareVersion?.let { firmware ->
                item {
                    InfoItem(displayMode, Icons.Default.SystemUpdate, i18n("device.firmware"), firmware)
                }
            }
            item {
                AdaptiveCard(
                    displayMode = displayMode,
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

// ── 页面编排 ──────────────────────────────────────────────────────────────────

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
                val iconId = UiAssetCatalog.anc(
                    when (raw) {
                        "cancellation" -> UiAssetCatalog.AncVisual.Cancellation
                        "awareness" -> UiAssetCatalog.AncVisual.Awareness
                        else -> UiAssetCatalog.AncVisual.Off
                    }
                )
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
