package me.arnee.tur

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

object Innst {
    private fun prefs(k: Context) = k.getSharedPreferences("innst", Context.MODE_PRIVATE)

    fun kart(k: Context) = prefs(k).getString("kart", "topo") ?: "topo"

    fun skjerm(k: Context) = prefs(k).getBoolean("skjerm", false)

    fun type(k: Context) = prefs(k).getString("type", "running") ?: "running"

    fun sett(k: Context, nokkel: String, verdi: Any) {
        prefs(k).edit().apply { if (verdi is Boolean) putBoolean(nokkel, verdi) else putString(nokkel, verdi.toString()) }.apply()
    }
}

class InnstillingerActivity : Activity() {
    private var buffer: Long? = null

    override fun onResume() {
        super.onResume()
        vis()
        Thread {
            val mb = Kart.buffer(this).walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1048576
            runOnUiThread {
                buffer = mb
                vis()
            }
        }.start()
    }

    private fun apne(handling: String, pakke: Boolean = true) {
        val i = Intent(handling)
        if (pakke) i.data = Uri.parse("package:$packageName")
        i.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        runCatching { startActivity(i) }.onFailure { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val presis = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val omtrent = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val gps = getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER)
        val batteri = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val varsler = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        ui.screen(k, ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Innstillinger"))
            addView(ui.box(k).apply {
                addView(Deler.verdi(k, "Kart", if (Innst.kart(k) == "osm") "OpenStreetMap" else "Topo") {
                    Innst.sett(k, "kart", if (Innst.kart(k) == "osm") "topo" else "osm")
                    vis()
                })
                addView(ui.divider(k))
                addView(Deler.verdi(k, "Skjermen på under opptak", if (Innst.skjerm(k)) "På" else "Av") {
                    Innst.sett(k, "skjerm", !Innst.skjerm(k))
                    vis()
                })
            })
            addView(ui.text(k, "Topo er fra Kartverket og dekker Norge. OpenStreetMap dekker hele verden. Løp tar GPS hvert sekund, tur hvert 5. sekund for å spare batteri.", muted = true))
            addView(ui.text(k, "Telefonen", WaddleTokens.TEXT_LG, bold = true))
            addView(ui.box(k).apply {
                addView(Deler.verdi(k, "Posisjon", if (presis) "Presis" else if (omtrent) "Omtrentlig, endre" else "Av, slå på") {
                    apne(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                })
                addView(ui.divider(k))
                addView(Deler.verdi(k, "GPS", if (gps) "På" else "Av, slå på") {
                    apne(Settings.ACTION_LOCATION_SOURCE_SETTINGS, false)
                })
                addView(ui.divider(k))
                addView(Deler.verdi(k, "Batterisparing", if (batteri) "Av" else "På, slå av") {
                    apne(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, false)
                })
                addView(ui.divider(k))
                addView(Deler.verdi(k, "Varsel under opptak", if (varsler) "På" else "Av, slå på") {
                    apne(Settings.ACTION_APP_NOTIFICATION_SETTINGS, false)
                })
            })
            addView(ui.text(k, "Posisjon må være presis, ellers blir sporet hakkete. Tillat når appen er i bruk holder, siden opptaket går som varsel. Slå av batterisparing for Tur, så opptaket ikke stoppes med skjermen av. På /e/OS: åpne Avansert personvern og sjekk at Tur får ekte posisjon.", muted = true))
            addView(ui.box(k).apply {
                addView(Deler.verdi(k, "Lagrede kartbiter", buffer?.let { "$it MB" } ?: "Teller"))
                addView(ui.button(k, "Tøm") {
                    buffer = null
                    vis()
                    Thread {
                        Kart.buffer(k).deleteRecursively()
                        runOnUiThread {
                            buffer = 0
                            vis()
                        }
                    }.start()
                })
            })
        })
    }
}
