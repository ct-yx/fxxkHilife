package com.freebuds.controller.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import coil.compose.AsyncImage
import com.freebuds.controller.BuildConfig
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.i18n.i18n
import com.freebuds.controller.util.LogBuffer
import com.freebuds.controller.ui.glass.AdaptiveCard
import com.freebuds.controller.ui.glass.GlassSurfaceProfile
import com.freebuds.controller.ui.glass.LiquidGlassConfig
import com.freebuds.controller.ui.theme.ThemeMode
import dev.chrisbanes.haze.HazeState
import com.freebuds.controller.ui.theme.saveThemeMode

enum class WallpaperScope { ALL, HOME, SETTINGS }

fun loadWallpaperScope(context: android.content.Context): WallpaperScope {
    val name = context.getSharedPreferences("fxxk_theme", android.content.Context.MODE_PRIVATE)
        .getString("wallpaper_scope", WallpaperScope.ALL.name) ?: WallpaperScope.ALL.name
    return try { WallpaperScope.valueOf(name) } catch (_: Exception) { WallpaperScope.ALL }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    wallpaperUri: String?,
    onWallpaperChange: (String?) -> Unit,
    wallpaperScope: WallpaperScope,
    onWallpaperScopeChange: (WallpaperScope) -> Unit,
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    glassConfig: LiquidGlassConfig,
    onGlassConfigChange: (LiquidGlassConfig) -> Unit,
    onDisplayModeChange: (UiDisplayMode) -> Unit,
) {
    val context = LocalContext.current
    val connState by viewModel.connectionState.collectAsState()
    val isConnected = connState is com.freebuds.controller.data.ConnectionState.Connected

    var showGlassWallpaperGuide by remember { mutableStateOf(false) }
    var enableGlassAfterWallpaperPick by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            val uriStr = it.toString()
            onWallpaperChange(uriStr)
            context.getSharedPreferences("fxxk_theme", android.content.Context.MODE_PRIVATE)
                .edit().putString("wallpaper_uri", uriStr).apply()
            if (enableGlassAfterWallpaperPick) {
                enableGlassAfterWallpaperPick = false
                onDisplayModeChange(UiDisplayMode.LIQUID_GLASS)
            }
        }
    }

    if (showGlassWallpaperGuide) {
        AlertDialog(
            onDismissRequest = { showGlassWallpaperGuide = false },
            title = { Text(i18n("settings.wallpaper_guide.title")) },
            text = { Text(i18n("settings.wallpaper_guide.text")) },
            confirmButton = {
                TextButton(onClick = {
                    showGlassWallpaperGuide = false
                    enableGlassAfterWallpaperPick = true
                    imagePicker.launch("image/*")
                }) { Text(i18n("settings.wallpaper_guide.pick")) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showGlassWallpaperGuide = false
                        onDisplayModeChange(UiDisplayMode.LIQUID_GLASS)
                    }) { Text(i18n("settings.wallpaper_guide.continue")) }
                    TextButton(onClick = { showGlassWallpaperGuide = false }) { Text(i18n("common.cancel")) }
                }
            },
        )
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(i18n("settings.title")) },
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
            // ── 主题 ──
            item { SettingsHeader(i18n("settings.theme")) }
            item {
                ThemeSelector(
                    displayMode = displayMode,
                    hazeState = hazeState,
                    current = themeMode,
                    onSelect = { mode ->
                        onThemeChange(mode)
                        saveThemeMode(context, mode)
                    }
                )
            }
            item {
                DisplayModeSelector(
                    displayMode = displayMode,
                    hazeState = hazeState,
                    current = displayMode,
                    onSelect = { mode ->
                        if (mode == UiDisplayMode.LIQUID_GLASS && wallpaperUri == null) {
                            showGlassWallpaperGuide = true
                        } else {
                            onDisplayModeChange(mode)
                        }
                    },
                )
            }

            // ── 个性化 ──
            item { SettingsHeader(i18n("settings.personalization")) }
            item {
                LiquidGlassPersonalizationCard(
                    displayMode = displayMode,
                    hazeState = hazeState,
                    config = glassConfig,
                    onConfigChange = onGlassConfigChange,
                )
            }

            // ── 壁纸 ──
            item { SettingsHeader(i18n("settings.wallpaper")) }
            item {
                WallpaperPicker(
                    displayMode = displayMode,
                    hazeState = hazeState,
                    uri = wallpaperUri,
                    onPick = { imagePicker.launch("image/*") },
                    onClear = {
                        onWallpaperChange(null)
                        context.getSharedPreferences("fxxk_theme", android.content.Context.MODE_PRIVATE)
                            .edit().remove("wallpaper_uri").apply()
                    }
                )
            }
            if (wallpaperUri != null) {
                item {
                    WallpaperScopeSelector(
                        displayMode = displayMode,
                        hazeState = hazeState,
                        current = wallpaperScope,
                        onSelect = {
                            onWallpaperScopeChange(it)
                            context.getSharedPreferences("fxxk_theme", android.content.Context.MODE_PRIVATE)
                                .edit().putString("wallpaper_scope", it.name).apply()
                        }
                    )
                }
            }

            // ── 关于 ──
            item { SettingsHeader(i18n("settings.about")) }
            item {
                SettingsCard(
                    displayMode = displayMode,
                    hazeState = hazeState,
                    headlineContent = { Text(i18n("settings.version")) },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                )
            }
            item {
                val savedAddresses = viewModel.getSavedAddresses()
                SettingsCard(
                    displayMode = displayMode,
                    hazeState = hazeState,
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
                val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                var autoLowLatency by remember {
                    mutableStateOf(prefs.getBoolean("auto_low_latency", true))
                }
                SettingsCard(
                    displayMode = displayMode,
                    hazeState = hazeState,
                    headlineContent = { Text(i18n("settings.auto_low_latency")) },
                    supportingContent = { Text(i18n("settings.auto_low_latency_desc")) },
                    leadingContent = { Icon(Icons.Default.Speed, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = autoLowLatency,
                            onCheckedChange = {
                                autoLowLatency = it
                                prefs.edit().putBoolean("auto_low_latency", it).apply()
                            }
                        )
                    },
                )
            }

            // ── 调试 ──
            item { SettingsHeader(i18n("settings.debug")) }
            if (isConnected) {
                item {
                    SettingsCard(
                        displayMode = displayMode,
                        hazeState = hazeState,
                        headlineContent = { Text(i18n("settings.debug_terminal")) },
                        supportingContent = { Text(i18n("settings.debug_terminal_desc")) },
                        leadingContent = { Icon(Icons.Default.Terminal, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        onClick = { context.startActivity(Intent(context, TerminalActivity::class.java)) },
                    )
                }
                item {
                    SettingsCard(
                        displayMode = displayMode,
                        hazeState = hazeState,
                        headlineContent = { Text(i18n("settings.share_log")) },
                        supportingContent = { Text(i18n("settings.share_log_desc")) },
                        leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = { viewModel.shareLog(context) },
                    )
                }
                item {
                    LogRetentionSelector(displayMode = displayMode, hazeState = hazeState)
                }
            } else {
                item {
                    SettingsCard(
                        displayMode = displayMode,
                        hazeState = hazeState,
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
                    hazeState = hazeState,
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
                AdaptiveCard(
                    displayMode = displayMode,
                    hazeState = hazeState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.clickable { expanded = !expanded },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(com.freebuds.controller.R.drawable.ic_anc_awareness),
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
                            // 降噪
                            Icon(painter = painterResource(com.freebuds.controller.R.drawable.ic_anc_cancellation),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "降噪模式：noise canceling by Vanicon studio from Noun Project (CC BY 3.0)",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(12.dp))

                            // 通透
                            Icon(painter = painterResource(com.freebuds.controller.R.drawable.ic_anc_awareness),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "通透模式：noise canceling by Gregor Cresnar from Noun Project (CC BY 3.0)",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(12.dp))

                            // 关闭
                            Icon(painter = painterResource(com.freebuds.controller.R.drawable.ic_anc_normal),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "ANC关闭：Wireless headset by Berkah Icon from Noun Project (CC BY 3.0)",
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
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    headlineContent: @Composable () -> Unit,
    supportingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
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
private fun LiquidGlassPersonalizationCard(
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    config: LiquidGlassConfig,
    onConfigChange: (LiquidGlassConfig) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }
    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(i18n("settings.liquid_glass_personalization"), fontWeight = FontWeight.Bold)
                Text(i18n("settings.liquid_glass_personalization_desc"), style = MaterialTheme.typography.bodySmall)
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 渲染方案固定为最新主线，传统实现仅保留为代码兜底。
                ConfigSegmentRow(
                    title = i18n("settings.glass_blur"),
                    subtitle = i18n("settings.glass_blur_desc"),
                    options = listOf(
                        i18n("settings.glass.transparent") to config.copy(tintAlpha = 0.08f, refractionStrength = 0.58f),
                        i18n("common.default") to config.copy(tintAlpha = 0.12f, refractionStrength = 0.72f),
                        i18n("settings.glass.misty") to config.copy(tintAlpha = 0.16f, refractionStrength = 0.86f),
                    ),
                    selectedIndex = when {
                        config.tintAlpha < 0.10f -> 0
                        config.tintAlpha > 0.14f -> 2
                        else -> 1
                    },
                    onSelect = onConfigChange,
                )
                ConfigSegmentRow(
                    title = i18n("settings.glass_refraction"),
                    subtitle = i18n("settings.glass_lens_desc"),
                    options = listOf(
                        i18n("common.disabled") to config.copy(refractionStrength = 0.35f),
                        i18n("common.default") to config.copy(refractionStrength = 0.72f),
                        i18n("common.enhanced") to config.copy(refractionStrength = 0.92f),
                    ),
                    selectedIndex = when {
                        config.refractionStrength < 0.50f -> 0
                        config.refractionStrength > 0.82f -> 2
                        else -> 1
                    },
                    onSelect = onConfigChange,
                )
                ConfigSegmentRow(
                    title = i18n("settings.glass_depth"),
                    subtitle = i18n("settings.glass_depth_desc"),
                    options = listOf(
                        i18n("common.disabled") to config.copy(depth = 0.16f),
                        i18n("common.default") to config.copy(depth = 0.42f),
                        i18n("common.enhanced") to config.copy(depth = 0.64f),
                    ),
                    selectedIndex = when {
                        config.depth < 0.25f -> 0
                        config.depth > 0.55f -> 2
                        else -> 1
                    },
                    onSelect = onConfigChange,
                )
                ConfigSegmentRow(
                    title = i18n("settings.glass_readability"),
                    subtitle = i18n("settings.glass_readability_desc"),
                    options = listOf(
                        i18n("settings.glass.low") to config.copy(readabilityStrength = 0.12f),
                        i18n("common.default") to config.copy(readabilityStrength = 0.28f),
                        i18n("settings.glass.high") to config.copy(readabilityStrength = 0.48f),
                    ),
                    selectedIndex = when {
                        config.readabilityStrength < 0.20f -> 0
                        config.readabilityStrength > 0.40f -> 2
                        else -> 1
                    },
                    onSelect = onConfigChange,
                )

                Row(
                    modifier = Modifier.clickable { advancedExpanded = !advancedExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(i18n("settings.advanced_mode"), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Icon(if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
                AnimatedVisibility(visible = advancedExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassSliderRow("Legacy Tint", config.tintAlpha, 0.04f..0.20f) {
                            onConfigChange(config.copy(tintAlpha = it))
                        }
                        GlassSliderRow("Readability", config.readabilityStrength, 0.00f..0.70f) {
                            onConfigChange(config.copy(readabilityStrength = it))
                        }
                        GlassSliderRow("Refraction", config.refractionStrength, 0.30f..1.00f) {
                            onConfigChange(config.copy(refractionStrength = it))
                        }
                        GlassSliderRow("Depth", config.depth, 0.10f..0.80f) {
                            onConfigChange(config.copy(depth = it))
                        }
                        GlassSliderRow("Radius", config.cornerRadiusDp, 16f..42f) {
                            onConfigChange(config.copy(cornerRadiusDp = it))
                        }
                        ConfigSegmentRow(
                            title = i18n("settings.surface_profile"),
                            subtitle = i18n("settings.edge_highlight_desc"),
                            options = listOf(
                                i18n("settings.surface.rounded") to config.copy(surfaceProfile = GlassSurfaceProfile.Rounded),
                                i18n("settings.surface.squircle") to config.copy(surfaceProfile = GlassSurfaceProfile.Squircle),
                                i18n("settings.surface.circle") to config.copy(surfaceProfile = GlassSurfaceProfile.Circle),
                            ),
                            selectedIndex = when (config.surfaceProfile) {
                                GlassSurfaceProfile.Rounded -> 0
                                GlassSurfaceProfile.Squircle -> 1
                                GlassSurfaceProfile.Circle -> 2
                            },
                            onSelect = onConfigChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigSegmentRow(
    title: String,
    subtitle: String,
    options: List<Pair<String, LiquidGlassConfig>>,
    selectedIndex: Int,
    onSelect: (LiquidGlassConfig) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, option ->
                FilterChip(
                    selected = index == selectedIndex,
                    onClick = { onSelect(option.second) },
                    label = { Text(option.first) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GlassSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(String.format("%.2f", value), style = MaterialTheme.typography.labelSmall)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun AppDetailsCard(
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    onOpenProject: () -> Unit,
    onOpenReleases: () -> Unit,
) {
    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
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
                        Icon(Icons.Default.Headphones, contentDescription = null)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("fxxkHilife", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
    hazeState: HazeState?,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val options = listOf(
        Triple(ThemeMode.SYSTEM, i18n("settings.theme.system"), Icons.Default.BrightnessAuto),
        Triple(ThemeMode.DARK, i18n("settings.theme.dark"), Icons.Default.DarkMode),
        Triple(ThemeMode.LIGHT, i18n("settings.theme.light"), Icons.Default.LightMode),
    )

    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
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
private fun DisplayModeSelector(
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    current: UiDisplayMode,
    onSelect: (UiDisplayMode) -> Unit,
) {
    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        Text(
            i18n("settings.display_mode"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UiDisplayMode.entries.forEach { mode ->
                val selected = mode == current
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(mode) },
                    shape = RoundedCornerShape(22.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    tonalElevation = if (selected) 3.dp else 1.dp,
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            mode.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            mode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun WallpaperPicker(
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    uri: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
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
    hazeState: HazeState?,
    current: WallpaperScope,
    onSelect: (WallpaperScope) -> Unit,
) {
    val options = listOf(
        WallpaperScope.ALL to i18n("settings.scope.all"),
        WallpaperScope.HOME to i18n("settings.scope.home"),
        WallpaperScope.SETTINGS to i18n("settings.scope.settings"),
    )
    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
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
private fun LogRetentionSelector(displayMode: UiDisplayMode, hazeState: HazeState?) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("fxxk_theme", android.content.Context.MODE_PRIVATE)
    var maxLines by remember {
        mutableIntStateOf(prefs.getInt("log_max_lines", LogBuffer.getMaxLines()))
    }

    val options = listOf(500, 1000, 2000, 5000, 10000).map { it to i18n("settings.log_lines", it) }

    AdaptiveCard(
        displayMode = displayMode,
        hazeState = hazeState,
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
                        maxLines = value
                        LogBuffer.setMaxLines(value)
                        prefs.edit().putInt("log_max_lines", value).apply()
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}
