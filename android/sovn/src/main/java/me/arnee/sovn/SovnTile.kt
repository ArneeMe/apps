package me.arnee.sovn

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class SovnTile : TileService() {

    override fun onStartListening() {
        oppdater()
    }

    override fun onClick() {
        if (Timer.gaar(this)) Timer.stopp(this) else Timer.start(this, Timer.STANDARD)
        oppdater()
    }

    private fun oppdater() {
        val tile = qsTile ?: return
        val gaar = Timer.gaar(this)
        tile.state = if (gaar) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (gaar) "${Timer.minutterIgjen(this)} min igjen" else "${Timer.STANDARD} min"
        }
        tile.updateTile()
    }
}
