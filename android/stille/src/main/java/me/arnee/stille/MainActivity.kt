package me.arnee.stille

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import android.widget.LinearLayout
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class MainActivity : Activity() {
    private val dagnavn = listOf("ma", "ti", "on", "to", "fr", "lø", "sø")

    override fun onResume() {
        super.onResume()
        vis()
    }

    private fun bryter(tittel: String, verdi: Boolean, endre: (Boolean) -> Unit) =
        Deler.klikkbar(WaddleUi.listRow(this, tittel, if (verdi) "På" else "Av")) {
            endre(!verdi)
            vis()
        }

    private fun velg(minutter: Int, valgt: (Int) -> Unit) {
        TimePickerDialog(this, { _, t, m ->
            valgt(t * 60 + m)
            vis()
        }, minutter / 60, minutter % 60, true).show()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Stille"))
            if (!Stille.tilgang(k)) {
                addView(ui.box(k).apply {
                    addView(ui.listRow(k, "Tilgang", "Stille trenger tilgang til Ikke forstyrr.", iconName = "volume-x"))
                    addView(ui.button(k, "Gi tilgang", primary = true) {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    })
                })
                return@apply
            }
            val pa = Stille.pa(k)
            addView(ui.box(k).apply {
                addView(ui.listRow(k, "Ikke forstyrr", if (pa) "På" else "Av", iconName = "volume-x"))
                addView(ui.button(k, if (pa) "Slå av" else "Slå på", primary = true) {
                    Stille.settPa(k, !pa)
                    vis()
                })
            })
            addView(ui.box(k).apply {
                addView(ui.text(k, "Slipp gjennom", WaddleTokens.TEXT_LG, bold = true))
                Stille.UNNTAK.forEachIndexed { i, (navn, _) ->
                    if (i == 0) return@forEachIndexed
                    addView(Deler.klikkbar(ui.listRow(k, "Anrop og meldinger fra $navn".lowercase().replaceFirstChar { it.uppercase() }, if (Stille.unntak(k) == i) "Valgt" else null, iconName = if (Stille.unntak(k) == i) "check" else "minus")) {
                        Stille.lagre(k) { putInt("unntak", if (Stille.unntak(k) == i) 0 else i) }
                        vis()
                    })
                }
                addView(ui.divider(k))
                addView(bryter("Den samme som ringer to ganger", Stille.gjentatte(k)) { v -> Stille.lagre(k) { putBoolean("gjentatte", v) } })
                addView(ui.divider(k))
                addView(bryter("Alarmer", Stille.alarmer(k)) { v -> Stille.lagre(k) { putBoolean("alarmer", v) } })
            })
            addView(ui.box(k).apply {
                val plan = Stille.plan(k)
                addView(ui.listRow(k, "Tidsplan", if (plan) "${Stille.tekst(Stille.start(k))} til ${Stille.tekst(Stille.slutt(k))}" else "Av", iconName = "clock"))
                addView(ui.row(k).apply {
                    addView(ui.button(k, "Fra kl.") { velg(Stille.start(k)) { m -> Stille.lagre(k) { putInt("start", m); putBoolean("plan", true) } } })
                    addView(ui.button(k, "Til kl.") { velg(Stille.slutt(k)) { m -> Stille.lagre(k) { putInt("slutt", m); putBoolean("plan", true) } } })
                })
                val dager = Stille.dager(k)
                addView(ui.row(k, WaddleTokens.SPACE_1).apply {
                    dagnavn.forEachIndexed { i, navn ->
                        val nr = i + 1
                        addView(ui.button(k, navn, primary = nr in dager) {
                            val ny = if (nr in dager) dager - nr else dager + nr
                            Stille.lagre(k) { putString("dager", ny.sorted().joinToString(",")) }
                            vis()
                        }.apply {
                            minWidth = 0
                            minimumWidth = 0
                            setPadding(0, 0, 0, 0)
                        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    }
                })
                if (plan) addView(ui.button(k, "Stopp tidsplan") {
                    Stille.lagre(k) { putBoolean("plan", false) }
                    vis()
                })
            })
        }
        ui.screen(this, innhold)
    }
}
