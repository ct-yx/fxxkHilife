package com.freebuds.controller.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.freebuds.controller.FreeBudsApp
import com.freebuds.controller.R
import com.freebuds.controller.device.AncMode
import kotlinx.coroutines.*

@RequiresApi(Build.VERSION_CODES.N)
class AncQuickTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onTileAdded() {
        super.onTileAdded()
        // Set initial active state so tile appears usable
        updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        // Unlock the tile for interaction
        unlockAndRun {
            val dm = FreeBudsApp.instance.deviceManager
            val current = dm.state.value.ancMode
            val next = when (current) {
                AncMode.OFF -> AncMode.CANCELLATION
                AncMode.CANCELLATION -> AncMode.AWARENESS
                AncMode.AWARENESS -> AncMode.OFF
                AncMode.UNKNOWN -> AncMode.OFF
            }
            scope.launch {
                dm.setProperty("anc_mode", next.name.lowercase())
                delay(500)
                updateTile()
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
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
            tile.label = getString(R.string.tile_label)
            tile.subtitle = getString(R.string.tile_subtitle_disconnected)
        } else {
            tile.state = Tile.STATE_ACTIVE
            tile.label = when (state.ancMode) {
                AncMode.OFF -> getString(R.string.anc_off)
                AncMode.CANCELLATION -> getString(R.string.anc_noise_cancel)
                AncMode.AWARENESS -> getString(R.string.anc_awareness)
                AncMode.UNKNOWN -> getString(R.string.tile_label)
            }
            tile.subtitle = getString(R.string.tile_subtitle_tap_to_switch)
        }
        tile.updateTile()
    }
}
