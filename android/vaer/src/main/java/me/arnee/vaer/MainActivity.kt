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

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Vær"))
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
                        addView(ui.divider(k))
                        v.timer.drop(1).take(12).forEach { t ->
                            addView(ui.row(k, WaddleTokens.SPACE_3).apply {
                                addView(ui.text(k, "%02d".format(t.time), muted = true), LinearLayout.LayoutParams(WaddleUi.dp(k, WaddleTokens.SPACE_8), LinearLayout.LayoutParams.WRAP_CONTENT))
                                addView(ui.icon(k, Met.ikon(t.symbol)))
                                addView(ui.text(k, Met.ord(t.symbol)), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                                if (t.nedbor > 0) addView(ui.text(k, "${t.nedbor} mm", muted = true))
                                addView(ui.text(k, grader(t.temp), bold = true), LinearLayout.LayoutParams(WaddleUi.dp(k, WaddleTokens.SPACE_12), LinearLayout.LayoutParams.WRAP_CONTENT))
                            })
                        }
                    } else {
                        addView(ui.text(k, s.navn.substringBefore(","), WaddleTokens.TEXT_LG, bold = true))
                        addView(ui.text(k, if (s.navn in feil) "Fikk ikke hentet været. Sjekk nettet og trykk Oppdater." else "Henter", muted = true))
                    }
                    addView(ui.button(k, "Fjern") {
                        lagre(steder().filter { it.navn != s.navn })
                        vis()
                    })
                })
            }
            addView(ui.button(k, "Oppdater", primary = true) { oppdater(true) })
            addView(ui.box(k).apply {
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
    }
}
