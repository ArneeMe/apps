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
            addView(ui.box(k).apply {
                val status = if (gaar) "Stopper media og låser ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(Timer.slutt(k)))}" else "Av"
                addView(ui.listRow(k, "Timer", status, iconName = "hourglass"))
                addView(ui.divider(k))
                if (gaar) {
                    addView(ui.button(k, "Stopp", primary = true) {
                        Timer.stopp(k)
                        vis()
                    })
                } else {
                    addView(ui.button(k, "Start ${Timer.STANDARD} min", primary = true) {
                        Timer.start(k, Timer.STANDARD)
                        vis()
                    })
                    addView(ui.row(k).apply {
                        Timer.VALG.filter { it != Timer.STANDARD }.forEach { minutter ->
                            addView(ui.button(k, "$minutter") {
                                Timer.start(k, minutter)
                                vis()
                            }.apply {
                                minWidth = 0
                                minimumWidth = 0
                            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                        }
                    })
                }
            })
            if (!Timer.kanLase(k)) {
                addView(ui.box(k).apply {
                    addView(ui.listRow(k, "Skjermlås", "Trenger tilgang for å slå av skjermen.", iconName = "lock"))
                    addView(ui.button(k, "Gi tilgang") {
                        startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Timer.admin(k)))
                    })
                })
            }
        }
        ui.screen(this, innhold)
    }
}
