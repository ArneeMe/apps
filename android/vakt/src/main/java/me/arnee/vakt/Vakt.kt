package me.arnee.vakt

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

object Vakt {
    val VALG = listOf(0, 5, 10, 15, 20)

    private fun lager(context: Context) = context.getSharedPreferences("vakt", Context.MODE_PRIVATE)
    private fun dpm(context: Context) = context.getSystemService(DevicePolicyManager::class.java)

    fun admin(context: Context) = ComponentName(context, Admin::class.java)
    fun aktiv(context: Context) = dpm(context).isAdminActive(admin(context))

    fun grense(context: Context) = lager(context).getInt("grense", 0)
    fun settGrense(context: Context, forsok: Int) = lager(context).edit().putInt("grense", forsok).apply()

    fun feil(context: Context) = lager(context).getInt("feil", 0)

    fun las(context: Context) {
        if (aktiv(context)) dpm(context).lockNow()
    }

    fun mislykket(context: Context) {
        val antall = runCatching { dpm(context).currentFailedPasswordAttempts }.getOrDefault(feil(context) + 1)
        lager(context).edit().putInt("feil", antall).apply()
        val g = grense(context)
        if (g > 0 && antall >= g) dpm(context).wipeData(0)
    }

    fun lyktes(context: Context) = lager(context).edit().putInt("feil", 0).apply()
}

class Admin : DeviceAdminReceiver() {
    override fun onPasswordFailed(context: Context, intent: Intent, user: UserHandle) = Vakt.mislykket(context)
    override fun onPasswordSucceeded(context: Context, intent: Intent, user: UserHandle) = Vakt.lyktes(context)
}

class LasTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = if (Vakt.aktiv(this@LasTile)) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            updateTile()
        }
    }

    override fun onClick() = Vakt.las(this)
}
