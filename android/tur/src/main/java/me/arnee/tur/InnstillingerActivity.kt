package me.arnee.tur

import android.app.Activity
import android.content.Context
import android.os.Bundle
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vis()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val buffer = (Kart.buffer(k).walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1048576)
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
            addView(ui.box(k).apply {
                addView(Deler.verdi(k, "Lagrede kartbiter", "$buffer MB"))
                addView(ui.button(k, "Tøm") {
                    Kart.buffer(k).deleteRecursively()
                    vis()
                })
            })
        })
    }
}
