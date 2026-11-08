package me.arnee.utklipp

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class MainActivity : Activity() {
    private var utkast = ""
    private var visOppsett = false
    private var status: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val delt = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
        if (delt != null && savedInstanceState == null) utkast = delt
    }

    override fun onResume() {
        super.onResume()
        hent()
    }

    private fun bakgrunn(jobb: () -> Unit) {
        Thread {
            val resultat = runCatching(jobb)
            runOnUiThread {
                status = if (resultat.isFailure) "Fikk ikke kontakt med postkassen. Sjekk nettet og prøv igjen." else null
                vis()
            }
        }.start()
    }

    private fun hent() {
        vis()
        bakgrunn { Postkasse.hent(this) }
    }

    private fun send(tekst: String) {
        if (tekst.isBlank()) return
        utkast = ""
        bakgrunn { Postkasse.send(this, tekst) }
    }

    private fun utklipp(): String =
        getSystemService(ClipboardManager::class.java).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Utklipp"))
            status?.let { addView(ui.text(k, it, muted = true)) }
            addView(ui.box(k).apply {
                val meldinger = Postkasse.meldinger(k).takeLast(50).reversed()
                if (meldinger.isEmpty()) addView(ui.text(k, "Ingen meldinger ennå.", muted = true))
                meldinger.forEachIndexed { i, m ->
                    if (i > 0) addView(ui.divider(k))
                    addView(Deler.klikkbar(ui.listRow(k, m.tekst, "${m.fra}, ${format.format(Date(m.tid))}")) {
                        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("melding", m.tekst))
                        Toast.makeText(k, "Kopiert", Toast.LENGTH_SHORT).show()
                    })
                }
            })
            addView(ui.box(k).apply {
                val felt = Deler.felt(k, "Skriv", flerlinje = true).apply {
                    setText(utkast)
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) {
                            utkast = s.toString()
                        }
                    })
                }
                addView(felt)
                addView(ui.row(k).apply {
                    addView(ui.button(k, "Send", primary = true) { send(utkast) })
                    addView(ui.button(k, "Send utklipp") { send(utklipp()) })
                })
            })
            addView(ui.row(k).apply {
                addView(ui.button(k, "Hent") { hent() })
                addView(ui.button(k, if (visOppsett) "Skjul oppsett" else "Oppsett") {
                    visOppsett = !visOppsett
                    vis()
                })
            })
            if (visOppsett) {
                addView(ui.box(k).apply {
                    addView(ui.text(k, "Kobling", WaddleTokens.TEXT_LG, bold = true))
                    addView(ui.text(k, "Lim den inn i PC-skriptet. Alt etter # er nøkkelen og forlater aldri enhetene.", muted = true))
                    addView(ui.text(k, Postkasse.kobling(k), WaddleTokens.TEXT_SM).apply { setTextIsSelectable(true) })
                    addView(ui.button(k, "Kopier kobling") {
                        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("kobling", Postkasse.kobling(k)))
                    })
                    val ny = Deler.felt(k, "Lim inn annen kobling")
                    addView(ny)
                    addView(ui.button(k, "Bruk kobling") {
                        val tekst = ny.text.toString()
                        if (tekst.startsWith("https://") && tekst.contains("#")) {
                            Postkasse.settKobling(k, tekst)
                            hent()
                        } else {
                            Toast.makeText(k, "Koblingen må starte med https:// og ha en # med nøkkel.", Toast.LENGTH_LONG).show()
                        }
                    })
                })
            }
        }
        ui.screen(this, innhold)
        Deler.tastatur(this)
    }
}
