package me.arnee.vaer

import android.app.Activity
import android.content.Context
import android.widget.LinearLayout
import kotlin.math.roundToInt
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class MainActivity : Activity() {
    private val varsler = mutableMapOf<String, Varsel>()
    private val feil = mutableSetOf<String>()
    private var treff: List<Sted> = emptyList()
    private var sokTekst = ""
    private var redigerer = false
    private var lang = false

    private fun lager() = getSharedPreferences("vaer", Context.MODE_PRIVATE)

    private fun steder(): List<Sted> {
        val lagret = lager().getString("steder", null) ?: "Oslo|59.9139|10.7522\nBergen|60.3913|5.3221"
        return lagret.lines().mapNotNull { linje ->
            val d = linje.split("|")
            if (d.size == 3) Sted(d[0], d[1].toDouble(), d[2].toDouble()) else null
        }
    }

    private fun lagre(liste: List<Sted>) {
        lager().edit().putString("steder", liste.joinToString("\n") { "${it.navn}|${it.lat}|${it.lon}" }).apply()
    }

    override fun onResume() {
        super.onResume()
        oppdater(false)
    }

    private fun oppdater(tving: Boolean) {
        vis()
        steder().forEach { s ->
            val gammelt = varsler[s.navn]
            if (!tving && gammelt != null && gammelt.utloper > System.currentTimeMillis()) return@forEach
            Thread {
                val resultat = runCatching { Met.varsel(s) }
                runOnUiThread {
                    resultat.onSuccess {
                        varsler[s.navn] = it
                        feil.remove(s.navn)
                    }.onFailure { feil.add(s.navn) }
                    vis()
                }
            }.start()
        }
    }

    private fun grader(t: Double) = "${t.roundToInt()}°"

    private fun dagRad(d: Dag): LinearLayout {
        val ui = WaddleUi
        val k = this
        val navn = d.dato.format(java.time.format.DateTimeFormatter.ofPattern("EEE d.", java.util.Locale.getDefault()))
        return ui.row(k, WaddleTokens.SPACE_3).apply {
            addView(ui.text(k, navn, muted = true), LinearLayout.LayoutParams(WaddleUi.dp(k, WaddleTokens.SPACE_12 + WaddleTokens.SPACE_8), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(ui.icon(k, Met.ikon(d.symbol)))
            addView(ui.column(k, 0).apply {
                addView(ui.text(k, Met.ord(d.symbol)))
                if (d.nedbor >= 0.5) addView(ui.text(k, "Regn %.1f mm".format(d.nedbor), WaddleTokens.TEXT_SM, bold = true).apply { setTextColor(ui.palette(k).accent) })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(ui.text(k, "${d.min.roundToInt()}–${grader(d.maks)}", bold = true))
        }
    }

    private fun grupper(timer: List<Time>): List<List<Time>> {
        val ut = mutableListOf<MutableList<Time>>()
        for (t in timer) {
            val siste = ut.lastOrNull()
            val nyDag = siste != null && t.time == 0
            if (siste != null && !nyDag && siste.size < 6 && Met.ord(siste[0].symbol) == Met.ord(t.symbol) && (siste[0].nedbor > 0) == (t.nedbor > 0)) {
                siste += t
            } else {
                ut += mutableListOf(t)
            }
        }
        return ut
    }

    private fun rad(g: List<Time>, etikett: String?): LinearLayout {
        val ui = WaddleUi
        val k = this
        val forste = g.first()
        val tid = etikett ?: if (g.size == 1) "%02d".format(forste.time) else "%02d–%02d".format(forste.time, (g.last().time + 1) % 24)
        val min = g.minOf { it.temp }
        val maks = g.maxOf { it.temp }
        val nedbor = g.sumOf { it.nedbor }
        return ui.row(k, WaddleTokens.SPACE_3).apply {
            addView(ui.text(k, tid, muted = true), LinearLayout.LayoutParams(WaddleUi.dp(k, WaddleTokens.SPACE_12 + WaddleTokens.SPACE_8), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(ui.icon(k, Met.ikon(forste.symbol)))
            addView(ui.column(k, 0).apply {
                addView(ui.text(k, Met.ord(forste.symbol)))
                if (nedbor > 0) addView(ui.text(k, "Regn %.1f mm".format(nedbor), WaddleTokens.TEXT_SM, bold = true).apply { setTextColor(ui.palette(k).accent) })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(ui.text(k, if (min.roundToInt() == maks.roundToInt()) grader(min) else "${min.roundToInt()}–${grader(maks)}", bold = true))
        }
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Vær", if (redigerer) "check" else "pencil", if (redigerer) "Ferdig" else "Rediger steder") {
                redigerer = !redigerer
                treff = emptyList()
                vis()
            })
            steder().forEach { s ->
                addView(ui.box(k).apply {
                    val v = varsler[s.navn]
                    val na = v?.timer?.firstOrNull()
                    if (na != null) {
                        addView(ui.row(k, WaddleTokens.SPACE_3).apply {
                            addView(ui.icon(k, Met.ikon(na.symbol), 48))
                            addView(ui.column(k, WaddleTokens.SPACE_1).apply {
                                addView(ui.text(k, s.navn.substringBefore(","), WaddleTokens.TEXT_LG, bold = true))
                                addView(ui.text(k, "${Met.ord(na.symbol)}, ${na.vind.roundToInt()} m/s", muted = true))
                            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                            addView(ui.text(k, grader(na.temp), WaddleTokens.TEXT_XL, bold = true))
                        })
                        val regn = v.timer.drop(1).take(12).firstOrNull { it.nedbor > 0 }
                        addView(if (regn != null) {
                            ui.text(k, "Regn fra kl. %02d".format(regn.time), bold = true).apply { setTextColor(ui.palette(k).accent) }
                        } else {
                            ui.text(k, "Ikke regn de neste 12 timene", muted = true)
                        })
                        addView(ui.divider(k))
                        v.timer.getOrNull(1)?.let { t -> addView(rad(listOf(t), "Neste time")) }
                        grupper(v.timer.drop(2).take(24)).forEach { g ->
                            if (g.first().time == 0) addView(ui.text(k, "I morgen", WaddleTokens.TEXT_SM, muted = true))
                            addView(rad(g, null))
                        }
                    } else {
                        addView(ui.text(k, s.navn.substringBefore(","), WaddleTokens.TEXT_LG, bold = true))
                        addView(ui.text(k, if (s.navn in feil) "Fikk ikke hentet været. Sjekk nettet og trykk Oppdater." else "Henter", muted = true))
                    }
                    if (lang && v != null && v.dager.isNotEmpty()) {
                        addView(ui.divider(k))
                        v.dager.forEach { d -> addView(dagRad(d)) }
                    }
                    if (redigerer) addView(ui.button(k, "Fjern") {
                        lagre(steder().filter { it.navn != s.navn })
                        vis()
                    })
                })
            }
            addView(ui.row(k).apply {
                addView(ui.button(k, if (lang) "Kortere" else "Neste dager", primary = !redigerer) {
                    lang = !lang
                    vis()
                })
                addView(ui.button(k, "Oppdater") { oppdater(true) })
            })
            if (redigerer) addView(ui.box(k).apply {
                addView(ui.text(k, "Legg til sted", WaddleTokens.TEXT_LG, bold = true))
                val felt = Deler.felt(k, "Stedsnavn").apply {
                    setText(sokTekst)
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) {
                            sokTekst = s.toString()
                        }
                    })
                }
                addView(felt)
                addView(ui.button(k, "Søk") {
                    val tekst = sokTekst.trim()
                    if (tekst.isEmpty()) return@button
                    Thread {
                        val resultat = runCatching { Met.sok(tekst) }
                        runOnUiThread {
                            treff = resultat.getOrDefault(emptyList())
                            vis()
                        }
                    }.start()
                })
                treff.forEach { t ->
                    addView(Deler.klikkbar(ui.listRow(k, t.navn, null, iconName = "map-pin")) {
                        if (steder().none { it.navn == t.navn }) lagre(steder() + t)
                        treff = emptyList()
                        sokTekst = ""
                        oppdater(false)
                    })
                }
            })
            addView(ui.text(k, "Data fra MET Norge og Kartverket.", WaddleTokens.TEXT_SM, muted = true))
        }
        ui.screen(this, innhold)
        Deler.tastatur(this)
    }
}
