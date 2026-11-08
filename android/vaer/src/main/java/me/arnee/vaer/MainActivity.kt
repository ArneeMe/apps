package me.arnee.vaer

import android.app.Activity
import android.content.Intent
import android.widget.LinearLayout
import kotlin.math.roundToInt
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class MainActivity : Activity() {
    private var treff: List<Sted> = emptyList()
    private var sokTekst = ""
    private var redigerer = false

    override fun onResume() {
        super.onResume()
        oppdater(false)
    }

    private fun oppdater(tving: Boolean) {
        vis()
        Visning.steder(this).forEach { s ->
            val gammelt = Visning.varsler[s.navn]
            if (!tving && gammelt != null && gammelt.utloper > System.currentTimeMillis()) return@forEach
            Thread {
                val resultat = runCatching { Met.varsel(s) }
                runOnUiThread {
                    resultat.onSuccess {
                        Visning.varsler[s.navn] = it
                        Visning.feil.remove(s.navn)
                        KlaerWidget.oppdaterAlle(this)
                    }.onFailure { Visning.feil.add(s.navn) }
                    vis()
                }
            }.start()
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
            addView(ui.box(k).apply {
                Visning.steder(k).forEachIndexed { i, s ->
                    if (i > 0) addView(ui.divider(k))
                    val v = Visning.varsler[s.navn]
                    val na = v?.timer?.firstOrNull()
                    val sted = s.navn.substringBefore(",")
                    val rad = ui.row(k, WaddleTokens.SPACE_3).apply {
                        val pad = WaddleUi.dp(k, WaddleTokens.SPACE_2)
                        setPadding(0, pad, 0, pad)
                        if (na != null) addView(ui.icon(k, Met.ikon(na.symbol), 48))
                        addView(ui.column(k, WaddleTokens.SPACE_1).apply {
                            addView(ui.text(k, sted, WaddleTokens.TEXT_LG, bold = true))
                            val detalj = when {
                                na != null -> listOfNotNull("${Met.ord(na.symbol)}, ${na.vind.roundToInt()} m/s", Visning.regn(v)).joinToString("\n")
                                s.navn in Visning.feil -> "Fikk ikke hentet været."
                                else -> "Henter"
                            }
                            addView(ui.text(k, detalj, muted = true))
                        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                        if (na != null) addView(ui.text(k, Visning.grader(na.temp), WaddleTokens.TEXT_XL, bold = true))
                    }
                    if (redigerer) {
                        addView(rad)
                        addView(ui.button(k, "Fjern $sted") {
                            Visning.lagre(k, Visning.steder(k).filter { it.navn != s.navn })
                            vis()
                        })
                    } else {
                        addView(Deler.klikkbar(rad) { startActivity(Intent(k, StedActivity::class.java).putExtra("navn", s.navn)) })
                    }
                }
            })
            if (!redigerer) {
                addView(ui.button(k, "Oppdater") { oppdater(true) })
            } else {
                addView(ui.box(k).apply {
                    addView(ui.text(k, "Legg til sted", WaddleTokens.TEXT_LG, bold = true))
                    addView(Deler.felt(k, "Stedsnavn").apply {
                        setText(sokTekst)
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: android.text.Editable?) {
                                sokTekst = s.toString()
                            }
                        })
                    })
                    addView(ui.button(k, "Søk", primary = true) {
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
                            if (Visning.steder(k).none { it.navn == t.navn }) Visning.lagre(k, Visning.steder(k) + t)
                            treff = emptyList()
                            sokTekst = ""
                            oppdater(false)
                        })
                    }
                })
            }
            addView(ui.text(k, "Data fra MET Norge og Kartverket.", WaddleTokens.TEXT_SM, muted = true))
        }
        ui.screen(this, innhold)
        Deler.tastatur(this)
    }
}
