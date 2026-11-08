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
        addView(ui.listRow(kontekst, "Nattmodus", if (pa) "På" else "Av", iconName = "moon"))

        val lys = ui.text(kontekst, "Lysstyrke ${Nattlys.lysstyrke(kontekst)} %", muted = true)
        addView(lys)
        addView(Deler.glider(kontekst, 100, Nattlys.lysstyrke(kontekst)) { verdi ->
            Nattlys.settLysstyrke(kontekst, verdi)
            lys.text = "Lysstyrke $verdi %"
        })

        val temperatur = ui.text(kontekst, "Temperatur ${Nattlys.kelvin(kontekst)} K", muted = true)
        addView(temperatur)
        addView(Deler.glider(kontekst, Nattlys.KELVIN_MAKS - Nattlys.KELVIN_MIN, Nattlys.KELVIN_MAKS - Nattlys.kelvin(kontekst)) { verdi ->
            Nattlys.settKelvin(kontekst, Nattlys.KELVIN_MAKS - verdi)
            temperatur.text = "Temperatur ${Nattlys.KELVIN_MAKS - verdi} K"
        })

        val intensitet = ui.text(kontekst, "Intensitet ${Rodfilter.styrke(kontekst)} %", muted = true)
        addView(intensitet)
        addView(Deler.glider(kontekst, Rodfilter.MAKS, Rodfilter.styrke(kontekst)) { verdi ->
            Rodfilter.settStyrke(kontekst, verdi)
            intensitet.text = "Intensitet $verdi %"
        })

        addView(ui.button(kontekst, if (pa) "Slå av" else "Slå på", primary = true) {
            Nattlys.settPa(kontekst, !pa)
            vis()
        })
    }

    private fun tidsplan() = WaddleUi.box(this).apply {
        val ui = WaddleUi
        val k = this@MainActivity
        val pa = Tidsplan.pa(k)
        val detalj = if (pa) "På ${Tidsplan.tekst(Tidsplan.start(k))}, av ${Tidsplan.tekst(Tidsplan.slutt(k))}" else "Av"
        addView(ui.listRow(k, "Tidsplan", detalj, iconName = "clock"))
        addView(ui.row(k).apply {
            addView(ui.button(k, "Slå på kl.") {
                velg(Tidsplan.start(k)) { Tidsplan.sett(k, aktiv = true, fra = it) }
            })
            addView(ui.button(k, "Slå av kl.") {
                velg(Tidsplan.slutt(k)) { Tidsplan.sett(k, aktiv = true, til = it) }
            })
        })
        if (pa) {
            addView(ui.button(k, "Stopp tidsplan") {
                Tidsplan.sett(k, aktiv = false)
                vis()
            })
        }
    }

    private fun velg(minutter: Int, valgt: (Int) -> Unit) {
        TimePickerDialog(this, { _, time, minutt ->
            valgt(time * 60 + minutt)
            vis()
        }, minutter / 60, minutter % 60, true).show()
    }
}
