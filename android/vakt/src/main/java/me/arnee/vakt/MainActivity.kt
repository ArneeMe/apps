package me.arnee.vakt

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.Intent
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class MainActivity : Activity() {

    override fun onResume() {
        super.onResume()
        vis()
    }

    private fun velg() {
        AlertDialog.Builder(this)
            .setTitle("Slett telefonen etter")
            .setItems(Vakt.VALG.map { if (it == 0) "Aldri" else "$it feil forsøk" }.toTypedArray()) { _, i ->
                val antall = Vakt.VALG[i]
                if (antall == 0) {
                    Vakt.settGrense(this, 0)
                    vis()
                } else {
                    AlertDialog.Builder(this)
                        .setMessage("Etter $antall feil forsøk på å låse opp blir alt på telefonen slettet. Det kan ikke angres. Har du sikkerhetskopi?")
                        .setPositiveButton("Slå på") { _, _ ->
                            Vakt.settGrense(this, antall)
                            vis()
                        }
                        .setNegativeButton("Avbryt", null)
                        .show()
                }
            }
            .show()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Vakt"))
            if (!Vakt.aktiv(k)) {
                addView(ui.box(k).apply {
                    addView(ui.listRow(k, "Tilgang", "Vakt må være enhetsadministrator for å låse og følge med på feil forsøk.", iconName = "shield"))
                    addView(ui.button(k, "Gi tilgang", primary = true) {
                        startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Vakt.admin(k)))
                    })
                })
                return@apply
            }
            addView(ui.box(k).apply {
                addView(ui.listRow(k, "Lås nå", "Låser skjermen. Finnes også som hurtigknapp.", iconName = "lock"))
                addView(ui.button(k, "Lås", primary = true) { Vakt.las(k) })
            })
            addView(ui.box(k).apply {
                val g = Vakt.grense(k)
                addView(ui.listRow(k, "Slett ved feil forsøk", if (g == 0) "Av" else "Etter $g feil forsøk. Nå: ${Vakt.feil(k)}.", iconName = "shield"))
                addView(ui.text(k, "Sletter alt på telefonen hvis noen prøver for mange koder. Telefonen må ha PIN, mønster eller passord.", WaddleTokens.TEXT_SM, muted = true))
                addView(ui.button(k, "Endre") { velg() })
            })
        }
        ui.screen(this, innhold)
    }
}
