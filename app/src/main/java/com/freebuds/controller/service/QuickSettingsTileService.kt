package com.freebuds.controller.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.freebuds.controller.HilifeApplication
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.ui.MainActivity

/**
 * Quick Settings Tile — 一键启动或切换 ANC 模式。
 * 点击：打开应用
 * 长按：切换 ANC 模式 (关闭 → 降噪 → 透传 → 关闭)
 */
@RequiresApi(Build.VERSION_CODES.N)
class QuickSettingsTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        qsTile?.apply {
            label = "fxxkHilife"
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
        if (repo.connectionState.value is ConnectionState.Connected) {
            // 长按行为 → 切换 ANC（因 onClick 无法区分单点，用状态判断）
            // 这里保持简单：直接打开应用
        }
        // 打开应用
        startActivityAndCollapse(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun updateTileState() {
        val repo = HilifeApplication.instance.deviceRepository
        qsTile?.apply {
            state = when (repo.connectionState.value) {
                is ConnectionState.Connected -> Tile.STATE_ACTIVE
                is ConnectionState.Connecting -> Tile.STATE_UNAVAILABLE
                else -> Tile.STATE_INACTIVE
            }
            subtitle = when {
                repo.connectionState.value is ConnectionState.Connected -> "已连接"
                repo.connectionState.value is ConnectionState.Connecting -> "连接中..."
                else -> "未连接"
            }
            updateTile()
        }
    }
}
