package me.arnee.digipost

import android.app.Activity
import android.content.Context
import android.content.Intent
import java.time.OffsetDateTime
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class Hendelsesrad(val tid: Long, val emne: String, val egen: Boolean, val sending: Sending?, val innkommende: Innkommende?)

object Samtaler {
    fun nokkel(navn: String) = navn.trim().lowercase()

    fun millis(tid: String) = runCatching { OffsetDateTime.parse(tid).toInstant().toEpochMilli() }.getOrDefault(0L)

    fun alle(k: Context): Map<String, List<Hendelsesrad>> {
        val test = Lager.test(k)
        val sendt = Lager.historikk(k).filter { it.test == test }.map { Hendelsesrad(it.sendt, it.emne, true, it, null) to it.mottaker }
        val mottatt = Lager.innboks(k).map { Hendelsesrad(millis(it.tid), it.emne, false, null, it) to it.avsender }
        return (sendt + mottatt).groupBy({ nokkel(it.second) }, { it.first }).mapValues { (_, v) -> v.sortedBy { it.tid } }
    }

    fun navn(k: Context, nokkel: String): String {
        val test = Lager.test(k)
        return Lager.historikk(k).lastOrNull { it.test == test && nokkel(it.mottaker) == nokkel }?.mottaker
            ?: Lager.innboks(k).firstOrNull { nokkel(it.avsender) == nokkel }?.avsender ?: nokkel
    }

    fun hent(k: Context): Int = nye(k).size

    fun nye(k: Context): List<Innkommende> {
        val o = Lager.oppsett(k) ?: return emptyList()
        val forste = !Lager.harInnboks(k)
        val gamle = Lager.innboks(k).map { it.id }.toSet()
        val alle = Digipost.innboks(o)
        Lager.settInnboks(k, alle)
        return if (forste) emptyList() else alle.filter { it.id !in gamle }
    }
}

class SamtalerActivity : Activity() {
    private var status: String? = null
    private var opptatt = false

    override fun onResume() {
        super.onResume()
        hent()
    }

    private fun hent() {
        if (opptatt || Lager.oppsett(this) == null) return vis()
        opptatt = true
        vis()
        Thread {
            val resultat = runCatching { Samtaler.hent(this) }
            runOnUiThread {
                opptatt = false
                status = resultat.fold({ if (it == 0) null else if (it == 1) "1 nytt brev." else "$it nye brev." }, {
                    if (it is Feil) "Fikk ikke hentet innboksen: ${it.message}" else "Fikk ikke hentet innboksen. Sjekk nettet."
                })
                vis()
            }
        }.start()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val lest = Lager.lest(k)
        val samtaler = Samtaler.alle(k).entries.sortedByDescending { it.value.last().tid }
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Samtaler"))
            if (Lager.test(k)) addView(ui.text(k, "Testmiljø.", bold = true))
            addView(ui.button(k, "Hent nye", primary = true) { hent() })
            (if (opptatt) "Sjekker innboksen." else status)?.let { addView(ui.text(k, it, muted = true)) }
            addView(ui.box(k).apply {
                if (samtaler.isEmpty()) addView(ui.text(k, "Ingen samtaler ennå. Send et brev, eller vent på svar.", muted = true))
                samtaler.forEachIndexed { i, (nokkel, rader) ->
                    if (i > 0) addView(ui.divider(k))
                    val siste = rader.last()
                    val nye = rader.count { it.innkommende != null && it.innkommende.id !in lest }
                    val detalj = (if (nye > 0) "$nye nye · " else "") + (if (siste.egen) "Du: " else "") + siste.emne + " · " + Ui.tid(siste.tid)
                    addView(Deler.klikkbar(ui.listRow(k, Samtaler.navn(k, nokkel), detalj, iconName = if (nye > 0) "mail" else "user")) {
                        startActivity(Intent(k, SamtaleActivity::class.java).putExtra("nokkel", nokkel))
                    })
                }
            })
            addView(ui.text(k, "Svar som sendes til virksomheten din i Digipost havner i innboksen. Appen henter dem når du åpner Samtaler.", WaddleTokens.TEXT_SM, muted = true))
        }
        Ui.skjerm(this, innhold)
    }
}
