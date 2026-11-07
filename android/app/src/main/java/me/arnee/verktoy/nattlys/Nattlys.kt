package me.arnee.verktoy.nattlys

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import me.arnee.verktoy.rodfilter.Rodfilter

object Nattlys {
    const val KOMMANDO = "adb shell pm grant me.arnee.verktoy android.permission.WRITE_SECURE_SETTINGS"
    const val KELVIN_MIN = 1200
    const val KELVIN_MAKS = 4600

    private const val PA = "night_display_activated"
    private const val KELVIN = "night_display_color_temperature"
    private const val DIMMING_PA = "reduce_bright_colors_activated"
    private const val DIMMING = "reduce_bright_colors_level"

    fun tilgang(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun lager(context: Context) = context.getSharedPreferences("nattlys", Context.MODE_PRIVATE)

    private fun sett(context: Context, navn: String, verdi: Int) {
        Settings.Secure.putInt(context.contentResolver, navn, verdi)
    }

    fun pa(context: Context) = Settings.Secure.getInt(context.contentResolver, PA, 0) == 1

    fun kelvin(context: Context) = Settings.Secure.getInt(context.contentResolver, KELVIN, 2850)

    fun lysstyrke(context: Context) = lager(context).getInt("lysstyrke", 100)

    fun settPa(context: Context, pa: Boolean) {
        sett(context, PA, if (pa) 1 else 0)
        sett(context, DIMMING, 100 - lysstyrke(context))
        sett(context, DIMMING_PA, if (pa && lysstyrke(context) < 100) 1 else 0)
        Rodfilter.sett(context, pa && Rodfilter.styrke(context) > 0)
    }

    fun settKelvin(context: Context, kelvin: Int) = sett(context, KELVIN, kelvin)

    fun settLysstyrke(context: Context, lysstyrke: Int) {
        lager(context).edit().putInt("lysstyrke", lysstyrke).apply()
        sett(context, DIMMING, 100 - lysstyrke)
        if (pa(context)) sett(context, DIMMING_PA, if (lysstyrke < 100) 1 else 0)
    }
}
