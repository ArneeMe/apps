package me.arnee.gratone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

object Gratone {
    const val KOMMANDO = "adb shell pm grant me.arnee.gratone android.permission.WRITE_SECURE_SETTINGS"

    private const val PA = "accessibility_display_daltonizer_enabled"
    private const val MODUS = "accessibility_display_daltonizer"
    private const val SVART_HVITT = 0

    fun tilgang(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun lager(context: Context) = context.getSharedPreferences("gratone", Context.MODE_PRIVATE)

    fun pa(context: Context): Boolean = try {
        Settings.Secure.getInt(context.contentResolver, PA, 0) == 1 &&
            Settings.Secure.getInt(context.contentResolver, MODUS, -1) == SVART_HVITT
    } catch (e: SecurityException) {
        lager(context).getBoolean("pa", false)
    }

    fun sett(context: Context, pa: Boolean) {
        lager(context).edit().putBoolean("pa", pa).apply()
        if (pa) Settings.Secure.putInt(context.contentResolver, MODUS, SVART_HVITT)
        Settings.Secure.putInt(context.contentResolver, PA, if (pa) 1 else 0)
    }
}
