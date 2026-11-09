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
    private var status: String? = null

    private fun last() {
        avsender = Lager.tekst(this, "avsender")
        passord = Lager.tekst(this, "passord")
        paVegneAv = Lager.tekst(this, "pa_vegne_av")
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
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
        Lager.sertifikat(this).writeBytes(bytes)
        status = "Sertifikatet er lagt inn. Trykk Sjekk for å prøve det."
        vis()
    }

    private fun lagre() {
        Lager.settTekst(this, "avsender", avsender.trim())
        Lager.settTekst(this, "passord", passord)
        Lager.settTekst(this, "pa_vegne_av", paVegneAv.trim())
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

            addView(ui.box(k).apply {
                addView(ui.text(k, if (test) "Avsender i testmiljøet" else "Avsender i produksjon", WaddleTokens.TEXT_LG, bold = true))
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

            addView(ui.box(k).apply {
                addView(ui.text(k, "Standard for nye brev", WaddleTokens.TEXT_LG, bold = true))
                addView(Ui.bytt(k, "Innlogging for å åpne", Ui.niva(Lager.streng(k, "niva", "PASSWORD"))) {
                    Lager.settStreng(k, "niva", Ui.neste(Ui.nivaer, Lager.streng(k, "niva", "PASSWORD")))
                    vis()
                })
                addView(ui.divider(k))
                addView(Ui.bryter(k, "Sensitivt", "Emnet skjules til mottakeren har logget inn.", Lager.bryter(k, "sensitiv")) {
                    Lager.settBryter(k, "sensitiv", !Lager.bryter(k, "sensitiv"))
                    vis()
                })
                addView(ui.divider(k))
                addView(Ui.bytt(k, "SMS-varsel hvis brevet ikke er åpnet", Ui.sms(Lager.tall(k, "sms_timer"))) {
                    Lager.settTall(k, "sms_timer", Ui.neste(Ui.smsTimer, Lager.tall(k, "sms_timer")))
                    vis()
                })
                addView(ui.divider(k))
                addView(Ui.bryter(k, "SMS selv om brevet er åpnet", "Sender SMS-varselet uansett.", Lager.bryter(k, "sms_alltid")) {
                    Lager.settBryter(k, "sms_alltid", !Lager.bryter(k, "sms_alltid"))
                    vis()
                })
            })

            addView(ui.box(k).apply {
                addView(ui.text(k, "Sending", WaddleTokens.TEXT_LG, bold = true))
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

            addView(ui.box(k).apply {
                addView(ui.text(k, "Data", WaddleTokens.TEXT_LG, bold = true))
                addView(ui.button(k, "Slett historikk") {
                    Lager.settHistorikk(k, emptyList())
                    status = "Historikken er slettet."
                    vis()
                })
                addView(ui.button(k, "Slett favoritter") {
                    Lager.settFavoritter(k, emptyList())
                    status = "Favorittene er slettet."
                    vis()
                })
            })
        }
        Ui.skjerm(this, innhold)
        Deler.tastatur(this)
    }
}
