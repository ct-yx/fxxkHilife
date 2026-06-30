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
import com.freebuds.controller.data.DeviceProps
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.i18n.I18n
import com.freebuds.controller.i18n.i18n
import com.freebuds.controller.ui.glass.AdaptiveCard
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureScreen(
    props: DeviceProps,
    viewModel: DeviceViewModel,
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(i18n("gesture.title")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = i18n("common.back"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (displayMode == UiDisplayMode.LIQUID_GLASS) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = if (displayMode == UiDisplayMode.LIQUID_GLASS) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (props.doubleTapLeft != null) {
                item {
                    OptionSettingItem(
                        displayMode = displayMode,
                        hazeState = hazeState,
                        icon = Icons.Default.TouchApp,
                        title = i18n("gesture.double_left"),
                        current = chineseTap(props.doubleTapLeft),
                        options = props.doubleTapOptions.map(::chineseTap),
                        rawOptions = props.doubleTapOptions,
                        onSelect = { viewModel.setProperty("action", "double_tap_left", it) }
                    )
                }
            }
            if (props.doubleTapRight != null) {
                item {
                    OptionSettingItem(
                        displayMode = displayMode,
                        hazeState = hazeState,
                        icon = Icons.Default.TouchApp,
                        title = i18n("gesture.double_right"),
                        current = chineseTap(props.doubleTapRight),
                        options = props.doubleTapOptions.map(::chineseTap),
                        rawOptions = props.doubleTapOptions,
                        onSelect = { viewModel.setProperty("action", "double_tap_right", it) }
                    )
                }
            }
            if (props.tripleTapLeft != null) {
                item {
                    OptionSettingItem(
                        displayMode = displayMode,
                        hazeState = hazeState,
                        icon = Icons.Default.TouchApp,
                        title = i18n("gesture.triple_left"),
                        current = chineseTap(props.tripleTapLeft),
                        options = props.tripleTapOptions.map(::chineseTap),
                        rawOptions = props.tripleTapOptions,
                        onSelect = { viewModel.setProperty("action", "triple_tap_left", it) }
                    )
                }
            }
            if (props.tripleTapRight != null) {
                item {
                    OptionSettingItem(
                        displayMode = displayMode,
                        hazeState = hazeState,
                        icon = Icons.Default.TouchApp,
                        title = i18n("gesture.triple_right"),
                        current = chineseTap(props.tripleTapRight),
                        options = props.tripleTapOptions.map(::chineseTap),
                        rawOptions = props.tripleTapOptions,
                        onSelect = { viewModel.setProperty("action", "triple_tap_right", it) }
                    )
                }
            }
            if (props.swipeGesture != null) {
                item {
                    OptionSettingItem(
                        displayMode = displayMode,
                        hazeState = hazeState,
                        icon = Icons.Default.Swipe,
                        title = i18n("gesture.swipe"),
                        current = chineseSwipe(props.swipeGesture),
                        options = props.swipeGestureOptions.map(::chineseSwipe),
                        rawOptions = props.swipeGestureOptions,
                        onSelect = { viewModel.setProperty("action", "swipe_gesture", it) }
                    )
                }
            }
            if (props.longTap != null) {
                item {
                    OptionSettingItem(
                        displayMode = displayMode,
                        hazeState = hazeState,
                        icon = Icons.Default.PanTool,
                        title = i18n("gesture.long_tap"),
                        current = chineseLongTap(props.longTap),
                        options = props.longTapOptions.map(::chineseLongTap),
                        rawOptions = props.longTapOptions,
                        onSelect = { viewModel.setProperty("action", "long_tap", it) }
                    )
                }
            }
        }
    }
}

// ── 中文映射 ──────────────────────────────────────────────────────────────

internal fun chineseTap(raw: String?): String = when (raw) {
    "tap_action_pause" -> I18n.t("gesture.action.pause")
    "tap_action_next" -> I18n.t("gesture.action.next")
    "tap_action_prev" -> I18n.t("gesture.action.prev")
    "tap_action_assistant" -> I18n.t("gesture.action.assistant")
    "tap_action_off" -> I18n.t("common.disabled")
    "tap_action_answer" -> I18n.t("gesture.action.answer")
    else -> raw ?: I18n.t("common.unknown")
}

internal fun chineseSwipe(raw: String?): String = when (raw) {
    "tap_action_change_volume" -> I18n.t("gesture.action.volume")
    "tap_action_off" -> I18n.t("common.disabled")
    else -> raw ?: I18n.t("common.unknown")
}

internal fun chineseLongTap(raw: String?): String = when (raw) {
    "noise_control_disabled" -> I18n.t("gesture.noise.disabled")
    "noise_control_off_on" -> I18n.t("gesture.noise.off_on")
    "noise_control_off_on_aw" -> I18n.t("gesture.noise.off_on_aw")
    "noise_control_on_aw" -> I18n.t("gesture.noise.on_aw")
    "noise_control_off_an" -> I18n.t("gesture.noise.off_an")
    else -> raw ?: I18n.t("common.unknown")
}

// ── 复用组件（从 DeviceScreen 移来） ──────────────────────────────────────

@Composable
internal fun OptionSettingItem(
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    icon: ImageVector,
    title: String,
    current: String?,
    options: List<String>,
    rawOptions: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(enabled = options.isNotEmpty()) { expanded = true },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (current != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(current, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (options.isNotEmpty()) Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
    if (expanded && options.isNotEmpty()) {
        OptionsDialog2(title, options, rawOptions, onDismiss = { expanded = false }, onSelect = {
            onSelect(it)
            expanded = false
        })
    }
}

@Composable
private fun OptionsDialog2(
    title: String,
    options: List<String>,
    rawOptions: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
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
                ) { Text(i18n("common.cancel")) }
            }
        },
        confirmButton = { }
    )
}