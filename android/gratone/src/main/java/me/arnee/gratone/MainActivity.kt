package me.arnee.gratone

import android.app.Activity
import android.widget.LinearLayout
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
            if (!Gratone.tilgang(k)) {
                addView(ui.text(k, "Gråtone trenger en tilgang som bare kan gis fra PC:", muted = true))
                addView(ui.text(k, Gratone.KOMMANDO, WaddleTokens.TEXT_SM).apply { setTextIsSelectable(true) })
            } else {
                val pa = Gratone.pa(k)
                addView(ui.row(k, WaddleTokens.SPACE_3).apply {
                    addView(ui.text(k, if (pa) "Svart-hvitt" else "Farger", WaddleTokens.TEXT_LG, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(ui.button(k, if (pa) "Slå av" else "Slå på", primary = true) {
                        Gratone.sett(k, !pa)
                        vis()
                    })
                })
            }
        }
        ui.screen(this, innhold)
    }
}
