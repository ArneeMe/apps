package me.arnee.verktoy.nattlys

import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class NattmodusTile : TileService() {

    override fun onStartListening() {
        oppdater()
    }

    override fun onClick() {
        if (Nattlys.tilgang(this) && Settings.canDrawOverlays(this)) {
            Nattlys.settPa(this, !Nattlys.pa(this))
        }
        oppdater()
    }

    private fun oppdater() {
        val tile = qsTile ?: return
        tile.state = when {
            !Nattlys.tilgang(this) || !Settings.canDrawOverlays(this) -> Tile.STATE_UNAVAILABLE
            Nattlys.pa(this) -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
