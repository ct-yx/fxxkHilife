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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.freebuds.controller.BuildConfig
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.util.LogBuffer
import com.freebuds.controller.ui.theme.ThemeMode
import com.freebuds.controller.ui.theme.saveThemeMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

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
) {
    val context = LocalContext.current
    val connState by viewModel.connectionState.collectAsState()
    val isConnected = connState is com.freebuds.controller.data.ConnectionState.Connected

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
        }
    }

    Scaffold(
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
                    current = themeMode,
                    onSelect = { mode ->
                        onThemeChange(mode)
                        saveThemeMode(context, mode)
                    }
                )
            }

            // ── 壁纸 ──
            item { SettingsHeader("壁纸") }
            item {
                WallpaperPicker(
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
                ListItem(
                    headlineContent = { Text("版本") },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
                HorizontalDivider()
            }
            item {
                val saved = viewModel.getSavedAddress() ?: "无"
                ListItem(
                    headlineContent = { Text("已保存的设备") },
                    supportingContent = { Text(saved) },
                    leadingContent = { Icon(Icons.Default.Devices, contentDescription = null) }
                )
                HorizontalDivider()
            }

            // ── 调试 ──
            item { SettingsHeader("调试") }
            if (isConnected) {
                item {
                    ListItem(
                        headlineContent = { Text("调试终端") },
                        supportingContent = { Text("查看 SPP 原始日志 / 发送命令") },
                        leadingContent = { Icon(Icons.Default.Terminal, contentDescription = null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(context, TerminalActivity::class.java))
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
                item {
                    ListItem(
                        headlineContent = { Text("分享日志") },
                        supportingContent = { Text("导出当前日志为文本文件") },
                        leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                        modifier = Modifier.clickable { viewModel.shareLog(context) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
                item {
                    LogRetentionSelector()
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
            } else {
                item {
                    ListItem(
                        headlineContent = { Text("调试功能需连接耳机后使用") },
                        leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                    )
                    HorizontalDivider()
                }
            }

            // ── 应用详情 ──────────────────────────────────────────────────────
            item { SettingsHeader("应用详情") }
            item {
                ListItem(
                    headlineContent = { Text("项目理念", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("为华为 FreeBuds 系列耳机提供第三方开源控制面板，还原官方 App 的完整功能，同时保持轻量与高效。") },
                    leadingContent = { Icon(Icons.Default.Lightbulb, contentDescription = null) }
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("GitHub") },
                    supportingContent = { Text("github.com/ct-yx/fxxkHilife") },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                    trailingContent = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ct-yx/fxxkHilife"))
                        context.startActivity(intent)
                    }
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("更新地址") },
                    supportingContent = { Text("github.com/ct-yx/fxxkHilife/releases") },
                    leadingContent = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
                    trailingContent = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ct-yx/fxxkHilife/releases"))
                        context.startActivity(intent)
                    }
                )
                HorizontalDivider()
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
private fun ThemeSelector(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val options = listOf(
        ThemeMode.SYSTEM to "跟随系统",
        ThemeMode.DARK to "深色",
        ThemeMode.LIGHT to "浅色",
    )
    val hazeState = remember { HazeState() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.medium)
    ) {
        Box(modifier = Modifier.fillMaxWidth().haze(state = hazeState)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { (mode, label) ->
                    val selected = mode == current
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(mode) },
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(vertical = 12.dp),
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
        Box(
            modifier = Modifier
                .matchParentSize()
                .hazeChild(state = hazeState) {
                    style = HazeStyle(
                        blurRadius = 10.dp,
                        tint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
                    )
                }
        )
    }

    // 圆形按钮模糊预览
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val btnHaze = remember { HazeState() }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { },
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.matchParentSize().haze(state = btnHaze))
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .hazeChild(state = btnHaze) {
                        style = HazeStyle(
                            blurRadius = 8.dp,
                            tint = HazeTint(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        )
                    }
            )
            Icon(Icons.Default.Hearing, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text("圆形按钮 haze 模糊", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
private fun WallpaperPicker(
    uri: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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

@Composable
private fun WallpaperScopeSelector(
    current: WallpaperScope,
    onSelect: (WallpaperScope) -> Unit,
) {
    val options = listOf(
        WallpaperScope.ALL to "全部界面",
        WallpaperScope.HOME to "仅主页",
        WallpaperScope.SETTINGS to "仅设置",
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("展示范围：", style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.CenterVertically))
        options.forEach { (scope, label) ->
            FilterChip(
                selected = scope == current,
                onClick = { onSelect(scope) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun LogRetentionSelector() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("fxxk_theme", android.content.Context.MODE_PRIVATE)
    var maxLines by remember {
        mutableIntStateOf(prefs.getInt("log_max_lines", LogBuffer.getMaxLines()))
    }

    val options = listOf(500 to "500 行", 1000 to "1000 行", 2000 to "2000 行", 5000 to "5000 行", 10000 to "10000 行")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
