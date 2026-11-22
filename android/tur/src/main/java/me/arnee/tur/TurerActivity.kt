package me.arnee.tur

import android.app.Activity
import android.content.Intent
import java.io.File
import java.util.Calendar
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class TurerActivity : Activity() {
    private var filter = "alle"
    private var turer: List<Sammendrag>? = null

    override fun onResume() {
        super.onResume()
        vis()
        Thread {
            val liste = Turer.oversikt(this)
            runOnUiThread {
                turer = liste
                vis()
            }
        }.start()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val alle = turer
        ui.screen(k, ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Turer"))
            when {
                alle == null -> addView(ui.text(k, "Henter", muted = true))
                alle.isEmpty() -> addView(ui.text(k, "Ingen turer ennå.", muted = true))
                else -> {
                    addView(ui.row(k).apply {
                        listOf("alle" to "Alle", "running" to "Løp", "hiking" to "Tur").forEach { (n, tekst) ->
                            addView(ui.button(k, tekst, primary = filter == n) {
                                filter = n
                                vis()
                            }, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                        }
                    })
                    val valgte = alle.filter { filter == "alle" || it.type == filter || (filter == "hiking" && it.type != "running") }
                    addView(ui.box(k).apply {
                        val na = Calendar.getInstance()
                        val uke = (na.clone() as Calendar).apply {
                            add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }.timeInMillis
                        listOf(
                            "Denne uka" to valgte.filter { it.start >= uke },
                            "Denne måneden" to valgte.filter { samme(it.start, na, Calendar.MONTH) },
                            "I år" to valgte.filter { samme(it.start, na, Calendar.YEAR) },
                        ).forEach { (navn, t) ->
                            addView(Deler.verdi(k, navn, "${t.size} · ${Vis.km(t.sumOf { it.lengde })} · ${Vis.tid(t.sumOf { it.tid })}"))
                        }
                    })
                    valgte.groupBy { Vis.maned(it.start) }.forEach { (maned, liste) ->
                        addView(ui.column(k, WaddleTokens.SPACE_2).apply {
                            addView(ui.text(k, "$maned · ${Vis.km(liste.sumOf { it.lengde })}", bold = true))
                            addView(ui.box(k).apply {
                                liste.forEachIndexed { i, t ->
                                    if (i > 0) addView(ui.divider(k))
                                    val maal = if (t.type == "running") Vis.tempo(t.tid, t.lengde) + " /km" else Vis.fart(t.tid, t.lengde) + " km/t"
                                    val tittel = (if (t.egetNavn) t.navn + " · " else "") + "${Vis.type(t.type)} · ${Vis.km(t.lengde)}"
                                    addView(Deler.klikkbar(ui.listRow(k, tittel, "${Vis.dato(t.start)} · ${Vis.tid(t.tid)} · $maal", if (t.type == "running") "directions" else "tree")) {
                                        startActivity(Intent(k, TurActivity::class.java).putExtra("fil", File(Turer.mappe(k), t.fil).path))
                                    })
                                }
                            })
                        })
                    }
                }
            }
        })
    }

    private fun samme(ms: Long, na: Calendar, felt: Int): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return c.get(Calendar.YEAR) == na.get(Calendar.YEAR) && (felt == Calendar.YEAR || c.get(felt) == na.get(felt))
    }
}
