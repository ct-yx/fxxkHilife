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
import com.freebuds.controller.util.LogBuffer
import com.freebuds.controller.ui.glass.AdaptiveCard
import com.freebuds.controller.ui.glass.GlassRendererMode
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
            title = { Text("建议先设置壁纸") },
            text = { Text("液态玻璃需要背景色彩参与模糊与折射。你可以先选择一张壁纸，也可以继续直接开启。") },
            confirmButton = {
                TextButton(onClick = {
                    showGlassWallpaperGuide = false
                    enableGlassAfterWallpaperPick = true
                    imagePicker.launch("image/*")
                }) { Text("选择壁纸") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showGlassWallpaperGuide = false
                        onDisplayModeChange(UiDisplayMode.LIQUID_GLASS)
                    }) { Text("仍然开启") }
                    TextButton(onClick = { showGlassWallpaperGuide = false }) { Text("取消") }
                }
            },
        )
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            // ── 主题 ──
            item { SettingsHeader("主题") }
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
            item { SettingsHeader("个性化") }
            item {
                LiquidGlassPersonalizationCard(
                    displayMode = displayMode,
                    hazeState = hazeState,
                    config = glassConfig,
                    onConfigChange = onGlassConfigChange,
                )
            }

            // ── 壁纸 ──
            item { SettingsHeader("壁纸") }
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
            item { SettingsHeader("关于") }
            item {
                SettingsCard(
                    displayMode = displayMode,
                    hazeState = hazeState,
                    headlineContent = { Text("版本") },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                )
            }
            item {
                val savedAddresses = viewModel.getSavedAddresses()
                SettingsCard(
                    displayMode = displayMode,
                    hazeState = hazeState,
                    headlineContent = { Text("已保存的设备（${savedAddresses.size}）") },
                    supportingContent = {
                        Text(if (savedAddresses.isEmpty()) "无" else savedAddresses.joinToString("\n"))
                    },
                    leadingContent = { Icon(Icons.Default.Devices, contentDescription = null) },
                )
            }

            // ── 连接偏好 ──
            item { SettingsHeader("连接偏好") }
            item {
                val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                var autoLowLatency by remember {
                    mutableStateOf(prefs.getBoolean("auto_low_latency", true))
                }
                SettingsCard(
                    displayMode = displayMode,
                    hazeState = hazeState,
                    headlineContent = { Text("自动低延迟模式") },
                    supportingContent = { Text("连接已保存耳机后自动开启低延迟") },
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
            item { SettingsHeader("调试") }
            if (isConnected) {
                item {
                    SettingsCard(
                        displayMode = displayMode,
                        hazeState = hazeState,
                        headlineContent = { Text("调试终端") },
                        supportingContent = { Text("查看 SPP 原始日志 / 发送命令") },
                        leadingContent = { Icon(Icons.Default.Terminal, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        onClick = { context.startActivity(Intent(context, TerminalActivity::class.java)) },
                    )
                }
                item {
                    SettingsCard(
                        displayMode = displayMode,
                        hazeState = hazeState,
                        headlineContent = { Text("分享日志") },
                        supportingContent = { Text("导出当前日志为文本文件") },
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
                        headlineContent = { Text("调试功能需连接耳机后使用") },
                        leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                    )
                }
            }

            // ── 应用详情 ──────────────────────────────────────────────────────
            item { SettingsHeader("应用详情") }
            item {
                SettingsCard(
                    displayMode = displayMode,
                    hazeState = hazeState,
                    headlineContent = { Text("项目理念", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("为华为 FreeBuds 系列耳机提供第三方开源控制面板，还原官方 App 的完整功能，同时保持轻量与高效。") },
                    leadingContent = { Icon(Icons.Default.Lightbulb, contentDescription = null) },
                )
            }
            item {
                SettingsCard(
                    displayMode = displayMode,
                    hazeState = hazeState,
                    headlineContent = { Text("GitHub") },
                    supportingContent = { Text("github.com/ct-yx/fxxkHilife") },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                    trailingContent = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ct-yx/fxxkHilife"))
                        context.startActivity(intent)
                    },
                )
            }
            item {
                SettingsCard(
                    displayMode = displayMode,
                    hazeState = hazeState,
                    headlineContent = { Text("更新地址") },
                    supportingContent = { Text("github.com/ct-yx/fxxkHilife/releases") },
                    leadingContent = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
                    trailingContent = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ct-yx/fxxkHilife/releases"))
                        context.startActivity(intent)
                    },
                )
            }

            // ── 其他贡献 ──────────────────────────────────────────────────────
            item { SettingsHeader("其他贡献") }
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
                            Text("第三方图标", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                            Text("点击展开图标来源与授权信息")
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
                Text("液态玻璃个性化", fontWeight = FontWeight.Bold)
                Text("调节模糊、边缘折射、深度与可读性", style = MaterialTheme.typography.bodySmall)
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ConfigSegmentRow(
                    title = "玻璃渲染方案",
                    subtitle = "最新为 3.0 主线；传统仅保留为兼容兜底",
                    options = listOf(
                        "最新" to config.copy(rendererMode = GlassRendererMode.HAZE_2),
                        "传统" to config.copy(rendererMode = GlassRendererMode.LEGACY_COMPAT),
                    ),
                    selectedIndex = when (config.rendererMode) {
                        GlassRendererMode.HAZE_2 -> 0
                        GlassRendererMode.LEGACY_COMPAT -> 1
                    },
                    onSelect = onConfigChange,
                )
                ConfigSegmentRow(
                    title = "玻璃模糊强度",
                    subtitle = "调整玻璃效果的透明与朦胧程度",
                    options = listOf(
                        "通透" to config.copy(tintAlpha = 0.08f, refractionStrength = 0.58f),
                        "默认" to config.copy(tintAlpha = 0.12f, refractionStrength = 0.72f),
                        "朦胧" to config.copy(tintAlpha = 0.16f, refractionStrength = 0.86f),
                    ),
                    selectedIndex = when {
                        config.tintAlpha < 0.10f -> 0
                        config.tintAlpha > 0.14f -> 2
                        else -> 1
                    },
                    onSelect = onConfigChange,
                )
                ConfigSegmentRow(
                    title = "液态玻璃边缘折射",
                    subtitle = "一种 Lens 效果，增强边缘厚度与折射感",
                    options = listOf(
                        "关闭" to config.copy(refractionStrength = 0.35f),
                        "默认" to config.copy(refractionStrength = 0.72f),
                        "增强" to config.copy(refractionStrength = 0.92f),
                    ),
                    selectedIndex = when {
                        config.refractionStrength < 0.50f -> 0
                        config.refractionStrength > 0.82f -> 2
                        else -> 1
                    },
                    onSelect = onConfigChange,
                )
                ConfigSegmentRow(
                    title = "液态玻璃深度效果",
                    subtitle = "增强玻璃厚度、暗边与层次",
                    options = listOf(
                        "关闭" to config.copy(depth = 0.16f),
                        "默认" to config.copy(depth = 0.42f),
                        "增强" to config.copy(depth = 0.64f),
                    ),
                    selectedIndex = when {
                        config.depth < 0.25f -> 0
                        config.depth > 0.55f -> 2
                        else -> 1
                    },
                    onSelect = onConfigChange,
                )
                ConfigSegmentRow(
                    title = "液态玻璃可读性增强",
                    subtitle = "复杂/浅色壁纸下保护文字，不改变玻璃主体透明度",
                    options = listOf(
                        "较低" to config.copy(readabilityStrength = 0.12f),
                        "默认" to config.copy(readabilityStrength = 0.28f),
                        "较高" to config.copy(readabilityStrength = 0.48f),
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
                    Text("高级模式", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
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
                            title = "表面轮廓",
                            subtitle = "影响玻璃边缘高光形态",
                            options = listOf(
                                "圆角" to config.copy(surfaceProfile = GlassSurfaceProfile.Rounded),
                                "柔方" to config.copy(surfaceProfile = GlassSurfaceProfile.Squircle),
                                "圆形" to config.copy(surfaceProfile = GlassSurfaceProfile.Circle),
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
private fun ThemeSelector(
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val options = listOf(
        Triple(ThemeMode.SYSTEM, "跟随", Icons.Default.BrightnessAuto),
        Triple(ThemeMode.DARK, "深色", Icons.Default.DarkMode),
        Triple(ThemeMode.LIGHT, "浅色", Icons.Default.LightMode),
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
            "展示模式",
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
                    contentDescription = "壁纸预览",
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
                Text(if (uri != null) "更换壁纸" else "导入壁纸")
            }
            if (uri != null) {
                OutlinedButton(onClick = onClear) { Text("清除") }
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
        WallpaperScope.ALL to "全部界面",
        WallpaperScope.HOME to "仅主页",
        WallpaperScope.SETTINGS to "仅设置",
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
            Text("展示范围：", style = MaterialTheme.typography.bodySmall)
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

    val options = listOf(500 to "500 行", 1000 to "1000 行", 2000 to "2000 行", 5000 to "5000 行", 10000 to "10000 行")

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
                "日志保留：",
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
