package me.arnee.tur

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class VarmekartActivity : Activity() {
    private lateinit var kart: Kart
    private lateinit var info: TextView
    private lateinit var valg: LinearLayout
    private var filter = "alle"
    private var turer: List<Pair<String, List<Punkt>>>? = null
    private var vist = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = WaddleUi
        val k = this
        kart = Kart(k)
        info = ui.text(k, "Henter turene", muted = true)
        valg = ui.row(k)
        ui.screen(k, ui.column(k, WaddleTokens.SPACE_4).apply {
            addView(Deler.tittel(k, "Varmekart"))
            addView(valg)
            addView(Kart.ramme(k, kart), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, resources.displayMetrics.heightPixels * 3 / 5))
            addView(info)
        })
        vis()
        Thread {
            val liste = Turer.oversikt(k).mapNotNull { t ->
                val s = Turer.les(File(Turer.mappe(k), t.fil)) ?: return@mapNotNull null
                if (s.punkter.size < 2) null else t.type to s.punkter.groupBy { it.seg }.values.flatMap { Deling.forenkle(it, 5.0) }
            }
            runOnUiThread {
                turer = liste
                vis()
            }
        }.start()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        valg.removeAllViews()
        listOf("alle" to "Alle", "running" to "Løp", "hiking" to "Tur").forEach { (n, tekst) ->
            valg.addView(ui.button(k, tekst, primary = filter == n) {
                filter = n
                vis()
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val alle = turer ?: return
        val valgte = alle.filter { (type, _) -> filter == "alle" || type == filter || (filter == "hiking" && type != "running") }.map { it.second }
        kart.varme = valgte
        info.text = if (valgte.isEmpty()) "Ingen turer her ennå." else "${valgte.size} turer. Jo sterkere farge, jo oftere har du vært der."
        if (!vist && valgte.isNotEmpty()) {
            kart.vis(valgte.flatten())
            vist = true
        }
    }
}
