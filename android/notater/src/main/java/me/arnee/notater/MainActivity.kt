package me.arnee.notater

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import java.text.DateFormat
import java.util.Date
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val delt = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
        if (delt != null && savedInstanceState == null) apne(Lager.nyttNotat(this, delt).name)
    }

    override fun onResume() {
        super.onResume()
        vis()
    }

    private fun apne(navn: String) {
        startActivity(Intent(this, NotatActivity::class.java).putExtra("navn", navn))
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Notater"))
            addView(ui.box(k).apply {
                addView(ui.text(k, "Oppgaver", WaddleTokens.TEXT_LG, bold = true))
                val oppgaver = Lager.oppgaver(k)
                oppgaver.forEachIndexed { i, o ->
                    addView(Deler.klikkbar(ui.listRow(k, o.tekst, if (o.ferdig) "Ferdig" else null, iconName = if (o.ferdig) "check" else "minus")) {
                        Lager.veksle(k, i)
                        vis()
                    })
                }
                val felt = Deler.felt(k, "Ny oppgave")
                addView(felt)
                addView(ui.row(k).apply {
                    addView(ui.button(k, "Legg til", primary = true) {
                        val tekst = felt.text.toString().trim()
                        if (tekst.isNotEmpty()) {
                            Lager.leggTil(k, tekst)
                            vis()
                        }
                    })
                    if (oppgaver.any { it.ferdig }) {
                        addView(ui.button(k, "Fjern ferdige") {
                            Lager.fjernFerdige(k)
                            vis()
                        })
                    }
                })
            })
            addView(ui.box(k).apply {
                addView(ui.text(k, "Notater", WaddleTokens.TEXT_LG, bold = true))
                val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                Lager.notater(k).forEach { fil ->
                    addView(Deler.klikkbar(ui.listRow(k, Lager.tittel(fil), format.format(Date(fil.lastModified())), iconName = "file-text")) {
                        apne(fil.name)
                    })
                    addView(ui.divider(k))
                }
                addView(ui.button(k, "Nytt notat") { apne(Lager.nyttNotat(k).name) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            })
        }
        ui.screen(this, innhold)
        Deler.tastatur(this)
    }
}
