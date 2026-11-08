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

    private var synkStatus: String? = null

    override fun onResume() {
        super.onResume()
        vis()
        if (Synk.mappe(this) != null) Thread {
            val feil = Synk.kjor(this)
            runOnUiThread {
                synkStatus = feil
                vis()
            }
        }.start()
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 7 && resultCode == RESULT_OK) Synk.settMappe(this, data?.data)
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
            addView(ui.box(k).apply {
                addView(ui.text(k, "Synk", WaddleTokens.TEXT_LG, bold = true))
                val mappe = Synk.mappe(k)
                addView(ui.text(k, synkStatus ?: if (mappe == null) "Velg en mappe, for eksempel en som Syncthing deler med PC-en. Notatene lagres der som tekstfiler." else "Synker med ${Synk.navn(k, mappe)}.", muted = true))
                addView(ui.row(k).apply {
                    addView(ui.button(k, if (mappe == null) "Velg mappe" else "Bytt mappe") {
                        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 7)
                    })
                    if (mappe != null) addView(ui.button(k, "Slutt å synke") {
                        Synk.settMappe(k, null)
                        vis()
                    })
                })
            })
        }
        ui.screen(this, innhold)
        Deler.tastatur(this)
    }
}
