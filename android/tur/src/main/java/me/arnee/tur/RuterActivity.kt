package me.arnee.tur

import android.app.Activity
import android.content.Intent
import android.widget.LinearLayout
import kotlin.math.roundToInt
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class RuterActivity : Activity() {
    private var valgt: String? = null

    override fun onResume() {
        super.onResume()
        vis()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val alle = Ruter.alle(k).sortedBy { it.navn.lowercase() }
        val mapper = alle.map { it.mappe }.distinct().sortedWith(compareBy({ it.isEmpty() }, { it.lowercase() }))
        if (valgt != null && valgt !in mapper) valgt = null
        ui.screen(k, ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Ruter"))
            if (alle.isEmpty()) {
                addView(ui.text(k, "Ingen lagrede ruter ennå. Lagre en rute fra Planlegg, fra en GPX-fil eller fra en tur du har gått.", muted = true))
                return@apply
            }
            if (mapper.size > 1) {
                val valg = listOf<String?>(null) + mapper
                valg.chunked(3).forEach { rad ->
                    addView(ui.row(k).apply {
                        rad.forEach { m ->
                            val tekst = when (m) {
                                null -> "Alle"
                                "" -> "Uten mappe"
                                else -> m
                            }
                            addView(ui.button(k, tekst, primary = valgt == m) {
                                valgt = m
                                vis()
                            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                        }
                        repeat(3 - rad.size) { addView(android.view.View(k), LinearLayout.LayoutParams(0, 0, 1f)) }
                    })
                }
            }
            (if (valgt == null) mapper else listOf(valgt!!)).forEach { m ->
                val ruter = alle.filter { it.mappe == m }
                if (ruter.isEmpty()) return@forEach
                addView(ui.column(k, WaddleTokens.SPACE_2).apply {
                    addView(ui.text(k, "${m.ifEmpty { "Uten mappe" }} · ${ruter.size}", bold = true))
                    addView(ui.box(k).apply {
                        ruter.forEachIndexed { i, r ->
                            if (i > 0) addView(ui.divider(k))
                            val detalj = Vis.km(r.lengde) + if (r.stigning > 0) " · ${r.stigning.roundToInt()} høydemeter" else ""
                            addView(Deler.klikkbar(ui.listRow(k, r.navn, detalj, "map")) {
                                startActivity(Intent(k, RuteActivity::class.java).putExtra("id", r.id))
                            })
                        }
                    })
                })
            }
        })
    }
}
