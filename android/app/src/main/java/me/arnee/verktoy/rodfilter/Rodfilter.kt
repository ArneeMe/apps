package me.arnee.verktoy.rodfilter

import android.content.Context
import android.content.Intent

object Rodfilter {
    const val STEG = 10
    const val MIN = 20
    const val MAKS = 80

    private fun lager(context: Context) = context.getSharedPreferences("rodfilter", Context.MODE_PRIVATE)

    fun pa(context: Context) = lager(context).getBoolean("pa", false)

    fun styrke(context: Context) = lager(context).getInt("styrke", 50)

    fun sett(context: Context, pa: Boolean) {
        lager(context).edit().putBoolean("pa", pa).apply()
        oppdater(context)
    }

    fun endreStyrke(context: Context, endring: Int) {
        val ny = (styrke(context) + endring).coerceIn(MIN, MAKS)
        lager(context).edit().putInt("styrke", ny).apply()
        oppdater(context)
    }

    private fun oppdater(context: Context) {
        val intent = Intent(context, RodfilterService::class.java)
        if (pa(context)) context.startForegroundService(intent) else context.stopService(intent)
    }
}
