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
    private var visSynk = false

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
            addView(Deler.tittel(k, "Notater", if (visSynk) "check" else "settings-cog", if (visSynk) "Ferdig" else "Synk") {
                visSynk = !visSynk
                vis()
            })
            if (visSynk) {
                addView(ui.box(k).apply {
                    addView(ui.text(k, "Synk", WaddleTokens.TEXT_LG, bold = true))
                    val mappe = Synk.mappe(k)
                    addView(ui.text(k, synkStatus ?: if (mappe == null) "Notatene lagres som tekstfiler i en mappe du velger." else "Synker med ${Synk.navn(k, mappe)}.", muted = true))
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
            addView(ui.box(k).apply {
                addView(ui.text(k, "Oppgaver", WaddleTokens.TEXT_LG, bold = true))
                val oppgaver = Lager.oppgaver(k)
                oppgaver.forEachIndexed { i, o ->
                    addView(Deler.klikkbar(ui.row(k, WaddleTokens.SPACE_3).apply {
                        val pad = WaddleUi.dp(k, WaddleTokens.SPACE_3)
                        setPadding(0, pad, 0, pad)
                        addView(ui.text(k, o.tekst, muted = o.ferdig).apply {
                            if (o.ferdig) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                        if (o.ferdig) addView(ui.icon(k, "check", color = ui.palette(k).muted))
                    }) {
                        Lager.veksle(k, i)
                        vis()
                    })
                }
                val leggTil = { felt: android.widget.EditText ->
                    val tekst = felt.text.toString().trim()
                    if (tekst.isNotEmpty()) {
                        Lager.leggTil(k, tekst)
                        vis()
                    }
                }
                addView(ui.row(k).apply {
                    val felt = Deler.felt(k, "Ny oppgave").apply {
                        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                        setOnEditorActionListener { v, _, _ -> leggTil(v as android.widget.EditText); true }
                    }
                    addView(felt, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(ui.button(k, "Legg til") { leggTil(felt) })
                })
                if (oppgaver.any { it.ferdig }) {
                    addView(ui.button(k, "Fjern ferdige") {
                        Lager.fjernFerdige(k)
                        vis()
                    })
                }
            })
            addView(ui.box(k).apply {
                addView(ui.row(k).apply {
                    addView(ui.text(k, "Notater", WaddleTokens.TEXT_LG, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(ui.button(k, "Nytt notat") { apne(Lager.nyttNotat(k).name) })
                })
                val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, java.util.Locale("nb", "NO"))
                val notater = Lager.notater(k)
                if (notater.isEmpty()) addView(ui.text(k, "Ingen notater ennå.", muted = true))
                notater.forEachIndexed { i, fil ->
                    if (i > 0) addView(ui.divider(k))
                    addView(Deler.klikkbar(ui.listRow(k, Lager.tittel(fil), format.format(Date(fil.lastModified())))) {
                        apne(fil.name)
                    })
                }
            })
        }
        ui.screen(this, innhold)
        Deler.tastatur(this)
    }
}
