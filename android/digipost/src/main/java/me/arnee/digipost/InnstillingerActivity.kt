package me.arnee.digipost

import android.app.Activity
import android.content.Intent
import android.text.InputType
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class InnstillingerActivity : Activity() {
    private var avsender = ""
    private var passord = ""
    private var paVegneAv = ""
    private val retur = mutableMapOf<String, String>()
    private val returfelt = listOf("retur_navn" to "Navn", "retur_linje1" to "Adresse", "retur_linje2" to "Adresse linje 2 (valgfri)", "retur_postnr" to "Postnummer", "retur_sted" to "Sted")
    private val dager = listOf(0, 1, 3, 7, 14, 30)
    private var status: String? = null
    private var kopiStatus: String? = null
    private var hentKopi = false
    private val apne = mutableSetOf<String>()
    private var slett: String? = null
    private var dataStatus: String? = null

    private fun bytt(navn: String) {
        if (!apne.remove(navn)) apne += navn
        vis()
    }

    private fun last() {
        avsender = Lager.tekst(this, "avsender")
        passord = Lager.tekst(this, "passord")
        paVegneAv = Lager.tekst(this, "pa_vegne_av")
        returfelt.forEach { (navn, _) -> retur[navn] = Lager.tekst(this, navn) }
    }

    override fun onResume() {
        super.onResume()
        last()
        vis()
    }

    override fun onPause() {
        super.onPause()
        lagre()
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        if (requestCode == 2) {
            Kopi.settMappe(this, uri)
            kopiStatus = "Lager kopi."
            vis()
            Thread {
                val feil = Kopi.skriv(this)
                runOnUiThread {
                    kopiStatus = feil ?: "Kopien er lagret i ${Kopi.navn(this, uri)}."
                    vis()
                }
            }.start()
            return
        }
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
        Lager.sertifikat(this).writeBytes(bytes)
        status = "Sertifikatet er lagt inn. Trykk Sjekk for å prøve det."
        vis()
    }

    private fun lagre() {
        Lager.settTekst(this, "avsender", avsender.trim())
        Lager.settTekst(this, "passord", passord)
        Lager.settTekst(this, "pa_vegne_av", paVegneAv.trim())
        retur.forEach { (navn, verdi) -> Lager.settTekst(this, navn, verdi.trim()) }
    }

    private fun sjekk() {
        lagre()
        val o = Lager.oppsett(this)
        if (o == null) {
            status = "Legg inn avsender-ID og sertifikat først."
            vis()
            return
        }
        status = "Spør Digipost."
        vis()
        Thread {
            val resultat = runCatching { Digipost.avsender(o) }
            runOnUiThread {
                status = resultat.fold({
                    if (it == "VALID_SENDER") "Alt virker. Digipost godtar avsenderen." else "Sertifikatet virker, men Digipost kjenner ikke avsenderen."
                }, {
                    if (it is Feil) it.message else "Fikk ikke kontakt. Sjekk nettet, sertifikatet og passordet."
                })
                vis()
            }
        }.start()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val test = Lager.test(k)
        if (Lager.oppsett(k) == null) apne += "avsender"
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Innstillinger"))

            addView(ui.box(k).apply {
                addView(ui.text(k, "Miljø", WaddleTokens.TEXT_LG, bold = true))
                addView(Ui.bryter(k, "Testmiljø", "Brevene går til Digipost sitt testmiljø og ikke til ekte mottakere.", test) {
                    lagre()
                    Lager.settTest(k, !test)
                    last()
                    status = null
                    vis()
                })
            })

            addView(Ui.seksjon(k, if (test) "Avsender i testmiljøet" else "Avsender i produksjon", if (Lager.oppsett(k) == null) "Ikke satt opp" else "Avsender-ID ${Lager.tekst(k, "avsender")}", "avsender" in apne, { bytt("avsender") }) {
                addView(Ui.felt(k, "Avsender-ID", avsender, InputType.TYPE_CLASS_NUMBER) { avsender = it })
                addView(Ui.felt(k, "Passord til sertifikatet", passord, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) { passord = it })
                addView(Ui.felt(k, "Send på vegne av (avsender-ID, valgfritt)", paVegneAv, InputType.TYPE_CLASS_NUMBER) { paVegneAv = it })
                addView(ui.text(k, "Bruk Send på vegne av hvis du er databehandler for en annen avsender.", WaddleTokens.TEXT_SM, muted = true))
                addView(ui.text(k, if (Lager.sertifikat(k).exists()) "Sertifikat lagt inn." else "Mangler sertifikat (.p12).", muted = true))
                addView(ui.button(k, "Velg sertifikat") {
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 1)
                })
                if (Lager.sertifikat(k).exists()) addView(ui.button(k, "Fjern sertifikat") {
                    Lager.sertifikat(k).delete()
                    status = null
                    vis()
                })
                addView(ui.button(k, "Lagre", primary = true) {
                    lagre()
                    status = "Lagret."
                    vis()
                })
                addView(ui.button(k, "Sjekk") { sjekk() })
                status?.let { addView(ui.text(k, it, muted = true)) }
            })

            addView(Ui.seksjon(k, "Standard for nye brev", listOfNotNull(Ui.niva(Lager.streng(k, "niva", "PASSWORD")), if (Lager.bryter(k, "sensitiv")) "sensitivt" else null, "SMS " + Ui.sms(Lager.tall(k, "sms_timer")).lowercase()).joinToString(" · "), "standard" in apne, { bytt("standard") }) {
                addView(Ui.valg(k, "Innlogging for å åpne", Ui.nivaer.map { it to Ui.niva(it) }, Lager.streng(k, "niva", "PASSWORD")) {
                    Lager.settStreng(k, "niva", it)
                    vis()
                })
                addView(ui.divider(k))
                addView(Ui.bryter(k, "Sensitivt", "Emnet skjules til mottakeren har logget inn.", Lager.bryter(k, "sensitiv")) {
                    Lager.settBryter(k, "sensitiv", !Lager.bryter(k, "sensitiv"))
                    vis()
                })
                addView(ui.divider(k))
                addView(Ui.valg(k, "SMS-varsel hvis brevet ikke er åpnet", Ui.smsTimer.map { it to Ui.sms(it) }, Lager.tall(k, "sms_timer")) {
                    Lager.settTall(k, "sms_timer", it)
                    vis()
                })
                addView(ui.divider(k))
                addView(Ui.bryter(k, "SMS selv om brevet er åpnet", "Sender SMS-varselet uansett.", Lager.bryter(k, "sms_alltid")) {
                    Lager.settBryter(k, "sms_alltid", !Lager.bryter(k, "sms_alltid"))
                    vis()
                })
            })

            addView(Ui.seksjon(k, "Papirpost", (if (Lager.retur(k) == null) "Mangler returadresse" else "Returadresse satt") + if (Lager.bryter(k, "papir_reserve", true)) " · papir som reserve" else "", "papir" in apne, { bytt("papir") }) {
                addView(ui.text(k, if (test) "Returadresse i testmiljøet" else "Returadresse", bold = true))
                returfelt.forEach { (navn, hint) ->
                    addView(Ui.felt(k, hint, retur[navn] ?: "", if (navn == "retur_postnr") InputType.TYPE_CLASS_NUMBER else null) { retur[navn] = it })
                }
                addView(ui.text(k, "Brev som ikke kommer fram, sendes hit. Papirpost virker ikke uten returadresse.", WaddleTokens.TEXT_SM, muted = true))
                addView(ui.divider(k))
                addView(Ui.bryter(k, "Papir som reserve", "Nye brev går på papir hvis mottakeren ikke har Digipost.", Lager.bryter(k, "papir_reserve", true)) {
                    lagre()
                    Lager.settBryter(k, "papir_reserve", !Lager.bryter(k, "papir_reserve", true))
                    vis()
                })
                addView(ui.divider(k))
                addView(Ui.valg(k, "Dager før papir når brevet ikke er åpnet", dager.drop(1).map { it to "$it dager" }, Lager.tall(k, "ulest_dager").takeIf { it > 0 } ?: 7) {
                    lagre()
                    Lager.settTall(k, "ulest_dager", it)
                    vis()
                })
                addView(ui.divider(k))
                addView(Ui.bryter(k, "Farger", "Skriver ut i farger. Koster mer enn svart-hvitt.", Lager.bryter(k, "farger")) {
                    lagre()
                    Lager.settBryter(k, "farger", !Lager.bryter(k, "farger"))
                    vis()
                })
                addView(ui.divider(k))
                addView(Ui.bryter(k, "Makuler hvis det ikke kommer fram", "Ellers sendes brevet tilbake.", Lager.bryter(k, "makuler")) {
                    lagre()
                    Lager.settBryter(k, "makuler", !Lager.bryter(k, "makuler"))
                    vis()
                })
                addView(ui.divider(k))
                addView(Ui.valg(k, "Frist for å registrere seg", dager.drop(1).map { it to "$it dager" }, Lager.tall(k, "registrer_dager", 14)) {
                    lagre()
                    Lager.settTall(k, "registrer_dager", it)
                    vis()
                })
            })

            addView(Ui.seksjon(k, "Varsler", listOfNotNull(if (Lager.bryter(k, "varsel_svar", true)) "svar" else null, if (Lager.bryter(k, "varsel_brev", true)) "status" else null).joinToString(" og ").ifEmpty { "Av" }.replaceFirstChar { it.uppercase() }, "varsler" in apne, { bytt("varsler") }) {
                addView(Ui.bryter(k, "Nye svar", "Varsler når noen sender brev til innboksen din.", Lager.bryter(k, "varsel_svar", true)) {
                    Lager.settBryter(k, "varsel_svar", !Lager.bryter(k, "varsel_svar", true))
                    Varsel.planlegg(k)
                    vis()
                })
                addView(ui.divider(k))
                addView(Ui.bryter(k, "Status for brev", "Varsler når et brev blir åpnet, postlagt eller feiler.", Lager.bryter(k, "varsel_brev", true)) {
                    Lager.settBryter(k, "varsel_brev", !Lager.bryter(k, "varsel_brev", true))
                    Varsel.planlegg(k)
                    vis()
                })
                addView(ui.divider(k))
                val minutter = Lager.tall(k, "varsel_minutter", 30)
                addView(Ui.valg(k, "Hvor ofte appen sjekker", Varsel.minutter.map { it to when {
                    it < 60 -> "Hvert $it. minutt"
                    it == 60 -> "Hver time"
                    else -> "Hver ${it / 60}. time"
                } }, minutter) {
                    Lager.settTall(k, "varsel_minutter", it)
                    Varsel.planlegg(k)
                    vis()
                })
                addView(ui.text(k, "Android kan utsette sjekken litt for å spare batteri.", WaddleTokens.TEXT_SM, muted = true))
            })

            addView(Ui.seksjon(k, "Sending", if (Lager.bryter(k, "bekreft", true)) "Bekreft før sending" else "Sender uten å spørre", "sending" in apne, { bytt("sending") }) {
                addView(Ui.bryter(k, "Bekreft før sending", "Spør en gang til før brevet går.", Lager.bryter(k, "bekreft", true)) {
                    Lager.settBryter(k, "bekreft", !Lager.bryter(k, "bekreft", true))
                    vis()
                })
                addView(ui.divider(k))
                addView(Ui.bryter(k, "Husk sendte brev", "Lagrer mottaker, emne og status på telefonen.", Lager.bryter(k, "historikk", true)) {
                    Lager.settBryter(k, "historikk", !Lager.bryter(k, "historikk", true))
                    vis()
                })
                addView(ui.divider(k))
                addView(Ui.bryter(k, "Tøm brevet etter sending", "Fjerner emne og filer når brevet er sendt.", Lager.bryter(k, "tom", true)) {
                    Lager.settBryter(k, "tom", !Lager.bryter(k, "tom", true))
                    vis()
                })
            })

            addView(Ui.seksjon(k, "Sikkerhetskopi av kunder", Kopi.mappe(k)?.let { "Lagres i ${Kopi.navn(k, it)}" } ?: "Av", "kopi" in apne, { bytt("kopi") }) {
                val mappe = Kopi.mappe(k)
                addView(ui.text(k, if (mappe == null) "Av. Velg en mappe, for eksempel en Syncthing-mappe." else "Lagres i ${Kopi.navn(k, mappe)} hver gang kundelisten endres." + if (Kopi.sist(k) > 0) " Sist ${Ui.tid(Kopi.sist(k))}." else "", muted = mappe == null))
                addView(ui.button(k, if (mappe == null) "Velg mappe" else "Bytt mappe") {
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 2)
                })
                if (mappe != null) {
                    addView(ui.button(k, "Lag kopi nå") {
                        kopiStatus = "Lager kopi."
                        vis()
                        Thread {
                            val feil = Kopi.skriv(k)
                            runOnUiThread {
                                kopiStatus = feil ?: "Kopien er lagret."
                                vis()
                            }
                        }.start()
                    })
                    addView(ui.button(k, if (hentKopi) "Trykk igjen for å erstatte kundelisten med kopien" else "Hent kunder fra kopien") {
                        if (!hentKopi) {
                            hentKopi = true
                            vis()
                            return@button
                        }
                        hentKopi = false
                        val kunder = runCatching { Kopi.les(k) }.getOrNull()
                        kopiStatus = if (kunder == null) "Fant ingen kopi i mappen." else {
                            Kunder.lagre(k, kunder)
                            "Hentet ${kunder.size} kunder fra kopien."
                        }
                        vis()
                    })
                    addView(ui.button(k, "Slå av kopi") {
                        Kopi.settMappe(k, null)
                        kopiStatus = null
                        vis()
                    })
                }
                kopiStatus?.let { addView(ui.text(k, it, muted = true)) }
                addView(ui.text(k, "Kopien har kundene som JSON og CSV. Sertifikat og passord er ikke med.", WaddleTokens.TEXT_SM, muted = true))
            })

            addView(Ui.seksjon(k, "Data", "Slett historikk og favoritter", "data" in apne, { bytt("data") }) {
                addView(ui.button(k, if (slett == "historikk") "Trykk igjen for å slette historikken" else "Slett historikk") {
                    if (slett != "historikk") {
                        slett = "historikk"
                    } else {
                        Lager.settHistorikk(k, emptyList())
                        slett = null
                        dataStatus = "Historikken er slettet."
                    }
                    vis()
                })
                addView(ui.button(k, if (slett == "favoritter") "Trykk igjen for å slette favorittene" else "Slett favoritter") {
                    if (slett != "favoritter") {
                        slett = "favoritter"
                    } else {
                        Lager.settFavoritter(k, emptyList())
                        slett = null
                        dataStatus = "Favorittene er slettet."
                    }
                    vis()
                })
                dataStatus?.let { addView(ui.text(k, it, muted = true)) }
            })
        }
        Ui.skjerm(this, innhold)
        Deler.tastatur(this)
    }
}
