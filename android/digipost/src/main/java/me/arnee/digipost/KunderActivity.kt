package me.arnee.digipost

import android.app.Activity
import android.content.Intent
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class KunderActivity : Activity() {
    private var sok = ""
    private var filter = "ALLE"
    private var velger = false
    private val valgt = mutableSetOf<String>()
    private var status: String? = null
    private var opptatt = false
    private var eksport: String? = null
    private var gruppeTekst = ""
    private var slett = false

    override fun onResume() {
        super.onResume()
        vis()
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        if (requestCode == 1) {
            val tekst = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            val nye = tekst?.let { Kunder.importer(it) } ?: emptyList()
            if (nye.isEmpty()) {
                status = "Fant ingen kunder i filen. Første linje må ha kolonnenavn, for eksempel navn;nummer;adresse;postnr;sted."
            } else {
                val gamle = Kunder.alle(this)
                val ukjente = nye.filter { n -> gamle.none { g -> (n.adresse().isNotEmpty() && g.adresse() == n.adresse()) || (n.adresse().isEmpty() && g.navn == n.navn) } }
                Kunder.lagre(this, gamle + ukjente)
                status = "La til ${ukjente.size} kunder." + if (ukjente.size < nye.size) " ${nye.size - ukjente.size} fantes fra før." else ""
            }
        }
        if (requestCode == 2) {
            val tekst = eksport ?: return
            eksport = null
            status = if (runCatching { contentResolver.openOutputStream(uri)?.use { it.write(("﻿" + tekst).toByteArray()) } }.isSuccess) "Eksportert." else "Fikk ikke lagret filen."
        }
        vis()
    }

    private fun synlige(): List<Kunde> {
        val s = sok.trim().lowercase()
        return Kunder.alle(this).filter { c ->
            (s.isEmpty() || listOf(c.navn, c.nummer, c.digipost, c.sted, c.gruppe, c.epost, c.telefon).any { it.lowercase().contains(s) }) &&
                when (filter) {
                    "ALLE" -> true
                    "DIGIPOST" -> c.status == "DIGIPOST"
                    "PAPIR" -> c.status == "IKKE" || c.adresse().isEmpty()
                    "UKJENT" -> c.status.isEmpty() && c.adresse().isNotEmpty()
                    else -> c.gruppe.trim() == filter
                }
        }.sortedBy { it.navn.lowercase() }
    }

    private fun sjekkAlle(liste: List<Kunde>) {
        val o = Lager.oppsett(this) ?: return
        opptatt = true
        status = "Sjekker ${liste.size} kunder."
        vis()
        Thread {
            var feil = 0
            liste.forEach { c -> runCatching { Kunder.sjekk(o, c) }.onFailure { feil++ } }
            val alle = Kunder.alle(this).map { g -> liste.firstOrNull { it.id == g.id } ?: g }
            Kunder.lagre(this, alle)
            runOnUiThread {
                opptatt = false
                val har = liste.count { it.status == "DIGIPOST" }
                status = "$har av ${liste.size} har Digipost." + if (feil > 0) " $feil oppslag feilet." else ""
                vis()
            }
        }.start()
    }

    private fun filternavn(f: String) = when (f) {
        "ALLE" -> "Alle"
        "DIGIPOST" -> "Har Digipost"
        "PAPIR" -> "Får papir"
        "UKJENT" -> "Ikke sjekket"
        else -> "Gruppe: $f"
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val liste = synlige()
        val lik = { LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Kunder"))
            addView(ui.row(k, WaddleTokens.SPACE_2).apply {
                addView(ui.button(k, "Ny kunde", primary = true) { startActivity(Intent(k, KundeActivity::class.java)) }, lik())
                addView(ui.button(k, if (velger) "Ferdig" else "Velg") {
                    velger = !velger
                    if (!velger) valgt.clear()
                    vis()
                }, lik())
            })
            addView(ui.box(k).apply {
                addView(Ui.felt(k, "Søk på navn, nummer, sted eller gruppe", sok) { sok = it }.apply {
                    imeOptions = EditorInfo.IME_ACTION_SEARCH
                    setOnEditorActionListener { _, _, _ ->
                        vis()
                        true
                    }
                })
                val filtre = listOf("ALLE", "DIGIPOST", "PAPIR", "UKJENT") + Kunder.grupper(k)
                addView(Ui.bytt(k, "Vis", filternavn(filter)) {
                    filter = Ui.neste(filtre, filter)
                    vis()
                })
                addView(ui.text(k, "${liste.size} kunder.", WaddleTokens.TEXT_SM, muted = true))
            })
            if (velger) addView(ui.box(k).apply {
                addView(ui.text(k, "${valgt.size} valgt.", bold = true))
                addView(ui.row(k, WaddleTokens.SPACE_2).apply {
                    addView(ui.button(k, "Velg alle") {
                        valgt += liste.map { it.id }
                        vis()
                    }, lik())
                    addView(ui.button(k, "Fjern valg") {
                        valgt.clear()
                        vis()
                    }, lik())
                })
                addView(ui.button(k, "Skriv brev til valgte", primary = true) {
                    if (valgt.isEmpty()) return@button
                    startActivity(Intent(k, MainActivity::class.java).putExtra("kunder", valgt.toTypedArray()).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    velger = false
                    valgt.clear()
                })
                addView(ui.button(k, "Sjekk Digipost for valgte") {
                    if (!opptatt && valgt.isNotEmpty()) sjekkAlle(Kunder.alle(k).filter { it.id in valgt })
                })
                addView(Ui.felt(k, "Gruppe", gruppeTekst) { gruppeTekst = it })
                addView(ui.button(k, "Sett gruppe på valgte") {
                    val gruppe = gruppeTekst.trim()
                    if (valgt.isEmpty()) return@button
                    Kunder.lagre(k, Kunder.alle(k).onEach { if (it.id in valgt) it.gruppe = gruppe })
                    status = if (gruppe.isEmpty()) "Fjernet gruppen fra ${valgt.size} kunder." else "${valgt.size} kunder er i gruppen $gruppe."
                    vis()
                })
                addView(ui.button(k, if (slett) "Trykk igjen for å slette ${valgt.size} kunder" else "Slett valgte") {
                    if (valgt.isEmpty()) return@button
                    if (!slett) {
                        slett = true
                        vis()
                        return@button
                    }
                    slett = false
                    Kunder.lagre(k, Kunder.alle(k).filter { it.id !in valgt })
                    status = "Slettet ${valgt.size} kunder."
                    valgt.clear()
                    vis()
                })
            })
            (if (opptatt) "Venter på Digipost." else status)?.let { addView(ui.text(k, it, muted = true)) }
            addView(ui.box(k).apply {
                if (liste.isEmpty()) addView(ui.text(k, if (Kunder.alle(k).isEmpty()) "Ingen kunder ennå. Legg til en, eller importer en CSV-fil." else "Ingen treff.", muted = true))
                liste.forEachIndexed { i, c ->
                    if (i > 0) addView(ui.divider(k))
                    val detalj = listOf(c.statusTekst(), c.sted, c.gruppe).filter { it.isNotBlank() }.joinToString(" · ")
                    val ikon = when {
                        velger && c.id in valgt -> "check"
                        c.status == "DIGIPOST" -> "mailbox"
                        c.status == "IKKE" || c.adresse().isEmpty() -> "mail"
                        else -> "user"
                    }
                    addView(Deler.klikkbar(ui.listRow(k, c.navn, detalj, iconName = ikon)) {
                        if (velger) {
                            if (!valgt.remove(c.id)) valgt += c.id
                            vis()
                        } else startActivity(Intent(k, KundeActivity::class.java).putExtra("id", c.id))
                    })
                }
            })
            addView(ui.button(k, "Sjekk Digipost for alle som ikke er sjekket") {
                if (!opptatt) sjekkAlle(Kunder.alle(k).filter { it.status.isEmpty() && it.adresse().isNotEmpty() })
            })
            addView(ui.row(k, WaddleTokens.SPACE_2).apply {
                addView(ui.button(k, "Importer CSV") {
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 1)
                }, lik())
                addView(ui.button(k, "Eksporter CSV") {
                    eksport = Kunder.eksport(Kunder.alle(k))
                    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv").putExtra(Intent.EXTRA_TITLE, "kunder.csv"), 2)
                }, lik())
            })
            addView(ui.text(k, "CSV: første linje er kolonnenavn. Appen kjenner navn, nummer (fødselsnr. eller org.nr.), digipost, telefon, epost, adresse, adresse2, postnr, sted, gruppe og notat. Andre kolonner blir flettefelt.", WaddleTokens.TEXT_SM, muted = true))
        }
        Ui.skjerm(this, innhold)
    }
}
