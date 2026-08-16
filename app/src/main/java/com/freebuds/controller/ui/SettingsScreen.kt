package com.freebuds.controller.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.freebuds.controller.BuildConfig
import com.freebuds.controller.data.UpdateSettings
import com.freebuds.controller.data.UpdateUiState
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.i18n.i18n
import com.freebuds.controller.i18n.I18nLocale
import com.freebuds.controller.ui.foundation.components.Material3Card
import com.freebuds.controller.ui.theme.ThemeMode
import com.freebuds.controller.ui.foundation.components.AppTopBar
import com.freebuds.controller.ui.foundation.assets.UiAssetCatalog
import com.freebuds.controller.ui.state.ConnectionSummary
import com.freebuds.controller.ui.state.SettingsEvent
import java.text.DateFormat
import java.util.Date
import java.util.Locale

enum class WallpaperScope { ALL, HOME, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit,
    onEvent: (SettingsEvent) -> Unit,
) {
    val context = LocalContext.current
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val controlChannelState by viewModel.controlChannelState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val themeMode = settingsState.themeMode
    val wallpaperUri = settingsState.wallpaperUri
    val wallpaperScope = settingsState.wallpaperScope
    val displayMode = UiDisplayMode.MATERIAL3
    val locale = settingsState.locale
    val updateSettings = settingsState.updateSettings
    val isConnected = remember(controlChannelState) {
        ConnectionSummary.from(controlChannelState).isReady
    }

    LaunchedEffect(Unit) { onEvent(SettingsEvent.CheckForUpdate()) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(it, flags) }
            val uriStr = it.toString()
            onEvent(SettingsEvent.SetWallpaperUri(uriStr))
        }
    }
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            AppTopBar(
                title = i18n("settings.title"),
                displayMode = displayMode,
                onBack = onBack,
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── 主题 ──
            item { SettingsHeader(i18n("settings.theme")) }
            item {
                ThemeSelector(
                    displayMode = displayMode,
                                current = themeMode,
                    onSelect = { mode ->
                        onEvent(SettingsEvent.SetThemeMode(mode))
                    }
                )
            }
            item { SettingsHeader(i18n("settings.language")) }
            item {
                LanguageSelector(
                    displayMode = displayMode,
                                current = locale,
                    onSelect = { onEvent(SettingsEvent.SetLocale(it)) },
                )
            }

            // ── 壁纸 ──
            item { SettingsHeader(i18n("settings.wallpaper")) }
            item {
                WallpaperPicker(
                    displayMode = displayMode,
                                uri = wallpaperUri,
                    onPick = { imagePicker.launch(arrayOf("image/*")) },
                    onClear = {
                        onEvent(SettingsEvent.SetWallpaperUri(null))
                    }
                )
            }
            if (wallpaperUri != null) {
                item {
                        WallpaperScopeSelector(
                        displayMode = displayMode,
                                        current = wallpaperScope,
                        onSelect = { onEvent(SettingsEvent.SetWallpaperScope(it)) }
                    )
                }
            }

            // ── 关于 ──
            item { SettingsHeader(i18n("settings.about")) }
            item {
                SettingsCard(
                    displayMode = displayMode,
                                headlineContent = { Text(i18n("settings.version")) },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                )
            }
            item {
                UpdateCard(
                    displayMode = displayMode,
                    state = updateState,
                    settings = updateSettings,
                    onEvent = onEvent,
                )
            }
            item {
                val savedAddresses = viewModel.getSavedAddresses()
                SettingsCard(
                    displayMode = displayMode,
                                headlineContent = { Text(i18n("settings.saved_devices", savedAddresses.size)) },
                    supportingContent = {
                        Text(if (savedAddresses.isEmpty()) i18n("common.none") else savedAddresses.joinToString("\n"))
                    },
                    leadingContent = { Icon(Icons.Default.Devices, contentDescription = null) },
                )
            }

            // ── 连接偏好 ──
            item { SettingsHeader(i18n("settings.connection_preferences")) }
            item {
                SettingsCard(
                    displayMode = displayMode,
                                headlineContent = { Text(i18n("settings.auto_low_latency")) },
                    supportingContent = { Text(i18n("settings.auto_low_latency_desc")) },
                    leadingContent = { Icon(Icons.Default.Speed, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = settingsState.autoLowLatency,
                            onCheckedChange = {
                                onEvent(SettingsEvent.SetAutoLowLatency(it))
                            }
                        )
                    },
                )
            }

            // ── 调试 ──
            item { SettingsHeader(i18n("settings.debug")) }
            item {
                SettingsCard(
                    displayMode = displayMode,
                                headlineContent = { Text(i18n("settings.share_log")) },
                    supportingContent = { Text(i18n("settings.share_log_desc")) },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = { viewModel.shareLog(context) },
                )
            }
            item {
                LogRetentionSelector(
                    displayMode = displayMode,
                    maxLines = settingsState.logMaxLines,
                    onMaxLinesChange = { onEvent(SettingsEvent.SetLogMaxLines(it)) },
                )
            }
            item {
                ProtocolFrameLogToggle(
                    displayMode = displayMode,
                    enabled = settingsState.protocolFrameLogging,
                    onEnabledChange = { onEvent(SettingsEvent.SetProtocolFrameLogging(it)) },
                )
            }
            if (isConnected) {
                item {
                    SettingsCard(
                        displayMode = displayMode,
                                        headlineContent = { Text(i18n("settings.debug_terminal")) },
                        supportingContent = { Text(i18n("settings.debug_terminal_desc")) },
                        leadingContent = { Icon(Icons.Default.Terminal, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        onClick = { context.startActivity(Intent(context, TerminalActivity::class.java)) },
                    )
                }
            } else {
                item {
                    SettingsCard(
                        displayMode = displayMode,
                                        headlineContent = { Text(i18n("settings.debug_requires_connected")) },
                        leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                    )
                }
            }

            // ── 应用详情 ──────────────────────────────────────────────────────
            item { SettingsHeader(i18n("settings.app_details")) }
            item {
                AppDetailsCard(
                    displayMode = displayMode,
                                onOpenProject = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ct-yx/fxxkHilife")))
                    },
                    onOpenReleases = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ct-yx/fxxkHilife/releases")))
                    },
                )
            }

            // ── 其他贡献 ──────────────────────────────────────────────────────
            item { SettingsHeader(i18n("settings.other_credits")) }
            item {
                var expanded by remember { mutableStateOf(false) }
                Material3Card(
                    displayMode = displayMode,
                                modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.clickable { expanded = !expanded },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(UiAssetCatalog.anc(UiAssetCatalog.AncVisual.Awareness)),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(i18n("settings.third_party_icons"), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                            Text(i18n("settings.third_party_icons_desc"))
                        }
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                    AnimatedVisibility(visible = expanded) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Icon(painter = painterResource(UiAssetCatalog.anc(UiAssetCatalog.AncVisual.Cancellation)),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                i18n("settings.credit.anc_cancellation"),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(12.dp))

                            Icon(painter = painterResource(UiAssetCatalog.anc(UiAssetCatalog.AncVisual.Awareness)),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                i18n("settings.credit.anc_awareness"),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(12.dp))

                            Icon(painter = painterResource(UiAssetCatalog.anc(UiAssetCatalog.AncVisual.Off)),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                i18n("settings.credit.anc_off"),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 组件 ────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsHeader(text: String) {
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
private fun SettingsCard(
    displayMode: UiDisplayMode,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    headlineContent: @Composable () -> Unit,
    supportingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Material3Card(
        displayMode = displayMode,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .then(onClick?.let { callback -> Modifier.clickable { callback() } } ?: Modifier),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingContent != null) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) { leadingContent() }
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                headlineContent()
                if (supportingContent != null) {
                    Spacer(Modifier.height(3.dp))
                    supportingContent()
                }
            }
            if (trailingContent != null) {
                Spacer(Modifier.width(12.dp))
                trailingContent()
            }
        }
    }
}

@Composable
private fun UpdateCard(
    displayMode: UiDisplayMode,
    state: UpdateUiState,
    settings: UpdateSettings,
    onEvent: (SettingsEvent) -> Unit,
) {
    val title = when (state) {
        UpdateUiState.Idle -> i18n("update.idle")
        is UpdateUiState.Checking -> i18n("update.checking")
        is UpdateUiState.UpToDate -> i18n("update.up_to_date")
        is UpdateUiState.Available -> i18n("update.available", state.manifest.versionName)
        is UpdateUiState.Downloading -> i18n("update.downloading")
        is UpdateUiState.ReadyToInstall -> i18n("update.ready_to_install")
        is UpdateUiState.Installing -> i18n("update.installing")
        is UpdateUiState.Error -> i18n("update.failed")
    }
    val supporting = when (state) {
        is UpdateUiState.Error -> state.message
        is UpdateUiState.Available -> state.manifest.notes.ifBlank { i18n("update.available_desc") }
        is UpdateUiState.Downloading -> state.totalBytes?.let {
            "${state.downloadedBytes / 1024} / ${it / 1024} KB"
        } ?: "${state.downloadedBytes / 1024} KB"
        is UpdateUiState.ReadyToInstall -> i18n("update.ready_to_install_desc")
        else -> i18n("update.current_version", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    }
    val manifest = when (state) {
        is UpdateUiState.Available -> state.manifest
        is UpdateUiState.Downloading -> state.manifest
        is UpdateUiState.ReadyToInstall -> state.manifest
        is UpdateUiState.Installing -> state.manifest
        else -> null
    }
    val checkedAtMs = when (state) {
        is UpdateUiState.Checking -> state.startedAtMs
        is UpdateUiState.UpToDate -> state.checkedAtMs
        is UpdateUiState.Available -> state.checkedAtMs
        else -> settings.lastCheckAtMs
    }
    Material3Card(
        displayMode = displayMode,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(i18n("update.title"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(title, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                i18n("update.last_checked", formatUpdateTimestamp(checkedAtMs)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            manifest?.let {
                Text(
                    i18n(
                        "update.release_metadata",
                        it.channel.name.lowercase(Locale.US),
                        it.publishedAt.ifBlank { "—" },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state is UpdateUiState.Downloading) {
                val progress = state.totalBytes?.takeIf { it > 0 }?.let { state.downloadedBytes.toFloat() / it }
                if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i18n("update.auto_check"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = settings.autoCheckEnabled,
                    onCheckedChange = { onEvent(SettingsEvent.SetUpdateSettings(settings.copy(autoCheckEnabled = it))) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i18n("update.auto_download"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = settings.autoDownloadEnabled,
                    onCheckedChange = { onEvent(SettingsEvent.SetUpdateSettings(settings.copy(autoDownloadEnabled = it))) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is UpdateUiState.Available -> Button(onClick = { onEvent(SettingsEvent.DownloadUpdate) }) { Text(i18n("update.download")) }
                    is UpdateUiState.Downloading -> OutlinedButton(onClick = { onEvent(SettingsEvent.CancelUpdateDownload) }) { Text(i18n("update.cancel")) }
                    is UpdateUiState.ReadyToInstall -> Button(onClick = { onEvent(SettingsEvent.InstallUpdate) }) { Text(i18n("update.install")) }
                    is UpdateUiState.Error -> OutlinedButton(onClick = { onEvent(SettingsEvent.CheckForUpdate(force = true)) }) { Text(i18n("common.retry")) }
                    else -> OutlinedButton(onClick = { onEvent(SettingsEvent.CheckForUpdate(force = true)) }) { Text(i18n("update.check_now")) }
                }
                TextButton(onClick = { onEvent(SettingsEvent.OpenRelease(manifest?.releaseUrl)) }) {
                    Text(i18n("update.open_release"))
                }
            }
        }
    }
}

@Composable
private fun formatUpdateTimestamp(timestampMs: Long): String =
    if (timestampMs <= 0L) {
        i18n("update.never_checked")
    } else {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestampMs))
    }

@Composable
private fun AppDetailsCard(
    displayMode: UiDisplayMode,
    onOpenProject: () -> Unit,
    onOpenReleases: () -> Unit,
) {
    Material3Card(
        displayMode = displayMode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(UiAssetCatalog.device(UiAssetCatalog.DeviceVisual.EarbudCase)),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(i18n("app.name"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(i18n("settings.project_philosophy"), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        i18n("settings.project_philosophy_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onOpenProject, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("GitHub")
                }
                Button(onClick = onOpenReleases, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(i18n("settings.update_url"))
                }
            }
        }
    }
}

@Composable
private fun ThemeSelector(
    displayMode: UiDisplayMode,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val options = listOf(
        Triple(ThemeMode.SYSTEM, i18n("settings.theme.system"), Icons.Default.BrightnessAuto),
        Triple(ThemeMode.DARK, i18n("settings.theme.dark"), Icons.Default.DarkMode),
        Triple(ThemeMode.LIGHT, i18n("settings.theme.light"), Icons.Default.LightMode),
    )

    Material3Card(
        displayMode = displayMode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (mode, label, icon) ->
                val selected = mode == current
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable { onSelect(mode) }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        label,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSelector(
    displayMode: UiDisplayMode,
    current: I18nLocale,
    onSelect: (I18nLocale) -> Unit,
) {
    Material3Card(
        displayMode = displayMode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        I18nLocale.entries.forEach { locale ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(locale) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = current == locale,
                    onClick = { onSelect(locale) },
                )
                Text(i18n(locale.labelKey), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun WallpaperPicker(
    displayMode: UiDisplayMode,
    uri: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Material3Card(
        displayMode = displayMode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column {
        if (uri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(MaterialTheme.shapes.medium)
            ) {
                AsyncImage(
                    model = Uri.parse(uri),
                    contentDescription = i18n("settings.wallpaper_preview"),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPick) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (uri != null) i18n("settings.wallpaper_change") else i18n("settings.wallpaper_import"))
            }
            if (uri != null) {
                OutlinedButton(onClick = onClear) { Text(i18n("common.clear")) }
            }
        }
    }
}
}

@Composable
private fun WallpaperScopeSelector(
    displayMode: UiDisplayMode,
    current: WallpaperScope,
    onSelect: (WallpaperScope) -> Unit,
) {
    val options = listOf(
        WallpaperScope.ALL to i18n("settings.scope.all"),
        WallpaperScope.HOME to i18n("settings.scope.home"),
        WallpaperScope.SETTINGS to i18n("settings.scope.settings"),
    )
    Material3Card(
        displayMode = displayMode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(i18n("settings.display_scope"), style = MaterialTheme.typography.bodySmall)
            options.forEach { (scope, label) ->
                FilterChip(
                    selected = scope == current,
                    onClick = { onSelect(scope) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

@Composable
private fun LogRetentionSelector(
    displayMode: UiDisplayMode,
    maxLines: Int,
    onMaxLinesChange: (Int) -> Unit,
) {

    val options = listOf(500, 1000, 2000, 5000, 10000).map { it to i18n("settings.log_lines", it) }

    Material3Card(
        displayMode = displayMode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                i18n("settings.log_retention"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            options.forEach { (value, label) ->
                FilterChip(
                    selected = maxLines == value,
                    onClick = {
                        onMaxLinesChange(value)
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

@Composable
private fun ProtocolFrameLogToggle(
    displayMode: UiDisplayMode,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    SettingsCard(
        displayMode = displayMode,
        headlineContent = { Text(i18n("settings.log_protocol_frames")) },
        supportingContent = { Text(i18n("settings.log_protocol_frames_desc")) },
        leadingContent = { Icon(Icons.Default.DataObject, contentDescription = null) },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    onEnabledChange(checked)
                }
            )
        },
    )
}
