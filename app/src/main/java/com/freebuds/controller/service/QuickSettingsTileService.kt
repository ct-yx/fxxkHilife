package com.freebuds.controller.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.freebuds.controller.HilifeApplication
import com.freebuds.controller.R
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.i18n.I18n
import com.freebuds.controller.ui.MainActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * Quick Settings Tile — 一键切换 ANC 模式 / 打开应用。
 * 已连接时：点击切换 ANC (关闭 → 降噪 → 透传 → 关闭)
 * 未连接时：点击打开应用
 */
@RequiresApi(Build.VERSION_CODES.N)
class QuickSettingsTileService : TileService() {

    private val scope = MainScope()
    private var propsJob: Job? = null

    override fun onTileAdded() {
        super.onTileAdded()
        qsTile?.apply {
            label = I18n.t("tile.anc")
            icon = tileIconForMode("normal")
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()

        // v2.7.0 修复：响应式更新（props 变化时自动刷新 Tile）
        if (propsJob == null) {
            propsJob = scope.launch {
                HilifeApplication.instance.deviceRepository.props.collect {
                    updateTileState()
                }
            }
        }
    }

    override fun onStopListening() {
        propsJob?.cancel()
        propsJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val repo = HilifeApplication.instance.deviceRepository
        when (repo.connectionState.value) {
            is ConnectionState.Connected -> cycleAncMode()
            is ConnectionState.Connecting -> updateTileState()
            else -> connectLastSavedOrOpenApp()
        }
    }

    private fun cycleAncMode() {
        val repo = HilifeApplication.instance.deviceRepository
        val currentMode = repo.props.value.ancMode ?: "normal"
        val nextMode = nextAncMode(currentMode)

        // 乐观刷新 Tile：点下去立刻看到目标状态，不等设备回包
        qsTile?.apply {
            label = "${I18n.t("tile.anc")}: ${ancLabel(nextMode)}"
            subtitle = I18n.t("tile.switching")
            icon = tileIconForMode(nextMode)
            state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                stateDescription = "正在切换到${ancLabel(nextMode)}模式"
            }
            updateTile()
        }

        scope.launch {
            repo.setProperty("anc", "mode", nextMode)
            delay(300)
            updateTileState()
        }
    }

    private fun connectLastSavedOrOpenApp() {
        val repo = HilifeApplication.instance.deviceRepository
        if (repo.autoConnectLastSaved()) {
            qsTile?.apply {
                label = I18n.t("tile.anc")
                subtitle = I18n.t("tile.connecting")
                icon = tileIconForMode("normal")
                state = Tile.STATE_UNAVAILABLE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    stateDescription = "正在连接已和手机蓝牙连接的耳机"
                }
                updateTile()
            }
        } else {
            startActivityAndCollapse(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun updateTileState() {
        val repo = HilifeApplication.instance.deviceRepository
        val connState = repo.connectionState.value
        qsTile?.apply {
            when (connState) {
                is ConnectionState.Connected -> {
                    val currentMode = repo.props.value.ancMode ?: "normal"
                    val nextMode = nextAncMode(currentMode)
                    label = "${I18n.t("tile.anc")}: ${ancLabel(currentMode)}"
                    subtitle = I18n.t("tile.connected.tap_to", ancLabel(nextMode))
                    icon = tileIconForMode(currentMode)
                    state = Tile.STATE_ACTIVE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        stateDescription = "当前${ancLabel(currentMode)}模式，点按切到${ancLabel(nextMode)}模式"
                    }
                }
                is ConnectionState.Connecting -> {
                    label = I18n.t("tile.anc")
                    subtitle = I18n.t("tile.connecting")
                    icon = tileIconForMode("normal")
                    state = Tile.STATE_UNAVAILABLE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        stateDescription = "耳机连接中"
                    }
                }
                is ConnectionState.Failed -> {
                    label = I18n.t("tile.anc")
                    subtitle = I18n.t("tile.connection_failed_retry")
                    icon = tileIconForMode("normal")
                    state = Tile.STATE_INACTIVE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        stateDescription = "连接失败，点按重新连接耳机"
                    }
                }
                else -> {
                    label = I18n.t("tile.anc")
                    subtitle = if (repo.getSavedAddress() != null) I18n.t("tile.tap_connect") else I18n.t("tile.add_device_first")
                    icon = tileIconForMode("normal")
                    state = Tile.STATE_INACTIVE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        stateDescription = subtitle.toString()
                    }
                }
            }
            updateTile()
        }
    }

    private fun nextAncMode(mode: String?): String = when (mode) {
        "normal" -> "cancellation"
        "cancellation" -> "awareness"
        "awareness" -> "normal"
        else -> "normal"
    }

    private fun ancLabel(mode: String?): String = when (mode) {
        "cancellation" -> "降噪"
        "awareness" -> "透传"
        "normal" -> "关闭"
        else -> "关闭"
    }

    private fun tileIconForMode(mode: String?): Icon = Icon.createWithResource(
        this,
        when (mode) {
            "cancellation" -> R.drawable.ic_anc_cancellation
            "awareness" -> R.drawable.ic_anc_awareness
            "normal" -> R.drawable.ic_anc_normal
            else -> R.drawable.ic_anc_normal
        }
    )

    override fun onDestroy() {
        super.onDestroy()
    }
}
