package com.freebuds.controller.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.freebuds.controller.data.DeviceProps
import com.freebuds.controller.data.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureScreen(
    props: DeviceProps,
    viewModel: DeviceViewModel,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("手势设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
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
                        icon = Icons.Default.TouchApp,
                        title = "双击 · 左",
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
                        icon = Icons.Default.TouchApp,
                        title = "双击 · 右",
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
                        icon = Icons.Default.TouchApp,
                        title = "三击 · 左",
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
                        icon = Icons.Default.TouchApp,
                        title = "三击 · 右",
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
                        icon = Icons.Default.Swipe,
                        title = "滑动手势",
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
                        icon = Icons.Default.PanTool,
                        title = "长按",
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
    "tap_action_pause" -> "播放/暂停"
    "tap_action_next" -> "下一首"
    "tap_action_prev" -> "上一首"
    "tap_action_assistant" -> "语音助手"
    "tap_action_off" -> "关闭"
    "tap_action_answer" -> "接听/挂断"
    else -> raw ?: "未知"
}

internal fun chineseSwipe(raw: String?): String = when (raw) {
    "tap_action_change_volume" -> "音量调节"
    "tap_action_off" -> "关闭"
    else -> raw ?: "未知"
}

internal fun chineseLongTap(raw: String?): String = when (raw) {
    "noise_control_disabled" -> "关闭降噪切换"
    "noise_control_off_on" -> "降噪切换"
    "noise_control_off_on_aw" -> "降噪/透传切换"
    "noise_control_on_aw" -> "透传切换"
    "noise_control_off_an" -> "仅降噪"
    else -> raw ?: "未知"
}

// ── 复用组件（从 DeviceScreen 移来） ──────────────────────────────────────

@Composable
internal fun OptionSettingItem(
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
                ) { Text("取消") }
            }
        },
        confirmButton = { }
    )
}