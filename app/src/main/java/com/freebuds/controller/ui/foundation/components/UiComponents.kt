package com.freebuds.controller.ui.foundation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.freebuds.controller.ui.state.ConnectionSummary
import com.freebuds.controller.ui.state.UiActionState
import com.freebuds.controller.ui.foundation.tokens.UiTokens
import com.freebuds.controller.ui.glass.AdaptiveCard
import com.freebuds.controller.ui.UiDisplayMode
import com.freebuds.controller.ui.foundation.surface.SurfaceRole

@Immutable
data class UiOption<T>(
    val value: T,
    val label: String,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val pending: Boolean = false,
    val unavailableReason: String? = null,
)

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = UiTokens.ref.space4, vertical = UiTokens.ref.space3),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    trailingContent: @Composable RowScope.() -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = onClick?.let { callback ->
        modifier
            .semantics { role = Role.Button }
            .clickable(enabled = enabled, onClick = callback)
    } ?: modifier
    Row(
        modifier = clickableModifier
            .fillMaxWidth()
            .padding(horizontal = UiTokens.ref.space4, vertical = UiTokens.ref.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiTokens.ref.space3),
    ) {
        leadingIcon?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) UiTokens.sys.content() else UiTokens.sys.secondaryContent())
            subtitle?.let {
                Spacer(Modifier.height(UiTokens.ref.space1))
                Text(it, style = MaterialTheme.typography.bodySmall, color = UiTokens.sys.secondaryContent())
            }
        }
        trailingContent()
    }
}

@Composable
fun BooleanOptionRow(
    title: String,
    checked: Boolean?,
    onCheckedChange: ((Boolean) -> Unit)?,
    subtitle: String? = null,
    enabled: Boolean = onCheckedChange != null,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        trailingContent = {
            if (checked == null) {
                Text("—", color = UiTokens.sys.secondaryContent())
            } else {
                Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
            }
        },
    )
}

@Composable
fun SingleChoiceOption(
    title: String,
    selected: Boolean,
    onSelected: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.RadioButton }
            .then(Modifier.clickable(enabled = enabled, onClick = onSelected))
            .padding(horizontal = UiTokens.ref.space4, vertical = UiTokens.ref.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelected, enabled = enabled)
        Spacer(Modifier.width(UiTokens.ref.space2))
        Text(title, color = if (enabled) UiTokens.sys.content() else UiTokens.sys.secondaryContent())
    }
}

@Composable
fun DependentChoiceOption(
    option: UiOption<String>,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = option.selected,
        onClick = onClick,
        enabled = option.enabled,
        label = { Text(option.label) },
        leadingIcon = if (option.pending) {
            { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) }
        } else null,
    )
}

@Composable
fun SegmentedOption(
    options: List<UiOption<String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiTokens.ref.space2)) {
        options.forEach { option ->
            DependentChoiceOption(option, onClick = { onSelect(option.value) })
        }
    }
}

@Composable
fun SliderOption(
    title: String,
    valueLabel: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = UiTokens.ref.space4, vertical = UiTokens.ref.space2)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelSmall, color = UiTokens.sys.secondaryContent())
        }
        content()
    }
}

@Composable
fun ActionPicker(
    title: String,
    options: List<UiOption<String>>,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ref.space2)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        SegmentedOption(options, onSelect)
    }
}

@Composable
fun AsyncActionIndicator(
    state: UiActionState,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    if (state == UiActionState.Idle) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiTokens.ref.space2),
    ) {
        when (state) {
            UiActionState.Pending -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            UiActionState.Success -> Icon(Icons.Default.Check, contentDescription = null, tint = UiTokens.sys.success())
            UiActionState.Failure -> Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = UiTokens.sys.danger())
            UiActionState.Disabled -> Icon(Icons.Default.Close, contentDescription = null, tint = UiTokens.sys.secondaryContent())
            UiActionState.Idle -> Unit
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun ConnectionBanner(
    summary: ConnectionSummary,
    displayMode: UiDisplayMode,
    modifier: Modifier = Modifier,
) {
    val error = summary.failedHandlers.isNotEmpty() || summary.reason != null
    val text = summary.deviceName?.let { name ->
        when {
            error -> "$name · ${summary.reason ?: "connection error"}"
            summary.isReady -> "$name · Ready"
            else -> "$name · ${summary.stageLabel}"
        }
    } ?: summary.stageLabel
    AdaptiveCard(
        displayMode = displayMode,
        role = SurfaceRole.FeatureCard,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiTokens.ref.space3)) {
            Icon(if (error) Icons.Default.ErrorOutline else Icons.Default.Info, contentDescription = null)
            Text(text, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun EmptyState(title: String, description: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        description?.let {
            Spacer(Modifier.height(UiTokens.ref.space1))
            Text(it, style = MaterialTheme.typography.bodySmall, color = UiTokens.sys.secondaryContent())
        }
    }
}

@Composable
fun ErrorState(title: String, description: String? = null, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = UiTokens.sys.danger())
        Text(title, color = UiTokens.sys.danger(), style = MaterialTheme.typography.titleMedium)
        description?.let {
            Spacer(Modifier.height(UiTokens.ref.space1))
            Text(it, style = MaterialTheme.typography.bodySmall, color = UiTokens.sys.secondaryContent())
        }
        onRetry?.let { retry -> TextButton(onClick = retry) { Text("Retry") } }
    }
}

@Composable
fun PrimaryActionButton(content: @Composable RowScope.() -> Unit, onClick: () -> Unit, enabled: Boolean = true) =
    androidx.compose.material3.Button(onClick = onClick, enabled = enabled, content = content)

@Composable
fun SecondaryActionButton(content: @Composable RowScope.() -> Unit, onClick: () -> Unit, enabled: Boolean = true) =
    androidx.compose.material3.OutlinedButton(onClick = onClick, enabled = enabled, content = content)

@Composable
fun TertiaryActionButton(content: @Composable RowScope.() -> Unit, onClick: () -> Unit, enabled: Boolean = true) =
    TextButton(onClick = onClick, enabled = enabled, content = content)

@Composable
fun DestructiveActionButton(content: @Composable RowScope.() -> Unit, onClick: () -> Unit, enabled: Boolean = true) =
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        content = content,
    )

@Composable
fun IconActionButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, enabled: Boolean = true) {
    androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
fun ToggleButton(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    androidx.compose.material3.FilterChip(selected = checked, onClick = { onCheckedChange(!checked) }, enabled = enabled, label = { Text(label) })
}
