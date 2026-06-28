package com.freebuds.controller.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.freebuds.controller.HilifeApplication
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.ui.MainActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Quick Settings Tile — 一键切换 ANC 模式 / 打开应用。
 * 已连接时：点击切换 ANC (关闭 → 降噪 → 透传 → 关闭)
 * 未连接时：点击打开应用
 */
@RequiresApi(Build.VERSION_CODES.N)
class QuickSettingsTileService : TileService() {

    private val scope = MainScope()

    override fun onTileAdded() {
        super.onTileAdded()
        qsTile?.apply {
            label = "ANC"
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val repo = HilifeApplication.instance.deviceRepository
        val connState = repo.connectionState.value
        if (connState is ConnectionState.Connected) {
            // 切换 ANC 模式
            val currentMode = repo.props.value.ancMode
            val nextMode = when (currentMode) {
                "normal" -> "cancellation"
                "cancellation" -> "awareness"
                "awareness" -> "normal"
                else -> "normal"
            }
            scope.launch {
                repo.setProperty("anc", "mode", nextMode)
            }
            // 延迟后更新 Tile 状态
            android.os.Handler(mainLooper).postDelayed({ updateTileState() }, 800)
        } else {
            // 未连接 → 打开应用
            startActivityAndCollapse(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun updateTileState() {
        val repo = HilifeApplication.instance.deviceRepository
        val connState = repo.connectionState.value
        qsTile?.apply {
            state = when (connState) {
                is ConnectionState.Connected -> Tile.STATE_ACTIVE
                is ConnectionState.Connecting -> Tile.STATE_UNAVAILABLE
                else -> Tile.STATE_INACTIVE
            }
            subtitle = when (connState) {
                is ConnectionState.Connected -> {
                    val ancLabel = when (repo.props.value.ancMode) {
                        "cancellation" -> "降噪"
                        "awareness" -> "透传"
                        "normal" -> "关闭"
                        else -> repo.props.value.ancMode
                    }
                    "已连接 · $ancLabel"
                }
                is ConnectionState.Connecting -> "连接中…"
                else -> "未连接"
            }
            updateTile()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
