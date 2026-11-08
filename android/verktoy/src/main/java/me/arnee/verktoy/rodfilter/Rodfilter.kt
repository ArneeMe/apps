package me.arnee.verktoy.rodfilter

import android.content.Context
import android.content.Intent
import me.arnee.verktoy.nattlys.Nattlys

object Rodfilter {
    const val MAKS = 80

    private fun lager(context: Context) = context.getSharedPreferences("rodfilter", Context.MODE_PRIVATE)

    fun pa(context: Context) = lager(context).getBoolean("pa", false)

    fun styrke(context: Context) = lager(context).getInt("styrke", 0)

    fun sett(context: Context, pa: Boolean) {
        lager(context).edit().putBoolean("pa", pa).apply()
        oppdater(context)
    }

    fun settStyrke(context: Context, styrke: Int) {
        lager(context).edit().putInt("styrke", styrke).apply()
        if (Nattlys.pa(context)) sett(context, styrke > 0) else oppdater(context)
    }

    private fun oppdater(context: Context) {
        val tjeneste = Intent(context, RodfilterService::class.java)
        if (Nattlys.tilgang(context) && FilterTjeneste.slaPa(context)) {
            context.stopService(tjeneste)
            FilterTjeneste.aktiv?.oppdater()
            return
        }
        if (pa(context)) context.startForegroundService(tjeneste) else context.stopService(tjeneste)
    }
}
