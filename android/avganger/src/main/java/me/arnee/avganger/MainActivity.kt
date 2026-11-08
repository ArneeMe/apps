package me.arnee.avganger

import android.app.Activity
import android.content.Context
import android.widget.LinearLayout
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class MainActivity : Activity() {
    private val avganger = mutableMapOf<String, List<Avgang>>()
    private val feil = mutableSetOf<String>()
    private var treff: List<Stopp> = emptyList()
    private var sokTekst = ""

    private fun lager() = getSharedPreferences("avganger", Context.MODE_PRIVATE)

    private fun stopp(): List<Stopp> =
        lager().getString("stopp", "")!!.lines().filter { it.contains("|") }.map { Stopp(it.substringBefore("|"), it.substringAfter("|")) }

    private fun lagre(liste: List<Stopp>) {
        lager().edit().putString("stopp", liste.joinToString("\n") { "${it.id}|${it.navn}" }).apply()
    }

    override fun onResume() {
        super.onResume()
        oppdater()
    }

    private fun oppdater() {
        vis()
        stopp().forEach { s ->
            Thread {
                val resultat = runCatching { Entur.avganger(s.id) }
                runOnUiThread {
                    resultat.onSuccess {
                        avganger[s.id] = it
                        feil.remove(s.id)
                    }.onFailure { feil.add(s.id) }
                    vis()
                }
            }.start()
        }
    }

    private fun tid(minutter: Long) = when {
        minutter <= 0 -> "nå"
        else -> "$minutter min"
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Avganger"))
            stopp().forEach { s ->
                addView(ui.box(k).apply {
                    addView(ui.text(k, s.navn, WaddleTokens.TEXT_LG, bold = true))
                    val liste = avganger[s.id]
                    when {
                        s.id in feil -> addView(ui.text(k, "Fikk ikke hentet avganger. Sjekk nettet og trykk Oppdater.", muted = true))
                        liste == null -> addView(ui.text(k, "Henter", muted = true))
                        liste.isEmpty() -> addView(ui.text(k, "Ingen avganger nå.", muted = true))
                        else -> liste.forEach { a ->
                            addView(ui.row(k, WaddleTokens.SPACE_3).apply {
                                addView(ui.text(k, a.linje, bold = true), LinearLayout.LayoutParams(WaddleUi.dp(k, WaddleTokens.SPACE_12), LinearLayout.LayoutParams.WRAP_CONTENT))
                                addView(ui.text(k, a.mot), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                                addView(ui.text(k, tid(a.minutter), muted = !a.sanntid))
                            })
                        }
                    }
                    addView(ui.button(k, "Fjern") {
                        lagre(stopp().filter { it.id != s.id })
                        vis()
                    })
                })
            }
            addView(ui.button(k, "Oppdater", primary = true) { oppdater() })
            addView(ui.box(k).apply {
                addView(ui.text(k, "Legg til stopp", WaddleTokens.TEXT_LG, bold = true))
                val felt = Deler.felt(k, "Navn på stopp").apply {
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
                    val tekst = felt.text.toString().trim()
                    if (tekst.isEmpty()) return@button
                    Thread {
                        val resultat = runCatching { Entur.sok(tekst) }
                        runOnUiThread {
                            treff = resultat.getOrDefault(emptyList())
                            vis()
                        }
                    }.start()
                })
                treff.forEach { t ->
                    addView(Deler.klikkbar(ui.listRow(k, t.navn, null, iconName = "map-pin")) {
                        if (stopp().none { it.id == t.id }) lagre(stopp() + t)
                        treff = emptyList()
                        oppdater()
                    })
                }
            })
        }
        ui.screen(this, innhold)
        Deler.tastatur(this)
    }
}
