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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.freebuds.controller.data.DeviceProps
import com.freebuds.controller.data.DualConnectDevice
import com.freebuds.controller.i18n.I18n
import com.freebuds.controller.i18n.i18n
import com.freebuds.controller.ui.glass.AdaptiveCard

/** Device feature cards kept separate from route/layout orchestration. */
@Composable
internal fun BatteryCard(props: DeviceProps, displayMode: UiDisplayMode) {
    if (props.batteryGlobal == null && props.batteryLeft == null && props.batteryRight == null && props.batteryCase == null) return
    val leftLevel = props.batteryLeft ?: props.batteryGlobal
    val rightLevel = props.batteryRight ?: props.batteryGlobal
    AdaptiveCard(
        displayMode = displayMode,
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
internal fun BackgroundSyncCard(
    pendingHandlers: List<String>,
    displayMode: UiDisplayMode,
) {
    val labels = pendingHandlers.map(::initHandlerLabel).distinct()
    val preview = labels.take(4).joinToString("、")
    val suffix = if (labels.size > 4) i18n("device.pending.more_suffix", labels.size) else ""
    AdaptiveCard(
        displayMode = displayMode,
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
    "config_eq" -> I18n.t("device.pending.config_eq")
    "dual_connect" -> I18n.t("device.pending.dual_connect")
    "voice_language" -> I18n.t("device.pending.voice_language")
    "tws_in_ear" -> I18n.t("device.pending.tws_in_ear")
    "battery" -> I18n.t("device.pending.battery")
    "low_latency" -> I18n.t("device.pending.low_latency")
    else -> id
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeviceOptionItem(
    displayMode: UiDisplayMode,
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
internal fun SwitchSettingItem(
    displayMode: UiDisplayMode,
    icon: ImageVector,
    title: String,
    checked: Boolean?,
    onCheckedChange: (Boolean) -> Unit,
) {
    AdaptiveCard(
        displayMode = displayMode,
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
internal fun InfoItem(displayMode: UiDisplayMode, icon: ImageVector, label: String, value: String) {
    AdaptiveCard(
        displayMode = displayMode,
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

@Composable
internal fun EqualizerStatusCard(
    props: DeviceProps,
    displayMode: UiDisplayMode,
) {
    AdaptiveCard(
        displayMode = displayMode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Default.GraphicEq, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(i18n("device.option.custom_eq"), style = MaterialTheme.typography.titleSmall)
                val rows = props.equalizerRows.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: i18n("common.unknown")
                Text(
                    i18n("device.option.custom_eq_desc", props.equalizerMaxCustomModes, rows),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(if (props.equalizerSaved == false) i18n("device.eq.unsaved") else i18n("device.eq.read_only")) },
            )
        }
    }
}

@Composable
internal fun DualConnectDeviceCard(
    device: DualConnectDevice,
    preferredAddress: String?,
    displayMode: UiDisplayMode,
    onPreferred: () -> Unit,
    onConnectionToggle: (Boolean) -> Unit,
    onAutoToggle: (Boolean) -> Unit,
) {
    val isPreferred = device.preferred || preferredAddress == device.address
    AdaptiveCard(
        displayMode = displayMode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(
                if (device.connected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = null,
                tint = if (device.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    buildString {
                        append(formatMac(device.address))
                        append(" · ")
                        append(if (device.connected) i18n("device.dual.connected") else i18n("device.dual.disconnected"))
                        if (device.playing) append(" · ").append(i18n("device.dual.playing"))
                        if (isPreferred) append(" · ").append(i18n("device.dual.preferred"))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = device.connected,
                onCheckedChange = onConnectionToggle,
            )
        }
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = isPreferred,
                onClick = onPreferred,
                label = { Text(i18n("device.dual.preferred")) },
                leadingIcon = if (isPreferred) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
            )
            device.autoConnect?.let { auto ->
                FilterChip(
                    selected = auto,
                    onClick = { onAutoToggle(!auto) },
                    label = { Text(i18n("device.dual.auto_connect")) },
                    leadingIcon = if (auto) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                )
            }
        }
    }
}

private fun formatMac(raw: String): String =
    raw.chunked(2).joinToString(":").uppercase()
