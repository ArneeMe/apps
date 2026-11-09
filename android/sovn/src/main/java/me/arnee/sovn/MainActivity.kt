package me.arnee.sovn

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.widget.LinearLayout
import java.text.DateFormat
import java.util.Date
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
        val gaar = Timer.gaar(k)
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Søvntimer"))
            if (gaar) {
                val klokka = DateFormat.getTimeInstance(DateFormat.SHORT, java.util.Locale("nb", "NO")).format(Date(Timer.slutt(k)))
                addView(ui.text(k, "${Timer.minutterIgjen(k)} min igjen", WaddleTokens.TEXT_XL, bold = true))
                addView(ui.text(k, "Media stopper og skjermen låses kl. $klokka.", muted = true))
                addView(ui.button(k, "Stopp", primary = true) {
                    Timer.stopp(k)
                    vis()
                })
            } else {
                addView(ui.box(k).apply {
                    addView(ui.text(k, "Start", WaddleTokens.TEXT_LG, bold = true))
                    Timer.VALG.forEachIndexed { i, minutter ->
                        if (i > 0) addView(ui.divider(k))
                        addView(Deler.klikkbar(ui.listRow(k, "$minutter min", if (minutter == Timer.STANDARD) "Vanligst" else null)) {
                            Timer.start(k, minutter)
                            vis()
                        })
                    }
                })
            }
            if (!Timer.kanLase(k)) {
                addView(ui.box(k).apply {
                    addView(ui.listRow(k, "Skjermen slås ikke av", "Søvntimer trenger lov til å låse skjermen.", iconName = "lock"))
                    addView(ui.button(k, "Gi tilgang") {
                        startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Timer.admin(k)))
                    })
                })
            }
        }
        ui.screen(this, innhold)
    }
}
