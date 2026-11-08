package me.arnee.gratone

import android.app.Activity
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class MainActivity : Activity() {

    override fun onResume() {
        super.onResume()
        vis()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Gråtone"))
            addView(ui.box(k).apply {
                if (!Gratone.tilgang(k)) {
                    addView(ui.listRow(k, "Skjerm", "Trenger tilgang. Kjør fra PC:", iconName = "eye-off"))
                    addView(ui.text(k, Gratone.KOMMANDO, WaddleTokens.TEXT_SM, muted = true).apply { setTextIsSelectable(true) })
                    return@apply
                }
                val pa = Gratone.pa(k)
                addView(ui.listRow(k, "Skjerm", if (pa) "Svart-hvitt" else "Farger", iconName = "eye-off"))
                addView(ui.button(k, if (pa) "Slå av" else "Slå på", primary = true) {
                    Gratone.sett(k, !pa)
                    vis()
                })
            })
        }
        ui.screen(this, innhold)
    }
}
