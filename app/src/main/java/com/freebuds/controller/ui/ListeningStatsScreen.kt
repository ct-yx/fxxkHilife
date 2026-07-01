package com.freebuds.controller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.data.ListeningStats
import com.freebuds.controller.ui.glass.AdaptiveCard
import dev.chrisbanes.haze.HazeState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningStatsScreen(
    viewModel: DeviceViewModel,
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    onBack: () -> Unit,
) {
    val stats by viewModel.listeningStats.collectAsState()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("听音统计") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (displayMode == UiDisplayMode.LIQUID_GLASS) Color.Transparent else MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { StatsSummaryGrid(stats, displayMode, hazeState) }
            item { ListeningHeatmapCard(stats, displayMode, hazeState) }
            item {
                Text(
                    "当前统计按耳机连接时长累计；后续在佩戴状态稳定后可切换为真实佩戴听音时长。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StatsSummaryGrid(stats: ListeningStats, displayMode: UiDisplayMode, hazeState: HazeState?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile(Icons.Default.Schedule, "总时长", formatDuration(stats.totalMs), displayMode, hazeState, Modifier.weight(1f))
            StatTile(Icons.Default.TextFields, "今日", formatDuration(stats.todayMs), displayMode, hazeState, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile(Icons.Default.Book, "已听", "${stats.activeDays}天", displayMode, hazeState, Modifier.weight(1f))
            StatTile(Icons.Default.LocalFireDepartment, "连续听音", "${stats.streakDays}天", displayMode, hazeState, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    displayMode: UiDisplayMode,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    AdaptiveCard(displayMode = displayMode, hazeState = hazeState, modifier = modifier.height(142.dp)) {
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
            Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ListeningHeatmapCard(stats: ListeningStats, displayMode: UiDisplayMode, hazeState: HazeState?) {
    AdaptiveCard(displayMode = displayMode, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("听音活动", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("近 16 周", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val days = recentDays(16 * 7)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(7) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        repeat(16) { col ->
                            val day = days.getOrNull(col * 7 + row)
                            val ms = day?.let { stats.dailyMs[it] } ?: 0L
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(heatColor(ms))
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(0L, 5 * 60_000L, 30 * 60_000L, 90 * 60_000L, 180 * 60_000L).forEach { ms ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(heatColor(ms))
                    )
                }
                Text("多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun recentDays(count: Int): List<String> {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(count - 1)) }
    return List(count) {
        fmt.format(cal.time).also { cal.add(Calendar.DAY_OF_YEAR, 1) }
    }
}

private fun heatColor(ms: Long): Color = when {
    ms <= 0L -> Color(0xFFECEBE7)
    ms < 15 * 60_000L -> Color(0xFFC8C8C5)
    ms < 60 * 60_000L -> Color(0xFF9B9B98)
    ms < 120 * 60_000L -> Color(0xFF676767)
    else -> Color(0xFF2E2E2E)
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = (ms / 60_000).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}时${mins}分"
        hours > 0 -> "${hours}时"
        else -> "${mins}分"
    }
}
