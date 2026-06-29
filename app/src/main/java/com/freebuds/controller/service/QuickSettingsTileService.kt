package com.freebuds.controller.service

import android.bluetooth.BluetoothAdapter
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
            label = "ANC"
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
            label = "ANC: ${ancLabel(nextMode)}"
            subtitle = "正在切换…"
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
        val address = repo.getSavedAddress()
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device = runCatching { address?.let { adapter?.getRemoteDevice(it) } }.getOrNull()

        if (device != null) {
            qsTile?.apply {
                label = "ANC"
                subtitle = "正在连接耳机…"
                state = Tile.STATE_UNAVAILABLE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    stateDescription = "正在连接上次保存的耳机"
                }
                updateTile()
            }
            repo.connect(device)
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
                    label = "ANC: ${ancLabel(currentMode)}"
                    subtitle = "点按切到${ancLabel(nextMode)}"
                    state = Tile.STATE_ACTIVE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        stateDescription = "当前${ancLabel(currentMode)}模式，点按切到${ancLabel(nextMode)}模式"
                    }
                }
                is ConnectionState.Connecting -> {
                    label = "ANC"
                    subtitle = "连接中…"
                    state = Tile.STATE_UNAVAILABLE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        stateDescription = "耳机连接中"
                    }
                }
                is ConnectionState.Failed -> {
                    label = "ANC"
                    subtitle = "连接失败，点按重试"
                    state = Tile.STATE_INACTIVE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        stateDescription = "连接失败，点按重新连接耳机"
                    }
                }
                else -> {
                    label = "ANC"
                    subtitle = if (repo.getSavedAddress() != null) "点按连接耳机" else "先在应用内添加耳机"
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

    override fun onDestroy() {
        super.onDestroy()
    }
}
