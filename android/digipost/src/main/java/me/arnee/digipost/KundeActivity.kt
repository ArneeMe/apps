package me.arnee.digipost

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class KundeActivity : Activity() {
    private lateinit var kunde: Kunde
    private var ny = true
    private var status: String? = null
    private var opptatt = false
    private var slett = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val eksisterende = intent.getStringExtra("id")?.let { Kunder.en(this, it) }
        ny = eksisterende == null
        kunde = eksisterende ?: Kunde()
    }

    override fun onResume() {
        super.onResume()
        vis()
    }

    override fun onPause() {
        super.onPause()
        if (!ny && !slett) Kunder.sett(this, kunde)
    }

    private fun lagre(): Boolean {
        if (kunde.navn.isBlank()) {
            status = "Kunden trenger et navn."
            vis()
            return false
        }
        Kunder.sett(this, kunde)
        ny = false
        return true
    }

    private fun sjekk() {
        val o = Lager.oppsett(this) ?: return
        if (kunde.adresse().isEmpty()) {
            status = "Legg inn fødselsnummer, org.nr. eller Digipost-adresse først."
            vis()
            return
        }
        opptatt = true
        vis()
        Thread {
            val resultat = runCatching { Kunder.sjekk(o, kunde) }
            runOnUiThread {
                opptatt = false
                status = resultat.fold({ kunde.statusTekst() + "." }, { if (it is Feil) it.message else "Fikk ikke kontakt med Digipost." })
                if (!ny) Kunder.sett(this, kunde)
                vis()
            }
        }.start()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val c = kunde
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, if (ny) "Ny kunde" else c.navn.ifBlank { "Kunde" }))
            if (!ny) addView(ui.box(k).apply {
                addView(ui.text(k, c.statusTekst() + if (c.sjekket > 0) ". Sjekket ${Ui.tid(c.sjekket)}." else ".", bold = true))
                addView(ui.button(k, "Skriv brev", primary = true) {
                    if (lagre()) startActivity(Intent(k, MainActivity::class.java).putExtra("kunder", arrayOf(c.id)).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                })
                addView(ui.button(k, "Sjekk Digipost") { if (!opptatt) sjekk() })
                addView(ui.button(k, "Se samtalen") { startActivity(Intent(k, SamtaleActivity::class.java).putExtra("nokkel", Samtaler.nokkel(c.navn))) })
            })
            (if (opptatt) "Venter på Digipost." else status)?.let { addView(ui.text(k, it, muted = true)) }

            addView(ui.box(k).apply {
                addView(ui.text(k, "Kunde", WaddleTokens.TEXT_LG, bold = true))
                addView(Ui.felt(k, "Navn", c.navn) { c.navn = it })
                addView(Ui.felt(k, "Fødselsnummer eller org.nr.", c.nummer, InputType.TYPE_CLASS_NUMBER) { c.nummer = it })
                addView(Ui.felt(k, "Digipost-adresse (valgfri)", c.digipost) { c.digipost = it })
                addView(Ui.felt(k, "Mobil", c.telefon, InputType.TYPE_CLASS_PHONE) { c.telefon = it })
                addView(Ui.felt(k, "E-post", c.epost, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) { c.epost = it })
                addView(Ui.felt(k, "Gruppe", c.gruppe) { c.gruppe = it })
            })

            addView(ui.box(k).apply {
                addView(ui.text(k, "Postadresse", WaddleTokens.TEXT_LG, bold = true))
                addView(Ui.felt(k, "Adresse", c.linje1) { c.linje1 = it })
                addView(Ui.felt(k, "Adresse linje 2", c.linje2) { c.linje2 = it })
                addView(Ui.felt(k, "Postnummer", c.postnr, InputType.TYPE_CLASS_NUMBER) { c.postnr = it })
                addView(Ui.felt(k, "Sted", c.sted) { c.sted = it })
                addView(ui.text(k, "Brukes når kunden ikke har Digipost, og i brev du skriver i appen.", WaddleTokens.TEXT_SM, muted = true))
            })

            addView(ui.box(k).apply {
                addView(ui.text(k, "Flettefelt", WaddleTokens.TEXT_LG, bold = true))
                addView(Deler.felt(k, "En per linje, for eksempel\nbeløp: 1200\nkid: 123456", flerlinje = true).apply {
                    setText(c.ekstra)
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) {
                            c.ekstra = s.toString()
                        }
                    })
                })
                addView(ui.text(k, "Skriv {navn}, {fornavn}, {adresse}, {dato} eller {beløp} i emne og brevtekst, så fylles det inn per kunde. Feltene beløp, kid, konto og forfall brukes også i faktura.", WaddleTokens.TEXT_SM, muted = true))
            })

            addView(ui.box(k).apply {
                addView(ui.text(k, "Notat", WaddleTokens.TEXT_LG, bold = true))
                addView(Deler.felt(k, "Notat", flerlinje = true).apply {
                    setText(c.notat)
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) {
                            c.notat = s.toString()
                        }
                    })
                })
            })

            addView(ui.button(k, "Lagre", primary = true) {
                if (lagre()) {
                    status = "Lagret."
                    vis()
                }
            })

            if (!ny) {
                val sendt = Kunder.historikk(k, c)
                addView(ui.box(k).apply {
                    addView(ui.text(k, "Sendt til kunden", WaddleTokens.TEXT_LG, bold = true))
                    if (sendt.isEmpty()) addView(ui.text(k, "Ingenting ennå.", muted = true))
                    sendt.forEach { s ->
                        val detalj = listOf(Ui.tid(s.sendt), Ui.status(s.status), Ui.kanal(s.kanal), if (s.lest == "Y") "åpnet" else "").filter { it.isNotBlank() }.joinToString(" · ")
                        addView(Deler.klikkbar(ui.listRow(k, s.emne, detalj, iconName = if (s.deling) "folder" else "send")) {
                            if (s.deling) startActivity(Intent(k, DelingActivity::class.java).putExtra("dokument", s.dokument))
                        })
                    }
                })
                addView(ui.button(k, if (slett) "Trykk igjen for å slette ${c.navn}" else "Slett kunde") {
                    if (!slett) {
                        slett = true
                        vis()
                    } else {
                        Kunder.slett(k, c.id)
                        finish()
                    }
                })
            }
        }
        Ui.skjerm(this, innhold)
        Deler.tastatur(this)
    }
}
