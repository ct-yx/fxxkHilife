package com.freebuds.controller.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.freebuds.controller.FreeBudsApp
import com.freebuds.controller.R
import com.freebuds.controller.device.AncMode
import kotlinx.coroutines.*

/**
 * Quick Settings Tile: Noise Cancel ON/OFF toggle.
 * Two-state tile: ACTIVE = Noise Cancel ON, INACTIVE = OFF.
 */
@RequiresApi(Build.VERSION_CODES.N)
class NoiseCancelTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            val dm = FreeBudsApp.instance.deviceManager
            val current = dm.state.value.ancMode
            val enable = current != AncMode.CANCELLATION
            scope.launch {
                dm.setProperty("anc_mode", if (enable) "cancellation" else "normal")
                delay(500)
                updateTile()
            }
        }
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        scope.cancel()
    }

    private fun updateTile() {
        val dm = FreeBudsApp.instance.deviceManager
        val state = dm.state.value
        val tile = qsTile ?: return

        if (!state.connected) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_nc_label)
            tile.subtitle = getString(R.string.tile_subtitle_disconnected)
        } else {
            val active = state.ancMode == AncMode.CANCELLATION
            tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_nc_label)
            tile.subtitle = if (active) getString(R.string.anc_noise_cancel) else getString(R.string.anc_off)
        }
        tile.updateTile()
    }
}

/**
 * Quick Settings Tile: Awareness ON/OFF toggle.
 * Two-state tile: ACTIVE = Awareness ON, INACTIVE = OFF.
 */
@RequiresApi(Build.VERSION_CODES.N)
class AwarenessTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            val dm = FreeBudsApp.instance.deviceManager
            val current = dm.state.value.ancMode
            val enable = current != AncMode.AWARENESS
            scope.launch {
                dm.setProperty("anc_mode", if (enable) "awareness" else "normal")
                delay(500)
                updateTile()
            }
        }
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        scope.cancel()
    }

    private fun updateTile() {
        val dm = FreeBudsApp.instance.deviceManager
        val state = dm.state.value
        val tile = qsTile ?: return

        if (!state.connected) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_aw_label)
            tile.subtitle = getString(R.string.tile_subtitle_disconnected)
        } else {
            val active = state.ancMode == AncMode.AWARENESS
            tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_aw_label)
            tile.subtitle = if (active) getString(R.string.anc_awareness) else getString(R.string.anc_off)
        }
        tile.updateTile()
    }
}
