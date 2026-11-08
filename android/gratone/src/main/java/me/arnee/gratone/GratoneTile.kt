package me.arnee.gratone

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class GratoneTile : TileService() {

    override fun onStartListening() {
        oppdater()
    }

    override fun onClick() {
        if (Gratone.tilgang(this)) Gratone.sett(this, !Gratone.pa(this))
        oppdater()
    }

    private fun oppdater() {
        val tile = qsTile ?: return
        tile.state = when {
            !Gratone.tilgang(this) -> Tile.STATE_UNAVAILABLE
            Gratone.pa(this) -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
