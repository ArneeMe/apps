package me.arnee.digipost

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import java.io.File
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class MainActivity : Activity() {
    private var visOppsett = false
    private var avsender = ""
    private var passord = ""
    private var adresse = ""
    private var sokTekst = ""
    private var treff: List<Mottaker> = emptyList()
    private var mottaker: Mottaker? = null
    private var emne = ""
    private var pdf: ByteArray? = null
    private var pdfNavn = ""
    private var status: String? = null
    private var opptatt = false

    private fun lager() = getSharedPreferences("digipost", Context.MODE_PRIVATE)

    private fun sertifikat() = File(filesDir, "sertifikat.p12")

    private fun oppsett(): Oppsett? {
        val l = lager()
        val id = l.getString("avsender", "")!!
        if (id.isEmpty() || !sertifikat().exists()) return null
        return Oppsett(id, sertifikat().readBytes(), l.getString("passord", "")!!, l.getString("adresse", "https://api.digipost.no")!!)
    }

    override fun onResume() {
        super.onResume()
        val l = lager()
        avsender = l.getString("avsender", "")!!
        passord = l.getString("passord", "")!!
        adresse = l.getString("adresse", "https://api.digipost.no")!!
        vis()
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
        if (requestCode == 1) sertifikat().writeBytes(bytes)
        if (requestCode == 2) {
            pdf = bytes
            pdfNavn = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Dokument"
            if (emne.isEmpty()) emne = pdfNavn.removeSuffix(".pdf")
        }
        vis()
    }

    private fun velg(type: String, kode: Int) {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type), kode)
    }

    private fun bakgrunn(jobb: (Oppsett) -> String?) {
        val o = oppsett() ?: return
        opptatt = true
        status = null
        vis()
        Thread {
            val resultat = runCatching { jobb(o) }
            runOnUiThread {
                opptatt = false
                status = resultat.getOrElse { if (it is Feil) it.message else "Fikk ikke kontakt med Digipost. Sjekk nettet, sertifikatet og passordet." }
                vis()
            }
        }.start()
    }

    private fun felt(hint: String, verdi: String, type: Int? = null, endret: (String) -> Unit) = Deler.felt(this, hint).apply {
        if (type != null) inputType = type
        setText(verdi)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                endret(s.toString())
            }
        })
    }

    private fun sok() {
        val tekst = sokTekst.trim()
        if (tekst.isEmpty()) return
        treff = emptyList()
        if (tekst.matches(Regex("\\d{11}"))) {
            bakgrunn {
                when (Digipost.identifiser(it, tekst)) {
                    "DIGIPOST" -> {
                        treff = listOf(Mottaker("Fødselsnummer $tekst", tekst, "Har Digipost"))
                        null
                    }
                    "IDENTIFIED" -> "Personen finnes, men har ikke Digipost."
                    "INVALID" -> "Ugyldig fødselsnummer."
                    else -> "Fant ingen med det fødselsnummeret."
                }
            }
        } else {
            bakgrunn {
                treff = Digipost.sok(it, tekst)
                if (treff.isEmpty()) "Ingen treff." else null
            }
        }
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val klar = oppsett() != null
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Digipost", "gear", "Oppsett") {
                visOppsett = !visOppsett
                vis()
            })
            if (visOppsett || !klar) addView(ui.box(k).apply {
                addView(ui.text(k, "Oppsett", WaddleTokens.TEXT_LG, bold = true))
                addView(felt("Avsender-ID", avsender, InputType.TYPE_CLASS_NUMBER) { avsender = it })
                addView(felt("Passord til sertifikatet", passord, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) { passord = it })
                addView(felt("Adresse", adresse, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI) { adresse = it })
                addView(ui.text(k, if (sertifikat().exists()) "Sertifikat lagt inn." else "Mangler sertifikat (.p12).", muted = true))
                addView(ui.button(k, "Velg sertifikat") { velg("*/*", 1) })
                addView(ui.button(k, "Lagre", primary = true) {
                    lager().edit().putString("avsender", avsender.trim()).putString("passord", passord).putString("adresse", adresse.trim()).apply()
                    visOppsett = false
                    vis()
                })
            })
            if (klar) {
                addView(ui.box(k).apply {
                    addView(ui.text(k, "Finn mottaker", WaddleTokens.TEXT_LG, bold = true))
                    addView(felt("Navn, adresse eller fødselsnummer", sokTekst) { sokTekst = it })
                    addView(ui.button(k, "Søk") { sok() })
                    treff.forEach { t ->
                        addView(Deler.klikkbar(ui.listRow(k, t.navn, t.detalj, iconName = "user")) {
                            mottaker = t
                            treff = emptyList()
                            vis()
                        })
                    }
                })
                addView(ui.box(k).apply {
                    addView(ui.text(k, "Send brev", WaddleTokens.TEXT_LG, bold = true))
                    val m = mottaker
                    addView(if (m == null) ui.text(k, "Velg en mottaker over.", muted = true) else ui.listRow(k, m.navn, m.detalj, iconName = "user"))
                    addView(felt("Emne", emne) { emne = it })
                    addView(ui.text(k, if (pdf == null) "Ingen PDF valgt." else pdfNavn, muted = pdf == null))
                    addView(ui.button(k, "Velg PDF") { velg("application/pdf", 2) })
                    addView(ui.button(k, "Send", primary = true) {
                        val fil = pdf
                        if (m == null || fil == null || emne.isBlank() || opptatt) return@button
                        bakgrunn {
                            Digipost.send(it, m.adresse, emne.trim(), fil)
                            pdf = null
                            emne = ""
                            "Sendt til ${m.navn}."
                        }
                    })
                })
            }
            (if (opptatt) "Venter på Digipost." else status)?.let { addView(ui.text(k, it, muted = true)) }
        }
        ui.screen(this, innhold)
        Deler.tastatur(this)
    }
}
