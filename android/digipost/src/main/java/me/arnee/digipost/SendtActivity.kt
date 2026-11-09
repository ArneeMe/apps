package me.arnee.digipost

import android.app.Activity
import android.content.Intent
import java.text.DateFormat
import java.time.OffsetDateTime
import java.util.Date
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class SendtActivity : Activity() {
    private var status: String? = null
    private var opptatt = false

    override fun onResume() {
        super.onResume()
        vis()
    }

    private fun tid(verdi: String) = runCatching {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(OffsetDateTime.parse(verdi).toInstant().toEpochMilli()))
    }.getOrDefault(verdi)

    private fun oppdater() {
        val o = Lager.oppsett(this) ?: return
        val test = Lager.test(this)
        opptatt = true
        status = null
        vis()
        Thread {
            val liste = Lager.historikk(this)
            var feil = 0
            liste.filter { it.test == test }.forEach { s ->
                runCatching { Digipost.status(o, s.dokument) }.onSuccess {
                    s.status = it.status
                    s.kanal = it.kanal
                    s.levert = it.levert
                    s.lest = it.lest
                }.onFailure { feil++ }
            }
            Lager.settHistorikk(this, liste)
            runOnUiThread {
                opptatt = false
                status = if (feil > 0) "Fikk ikke status for $feil brev." else "Oppdatert."
                vis()
            }
        }.start()
    }

    private fun detalj(s: Sending): String {
        val deler = mutableListOf("Til ${s.mottaker}", DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.sendt)))
        if (s.antall > 1) deler += "${s.antall} filer"
        if (s.status.isNotEmpty()) deler += Ui.status(s.status)
        if (s.kanal.isNotEmpty()) deler += Ui.kanal(s.kanal)
        if (s.levert.isNotEmpty()) deler += "levert ${tid(s.levert)}"
        else if (s.leveres.isNotEmpty()) deler += "leveres ${tid(s.leveres)}"
        if (s.lest == "Y") deler += "åpnet"
        if (s.lest == "N") deler += "ikke åpnet"
        return deler.joinToString(" · ")
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val test = Lager.test(k)
        val liste = Lager.historikk(k).filter { it.test == test }.sortedByDescending { it.sendt }
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Sendt"))
            if (test) addView(ui.text(k, "Testmiljø.", bold = true))
            addView(ui.button(k, "Oppdater status", primary = true) { if (!opptatt) oppdater() })
            (if (opptatt) "Spør Digipost." else status)?.let { addView(ui.text(k, it, muted = true)) }
            addView(ui.box(k).apply {
                if (liste.isEmpty()) addView(ui.text(k, "Ingen sendte brev ennå.", muted = true))
                liste.forEachIndexed { i, s ->
                    if (i > 0) addView(ui.divider(k))
                    addView(Deler.klikkbar(ui.listRow(k, s.emne, detalj(s), iconName = "mail")) {
                        startActivity(Intent(k, MainActivity::class.java).putExtra("navn", s.mottaker).putExtra("adresse", s.adresse).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    })
                }
            })
            if (liste.isNotEmpty()) addView(ui.text(k, "Trykk på et brev for å skrive et nytt til samme mottaker.", WaddleTokens.TEXT_SM, muted = true))
        }
        ui.screen(this, innhold)
    }
}
