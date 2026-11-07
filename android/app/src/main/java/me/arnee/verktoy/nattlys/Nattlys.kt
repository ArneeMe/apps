package me.arnee.verktoy.nattlys

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

object Nattlys {
    const val KOMMANDO = "adb shell pm grant me.arnee.verktoy android.permission.WRITE_SECURE_SETTINGS"
    const val KELVIN_STEG = 200
    const val KELVIN_MIN = 1200
    const val KELVIN_MAKS = 4600
    const val DIMMING_STEG = 10

    private const val PA = "night_display_activated"
    private const val KELVIN = "night_display_color_temperature"
    private const val DIMMING_PA = "reduce_bright_colors_activated"
    private const val DIMMING = "reduce_bright_colors_level"

    fun tilgang(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun hent(context: Context, navn: String, standard: Int) =
        Settings.Secure.getInt(context.contentResolver, navn, standard)

    private fun sett(context: Context, navn: String, verdi: Int) {
        Settings.Secure.putInt(context.contentResolver, navn, verdi)
    }

    fun pa(context: Context) = hent(context, PA, 0) == 1

    fun kelvin(context: Context) = hent(context, KELVIN, 2850)

    private fun lager(context: Context) = context.getSharedPreferences("nattlys", Context.MODE_PRIVATE)

    fun dimmingPa(context: Context) = lager(context).getBoolean("dimming_pa", false)

    fun dimming(context: Context) = lager(context).getInt("dimming", 50)

    fun settPa(context: Context, pa: Boolean) = sett(context, PA, if (pa) 1 else 0)

    fun endreKelvin(context: Context, endring: Int) =
        sett(context, KELVIN, (kelvin(context) + endring).coerceIn(KELVIN_MIN, KELVIN_MAKS))

    fun settDimmingPa(context: Context, pa: Boolean) {
        lager(context).edit().putBoolean("dimming_pa", pa).apply()
        sett(context, DIMMING, dimming(context))
        sett(context, DIMMING_PA, if (pa) 1 else 0)
    }

    fun endreDimming(context: Context, endring: Int) {
        val ny = (dimming(context) + endring).coerceIn(0, 100)
        lager(context).edit().putInt("dimming", ny).apply()
        sett(context, DIMMING, ny)
    }
}
