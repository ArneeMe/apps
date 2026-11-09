package me.arnee.verktoy

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler
import me.arnee.verktoy.nattlys.Nattlys
import me.arnee.verktoy.plan.Tidsplan
import me.arnee.verktoy.rodfilter.Rodfilter

class MainActivity : Activity() {

    override fun onResume() {
        super.onResume()
        vis()
    }

    private fun vis() {
        val ui = WaddleUi
        val innhold = ui.column(this, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(this@MainActivity, "Nattmodus"))
            addView(nattlys())
            addView(tidsplan())
        }
        ui.screen(this, innhold)
    }

    private fun nattlys() = WaddleUi.box(this).apply {
        val ui = WaddleUi
        val kontekst = this@MainActivity
        if (!Nattlys.tilgang(kontekst)) {
            addView(ui.listRow(kontekst, "Nattmodus", "Trenger tilgang. Kjør fra PC:", iconName = "moon"))
            addView(ui.text(kontekst, Nattlys.KOMMANDO, WaddleTokens.TEXT_SM, muted = true).apply { setTextIsSelectable(true) })
            return@apply
        }
        if (!Settings.canDrawOverlays(kontekst)) {
            addView(ui.listRow(kontekst, "Nattmodus", "Trenger tilgang til å vise over andre apper.", iconName = "moon"))
            addView(ui.button(kontekst, "Gi tilgang", primary = true) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            })
            return@apply
        }
        val pa = Nattlys.pa(kontekst)
        addView(ui.row(kontekst, WaddleTokens.SPACE_3).apply {
            addView(ui.text(kontekst, if (pa) "På" else "Av", WaddleTokens.TEXT_LG, bold = true), android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(ui.button(kontekst, if (pa) "Slå av" else "Slå på", primary = true) {
                Nattlys.settPa(kontekst, !pa)
                vis()
            })
        })
        addView(ui.divider(kontekst))

        fun glider(navn: String, tekst: String, maks: Int, verdi: Int, endret: (Int) -> String) {
            val etikett = ui.text(kontekst, tekst)
            addView(ui.row(kontekst).apply {
                addView(ui.text(kontekst, navn, muted = true), android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(etikett)
            })
            addView(Deler.glider(kontekst, maks, verdi) { etikett.text = endret(it) })
        }
        glider("Lysstyrke", "${Nattlys.lysstyrke(kontekst)} %", 100, Nattlys.lysstyrke(kontekst)) {
            Nattlys.settLysstyrke(kontekst, it)
            "$it %"
        }
        glider("Varme", "${Nattlys.kelvin(kontekst)} K", Nattlys.KELVIN_MAKS - Nattlys.KELVIN_MIN, Nattlys.KELVIN_MAKS - Nattlys.kelvin(kontekst)) {
            Nattlys.settKelvin(kontekst, Nattlys.KELVIN_MAKS - it)
            "${Nattlys.KELVIN_MAKS - it} K"
        }
        glider("Rødt filter", "${Rodfilter.styrke(kontekst)} %", Rodfilter.MAKS, Rodfilter.styrke(kontekst)) {
            Rodfilter.settStyrke(kontekst, it)
            "$it %"
        }
    }

    private fun tidsplan() = WaddleUi.box(this).apply {
        val ui = WaddleUi
        val k = this@MainActivity
        val pa = Tidsplan.pa(k)
        addView(ui.text(k, "Tidsplan", WaddleTokens.TEXT_LG, bold = true))
        addView(Deler.verdi(k, "Bruk tidsplan", if (pa) "Ja" else "Nei") {
            Tidsplan.sett(k, aktiv = !pa)
            vis()
        })
        addView(ui.divider(k))
        addView(Deler.verdi(k, "Slå på", Tidsplan.tekst(Tidsplan.start(k))) {
            velg(Tidsplan.start(k)) { Tidsplan.sett(k, aktiv = true, fra = it) }
        })
        addView(ui.divider(k))
        addView(Deler.verdi(k, "Slå av", Tidsplan.tekst(Tidsplan.slutt(k))) {
            velg(Tidsplan.slutt(k)) { Tidsplan.sett(k, aktiv = true, til = it) }
        })
    }

    private fun velg(minutter: Int, valgt: (Int) -> Unit) {
        TimePickerDialog(this, { _, time, minutt ->
            valgt(time * 60 + minutt)
            vis()
        }, minutter / 60, minutter % 60, true).show()
    }
}
