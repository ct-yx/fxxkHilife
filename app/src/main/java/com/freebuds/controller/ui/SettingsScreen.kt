package com.freebuds.controller.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.freebuds.controller.BuildConfig
import com.freebuds.controller.data.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val connState by viewModel.connectionState.collectAsState()
    val isConnected = connState is com.freebuds.controller.data.ConnectionState.Connected

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
            // ── 关于 ──
            item {
                ListItem(
                    headlineContent = { Text("版本") },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
                HorizontalDivider()
            }

            // ── 保存的设备 ──
            item {
                val saved = viewModel.getSavedAddress() ?: "无"
                ListItem(
                    headlineContent = { Text("已保存的设备") },
                    supportingContent = { Text(saved) },
                    leadingContent = { Icon(Icons.Default.Devices, contentDescription = null) }
                )
                HorizontalDivider()
            }

            // ── 调试模式（仅连接后可用） ──
            item {
                SettingsHeader("调试")
            }
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
            } else {
                item {
                    ListItem(
                        headlineContent = { Text("调试功能需连接耳机后使用") },
                        leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

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
