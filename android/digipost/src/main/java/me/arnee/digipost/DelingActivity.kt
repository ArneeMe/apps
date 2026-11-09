package me.arnee.digipost

import android.app.Activity
import android.content.Intent
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class DelingActivity : Activity() {
    private var deling: Delingsstatus? = null
    private var lagres: ByteArray? = null
    private var status: String? = null
    private var opptatt = false

    private fun dokument() = intent.getStringExtra("dokument") ?: ""

    override fun onResume() {
        super.onResume()
        if (deling == null) hent() else vis()
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

    private fun hent() = bakgrunn {
        deling = Digipost.deling(it, dokument())
        null
    }

    private fun lagre(d: Delt) = bakgrunn { o ->
        val bytes = Digipost.innhold(o, d.strom)
        runOnUiThread {
            lagres = bytes
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream")
                    .putExtra(Intent.EXTRA_TITLE, "${d.emne.ifEmpty { "Dokument" }}.${d.type.ifEmpty { "pdf" }}"),
                1,
            )
        }
        null
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val sending = Lager.historikk(k).firstOrNull { it.dokument == dokument() }
        val d = deling
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Delte dokumenter"))
            if (sending != null) addView(ui.text(k, "${sending.emne} til ${sending.mottaker}", muted = true))
            addView(ui.button(k, "Oppdater", primary = true) { if (!opptatt) hent() })
            if (d != null) addView(ui.box(k).apply {
                val tilstand = when {
                    d.trukket.isNotEmpty() -> "Delingen ble stoppet ${Ui.tid(d.trukket)}."
                    d.delt.isEmpty() -> "Mottakeren har ikke delt noe ennå."
                    else -> "Delt ${Ui.tid(d.delt)}." + if (d.utloper.isNotEmpty()) " Du kan se dem til ${Ui.tid(d.utloper)}." else ""
                }
                addView(ui.text(k, tilstand))
                d.dokumenter.forEach { dok ->
                    val detalj = listOf(dok.fra, Ui.tid(dok.tid), Ui.storrelse(dok.storrelse.toInt()), "Trykk for å lagre").filter { it.isNotEmpty() }.joinToString(" · ")
                    addView(Deler.klikkbar(ui.listRow(k, dok.emne.ifEmpty { "Uten emne" }, detalj, iconName = "file-text")) { if (!opptatt && dok.strom.isNotEmpty()) lagre(dok) })
                }
                if (d.stopp.isNotEmpty() && d.trukket.isEmpty()) addView(ui.button(k, "Stopp deling") {
                    if (!opptatt) bakgrunn {
                        Digipost.stoppDeling(it, d.stopp)
                        deling = Digipost.deling(it, dokument())
                        "Delingen er stoppet."
                    }
                })
            })
            (if (opptatt) "Venter på Digipost." else status)?.let { addView(ui.text(k, it, muted = true)) }
        }
        Ui.skjerm(this, innhold)
    }
}
