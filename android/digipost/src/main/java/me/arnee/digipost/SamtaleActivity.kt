package me.arnee.digipost

import android.app.Activity
import android.content.Intent
import android.widget.LinearLayout
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class SamtaleActivity : Activity() {
    private var valgt: String? = null
    private var lagres: ByteArray? = null
    private var status: String? = null
    private var opptatt = false

    private fun nokkel() = intent.getStringExtra("nokkel") ?: ""

    override fun onResume() {
        super.onResume()
        vis()
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val bytes = lagres
        lagres = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || bytes == null) return
        status = if (runCatching { contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }.isSuccess) "Lagret." else "Fikk ikke lagret filen."
        vis()
    }

    private fun bakgrunn(jobb: (Oppsett) -> String?) {
        val o = Lager.oppsett(this) ?: return
        opptatt = true
        status = null
        vis()
        Thread {
            val resultat = runCatching { jobb(o) }
            runOnUiThread {
                opptatt = false
                status = resultat.getOrElse { if (it is Feil) it.message else "Fikk ikke kontakt med Digipost." }
                vis()
            }
        }.start()
    }

    private fun endelse(type: String) = when {
        type.contains("pdf") -> "pdf"
        type.contains("html") -> "html"
        type.contains("png") -> "png"
        type.contains("jpeg") -> "jpg"
        type.contains("gif") -> "gif"
        else -> "bin"
    }

    private fun lagre(d: Innkommende) {
        Lager.settLest(this, d.id)
        bakgrunn { o ->
            val bytes = Digipost.innhold(o, d.innhold)
            runOnUiThread {
                lagres = bytes
                startActivityForResult(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(d.type.ifEmpty { "application/octet-stream" })
                        .putExtra(Intent.EXTRA_TITLE, "${d.emne.ifEmpty { "Dokument" }}.${endelse(d.type)}"),
                    1,
                )
            }
            null
        }
    }

    private fun slett(d: Innkommende) {
        bakgrunn { o ->
            Digipost.slett(o, d.slett)
            Lager.settInnboks(this, Lager.innboks(this).filter { it.id != d.id })
            valgt = null
            "Slettet fra innboksen."
        }
    }

    private fun svar() {
        val nokkel = nokkel()
        val test = Lager.test(this)
        val siste = Lager.historikk(this).lastOrNull { it.test == test && Samtaler.nokkel(it.mottaker) == nokkel }
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (siste != null) intent.putExtra("navn", siste.mottaker).putExtra("adresse", siste.adresse)
        else intent.putExtra("sok", Samtaler.navn(this, nokkel))
        startActivity(intent)
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val rader = Samtaler.alle(k)[nokkel()] ?: emptyList()
        val lest = Lager.lest(k)
        val innhold = ui.column(k, WaddleTokens.SPACE_3).apply {
            addView(Deler.tittel(k, Samtaler.navn(k, nokkel())))
            if (Lager.test(k)) addView(ui.text(k, "Testmiljø.", bold = true))
            if (rader.isEmpty()) addView(ui.text(k, "Ingen brev i denne samtalen.", muted = true))
            rader.forEach { r ->
                val s = r.sending
                val d = r.innkommende
                if (s != null) {
                    val deler = mutableListOf(Ui.tid(s.sendt))
                    if (s.antall > 1) deler += "${s.antall} filer"
                    if (s.status.isNotEmpty()) deler += Ui.status(s.status)
                    if (s.kanal.isNotEmpty()) deler += Ui.kanal(s.kanal)
                    if (s.lest == "Y") deler += "åpnet"
                    if (s.hendelser.isNotEmpty()) deler += s.hendelser.split(",").map { Ui.hendelse(it) }
                    addView(Ui.boble(k, s.emne, deler.distinct().joinToString(" · "), true, "send"))
                }
                if (d != null) {
                    val ny = d.id !in lest
                    val detalj = listOfNotNull(if (ny) "Nytt" else null, Ui.tid(d.tid), if (d.vedlegg > 0) "${d.vedlegg} vedlegg" else null).joinToString(" · ")
                    addView(Deler.klikkbar(Ui.boble(k, d.emne.ifEmpty { "Uten emne" }, detalj, false, "mail")) {
                        valgt = if (valgt == d.id) null else d.id
                        vis()
                    })
                    if (valgt == d.id) addView(ui.row(k, WaddleTokens.SPACE_2).apply {
                        val lik = { LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
                        addView(ui.button(k, "Lagre") { if (!opptatt) lagre(d) }, lik())
                        addView(ui.button(k, "Lest") {
                            Lager.settLest(k, d.id)
                            vis()
                        }, lik())
                        addView(ui.button(k, "Slett") { if (!opptatt) slett(d) }, lik())
                    })
                }
            }
            (if (opptatt) "Venter på Digipost." else status)?.let { addView(ui.text(k, it, muted = true)) }
            addView(ui.button(k, "Skriv svar", primary = true) { svar() })
        }
        Ui.skjerm(this, innhold)
    }
}
