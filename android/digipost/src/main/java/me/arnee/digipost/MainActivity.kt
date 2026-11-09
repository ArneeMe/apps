package me.arnee.digipost

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import java.time.OffsetDateTime
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class MainActivity : Activity() {
    private val typer = mapOf("application/pdf" to "pdf", "text/html" to "html", "image/png" to "png", "image/jpeg" to "jpg", "image/gif" to "gif")
    private val papirvalg = listOf("AV", "RESERVE", "ULEST", "BARE")
    private val dagvalg = listOf(1, 3, 7, 14, 30)
    private val datavalg = listOf("INGEN", "FAKTURA", "LENKE", "AVTALE")

    private var sokTekst = ""
    private var treff: List<Mottaker> = emptyList()
    private var mottaker: Mottaker? = null
    private var emne = ""
    private val dokumenter = mutableListOf<Dokument>()
    private var standard = Valg()
    private var sensitiv = false
    private var niva = "PASSWORD"
    private var smsTimer = 0
    private var leveres: OffsetDateTime? = null
    private var papir = "AV"
    private var ulestDager = 7
    private var post = Papir("", "", "", "", "")
    private var farger = false
    private var makuler = false
    private var registrer = false
    private var telefon = ""
    private var registrerDager = 14
    private var data = "INGEN"
    private val felt = mutableMapOf<String, String>()
    private var forfall: OffsetDateTime? = null
    private var frist: OffsetDateTime? = null
    private var start: OffsetDateTime? = null
    private var slutt: OffsetDateTime? = null
    private var status: String? = null
    private var opptatt = false
    private var bekreft = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nullstill()
        if (savedInstanceState == null) mottak(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        mottak(intent)
    }

    override fun onResume() {
        super.onResume()
        vis()
    }

    private fun nullstill() {
        standard = Lager.valg(this)
        sensitiv = standard.sensitiv
        niva = standard.niva
        smsTimer = standard.smsTimer
        farger = standard.farger
        makuler = standard.makuler
        ulestDager = if (standard.ulestDager > 0) standard.ulestDager else 7
        papir = if (Lager.bryter(this, "papir_reserve")) "RESERVE" else "AV"
        registrerDager = standard.registrerDager
        leveres = null
        data = "INGEN"
        felt.clear()
        forfall = null
        frist = null
        start = null
        slutt = null
    }

    private fun velgMottaker(m: Mottaker?) {
        mottaker = m
        treff = emptyList()
        bekreft = false
        m?.post?.let { post = it }
        if (m != null && post.navn.isBlank() && !m.navn.startsWith("Fødselsnummer") && !m.navn.startsWith("Org.nr.")) post = Papir(m.navn, post.linje1, post.linje2, post.postnr, post.sted)
    }

    @Suppress("DEPRECATION")
    private fun mottak(intent: Intent?) {
        intent ?: return
        val navn = intent.getStringExtra("navn")
        val adresse = intent.getStringExtra("adresse")
        if (navn != null && adresse != null) velgMottaker(Lager.favoritter(this).firstOrNull { it.adresse == adresse } ?: Mottaker(navn, adresse, adresse))
        intent.getStringExtra("sok")?.let {
            velgMottaker(null)
            sokTekst = it
            if (Lager.oppsett(this) != null) sok()
        }
        when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { les(it) }
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.forEach { les(it) }
        }
    }

    private fun les(uri: Uri) {
        val navn = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Dokument"
        val endelse = navn.substringAfterLast('.', "").lowercase()
        val type = endelse.takeIf { it in setOf("pdf", "html", "htm", "png", "jpg", "jpeg", "gif") } ?: typer[contentResolver.getType(uri)]
        if (type == null) {
            status = "$navn kan ikke sendes. Digipost tar PDF, HTML og bilder."
            return
        }
        val innhold = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return
        val tittel = if (endelse.isEmpty()) navn else navn.substringBeforeLast('.')
        dokumenter += Dokument(tittel, type, innhold)
        if (emne.isEmpty()) emne = tittel
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK || data == null) return
        val flere = data.clipData
        if (flere != null) (0 until flere.itemCount).forEach { les(flere.getItemAt(it).uri) } else data.data?.let { les(it) }
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
                status = resultat.getOrElse { if (it is Feil) it.message else "Fikk ikke kontakt med Digipost. Sjekk nettet, sertifikatet og passordet." }
                vis()
            }
        }.start()
    }

    private fun sok() {
        val tekst = sokTekst.trim().replace(" ", "")
        if (tekst.isEmpty() || opptatt) return
        treff = emptyList()
        when {
            tekst.contains("#") -> {
                velgMottaker(Mottaker(tekst, tekst, "Digipost-adresse"))
                vis()
            }
            tekst.matches(Regex("\\d{11}|\\d{9}")) -> bakgrunn {
                val person = tekst.length == 11
                val navn = if (person) "Fødselsnummer $tekst" else "Org.nr. $tekst"
                when (Digipost.identifiser(it, tekst)) {
                    "DIGIPOST" -> {
                        treff = listOf(Mottaker(navn, tekst, "Har Digipost"))
                        null
                    }
                    "INVALID" -> if (person) "Ugyldig fødselsnummer." else "Ugyldig organisasjonsnummer."
                    else -> {
                        treff = listOf(Mottaker(navn, tekst, if (person) "Har ikke Digipost. Send på papir eller be om registrering." else "Har ikke Digipost. Send på papir."))
                        null
                    }
                }
            }
            else -> bakgrunn {
                treff = Digipost.sok(it, sokTekst.trim())
                if (treff.isEmpty()) "Ingen treff. Du kan sende på papir under Papir." else null
            }
        }
    }

    private fun tekst(navn: String) = felt[navn] ?: ""

    private fun dataverdi(): Data? = when (data) {
        "FAKTURA" -> forfall?.let { Faktura(tekst("belop"), tekst("konto"), tekst("kid"), it) }
        "LENKE" -> Lenke(tekst("url"), tekst("knapp"), tekst("beskrivelse"), frist)
        "AVTALE" -> start?.let { Avtale(it, slutt, tekst("oppmote"), tekst("sted"), tekst("gate"), tekst("postnr"), tekst("by"), tekst("undertittel"), tekst("info")) }
        else -> null
    }

    private fun mangler(): String? {
        val m = mottaker
        return when {
            m == null && papir != "BARE" -> "Velg en mottaker, eller velg Bare papir."
            emne.isBlank() -> "Skriv et emne."
            dokumenter.isEmpty() -> "Legg til en fil."
            papir != "AV" && post.tom() -> "Fyll inn navn, postnummer og sted for papirbrevet."
            papir != "AV" && standard.retur == null -> "Legg inn returadresse i innstillingene for å sende på papir."
            registrer && m != null && m.adresse.matches(Regex("\\d{11}")) && telefon.isBlank() -> "Skriv telefonnummeret til den som skal registrere seg."
            data == "FAKTURA" && (tekst("belop").isBlank() || tekst("konto").isBlank() || forfall == null) -> "Fakturaen trenger beløp, kontonummer og forfall."
            data == "LENKE" && !tekst("url").startsWith("https://") -> "Lenken må starte med https://."
            data == "AVTALE" && start == null -> "Avtalen trenger starttid."
            else -> null
        }
    }

    private fun send() {
        val m = mottaker
        val filer = dokumenter.toList()
        val tittel = emne.trim()
        val bruk = papir != "AV"
        val v = Valg(
            sensitiv, niva, smsTimer, standard.smsAlltid, leveres,
            papir = if (bruk) post else null, retur = standard.retur, farger = farger, makuler = makuler,
            ulestDager = if (papir == "ULEST") ulestDager else 0,
            registrerTelefon = if (registrer) telefon else "", registrerDager = registrerDager, data = dataverdi(),
        )
        val adresse = if (papir == "BARE") "" else m?.adresse ?: ""
        val navn = m?.navn?.takeIf { papir != "BARE" } ?: post.navn
        val test = Lager.test(this)
        val tom = Lager.bryter(this, "tom", true)
        val husk = Lager.bryter(this, "historikk", true)
        bekreft = false
        bakgrunn {
            val levering = Digipost.send(it, adresse, tittel, filer, v)
            if (husk) Lager.settHistorikk(this, Lager.historikk(this) + Sending(
                levering.dokument, test, System.currentTimeMillis(), navn, adresse, tittel, filer.size,
                levering.status, levering.kanal, "", "", v.leveres?.toString() ?: "",
            ))
            if (tom) runOnUiThread {
                dokumenter.clear()
                emne = ""
                nullstill()
            }
            val nar = if (v.leveres != null) "Leveres ${Ui.tid(v.leveres)}" else Ui.status(levering.status)
            "Sendt til $navn. $nar${if (levering.kanal.isNotEmpty()) " via ${Ui.kanal(levering.kanal)}" else ""}."
        }
    }

    private fun skriv(k: LinearLayout, hint: String, navn: String, type: Int? = null) {
        k.addView(Ui.felt(this, hint, tekst(navn), type) { felt[navn] = it })
    }

    private fun tidsrad(k: LinearLayout, tittel: String, verdi: OffsetDateTime?, valgfri: Boolean, satt: (OffsetDateTime?) -> Unit) {
        val detalj = if (verdi == null) "Ikke valgt. Trykk for å velge." else "${Ui.tid(verdi)}. Trykk for å endre."
        k.addView(Deler.klikkbar(WaddleUi.listRow(this, tittel, detalj)) {
            Ui.velgTid(this, verdi) {
                satt(it)
                vis()
            }
        })
        if (valgfri && verdi != null) k.addView(WaddleUi.button(this, "Fjern $tittel".lowercase().replaceFirstChar { it.uppercase() }) {
            satt(null)
            vis()
        })
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val innhold = ui.column(k, WaddleTokens.SPACE_6)
        innhold.addView(Deler.tittel(k, "Digipost", "gear", "Innstillinger") {
            startActivity(Intent(k, InnstillingerActivity::class.java))
        })
        if (Lager.test(k)) innhold.addView(ui.text(k, "Testmiljø. Brevene går ikke til ekte mottakere.", bold = true))
        if (Lager.oppsett(k) == null) {
            innhold.addView(ui.box(k).apply {
                addView(ui.text(k, "Legg inn avsender-ID, sertifikat og passord først.", muted = true))
                addView(ui.button(k, "Åpne innstillinger", primary = true) { startActivity(Intent(k, InnstillingerActivity::class.java)) })
            })
            Ui.skjerm(this, innhold)
            return
        }
        innhold.apply {
            addView(ui.row(k, WaddleTokens.SPACE_2).apply {
                val lik = { LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
                addView(ui.button(k, "Samtaler") { startActivity(Intent(k, SamtalerActivity::class.java)) }, lik())
                addView(ui.button(k, "Sendte brev") { startActivity(Intent(k, SendtActivity::class.java)) }, lik())
            })

            addView(ui.box(k).apply {
                addView(ui.text(k, "Mottaker", WaddleTokens.TEXT_LG, bold = true))
                val m = mottaker
                val favoritter = Lager.favoritter(k)
                if (m != null) {
                    addView(Deler.klikkbar(ui.listRow(k, m.navn, m.detalj + ". Trykk for å bytte.", iconName = "user")) {
                        velgMottaker(null)
                        vis()
                    })
                    val favoritt = favoritter.any { it.adresse == m.adresse }
                    addView(ui.button(k, if (favoritt) "Fjern favoritt" else "Lagre som favoritt") {
                        val lagret = Mottaker(m.navn, m.adresse, m.detalj, if (post.tom()) m.post else post)
                        Lager.settFavoritter(k, if (favoritt) favoritter.filter { it.adresse != m.adresse } else favoritter + lagret)
                        vis()
                    })
                } else {
                    favoritter.forEach { f ->
                        addView(Deler.klikkbar(ui.listRow(k, f.navn, f.detalj, iconName = "star")) {
                            velgMottaker(f)
                            vis()
                        })
                    }
                    addView(Ui.felt(k, "Navn, fødselsnr., org.nr. eller adresse", sokTekst) { sokTekst = it }.apply {
                        imeOptions = EditorInfo.IME_ACTION_SEARCH
                        setOnEditorActionListener { _, handling, _ ->
                            if (handling == EditorInfo.IME_ACTION_SEARCH) sok()
                            handling == EditorInfo.IME_ACTION_SEARCH
                        }
                    })
                    addView(ui.button(k, "Søk") { sok() })
                    if (opptatt && treff.isEmpty()) addView(ui.text(k, "Søker.", muted = true))
                    treff.forEach { t ->
                        addView(Deler.klikkbar(ui.listRow(k, t.navn, t.detalj, iconName = "user")) {
                            velgMottaker(t)
                            vis()
                        })
                    }
                }
            })

            addView(ui.box(k).apply {
                addView(ui.text(k, "Brev", WaddleTokens.TEXT_LG, bold = true))
                addView(Ui.felt(k, "Emne", emne) { emne = it })
                if (dokumenter.isEmpty()) addView(ui.text(k, "Ingen filer valgt.", muted = true))
                dokumenter.toList().forEachIndexed { i, d ->
                    val rolle = if (i == 0) "Hoveddokument" else "Vedlegg"
                    addView(Deler.klikkbar(ui.listRow(k, "${d.navn}.${d.type}", "$rolle · ${Ui.storrelse(d.innhold.size)}. Trykk for å fjerne.", iconName = "file-text")) {
                        dokumenter.remove(d)
                        vis()
                    })
                }
                addView(ui.button(k, if (dokumenter.isEmpty()) "Legg til fil" else "Legg til vedlegg") {
                    startActivityForResult(
                        Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                            .putExtra(Intent.EXTRA_MIME_TYPES, typer.keys.toTypedArray()).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true),
                        2,
                    )
                })
            })

            addView(ui.box(k).apply {
                addView(ui.text(k, "Valg", WaddleTokens.TEXT_LG, bold = true))
                addView(Ui.bytt(k, "Innlogging for å åpne", Ui.niva(niva)) {
                    niva = Ui.neste(Ui.nivaer, niva)
                    vis()
                })
                addView(ui.divider(k))
                addView(Ui.bryter(k, "Sensitivt", "Emnet skjules til mottakeren har logget inn.", sensitiv) {
                    sensitiv = !sensitiv
                    vis()
                })
                addView(ui.divider(k))
                addView(Ui.bytt(k, "SMS-varsel", Ui.sms(smsTimer)) {
                    smsTimer = Ui.neste(Ui.smsTimer, smsTimer)
                    vis()
                })
                addView(ui.divider(k))
                tidsrad(this, "Levering", leveres, true) { leveres = it }
                if (leveres == null) addView(ui.text(k, "Uten tidspunkt går brevet med en gang.", WaddleTokens.TEXT_SM, muted = true))
            })

            addView(ui.box(k).apply {
                addView(ui.text(k, "Papir", WaddleTokens.TEXT_LG, bold = true))
                val forklaring = when (papir) {
                    "RESERVE" -> "Hvis mottakeren ikke har Digipost"
                    "ULEST" -> "Hvis brevet ikke er åpnet etter $ulestDager dager"
                    "BARE" -> "Bare papir, ikke digitalt"
                    else -> "Av"
                }
                addView(Ui.bytt(k, "Send på papir", forklaring) {
                    papir = Ui.neste(papirvalg, papir)
                    bekreft = false
                    vis()
                })
                if (papir == "ULEST") addView(Ui.bytt(k, "Dager før papir", "$ulestDager dager") {
                    ulestDager = Ui.neste(dagvalg, ulestDager)
                    vis()
                })
                if (papir != "AV") {
                    addView(Ui.felt(k, "Navn", post.navn) { post = Papir(it, post.linje1, post.linje2, post.postnr, post.sted) })
                    addView(Ui.felt(k, "Adresse", post.linje1) { post = Papir(post.navn, it, post.linje2, post.postnr, post.sted) })
                    addView(Ui.felt(k, "Adresse linje 2 (valgfri)", post.linje2) { post = Papir(post.navn, post.linje1, it, post.postnr, post.sted) })
                    addView(Ui.felt(k, "Postnummer", post.postnr, InputType.TYPE_CLASS_NUMBER) { post = Papir(post.navn, post.linje1, post.linje2, it, post.sted) })
                    addView(Ui.felt(k, "Sted", post.sted) { post = Papir(post.navn, post.linje1, post.linje2, post.postnr, it) })
                    addView(Ui.bryter(k, "Farger", "Skriver ut i farger. Koster mer enn svart-hvitt.", farger) {
                        farger = !farger
                        vis()
                    })
                    addView(Ui.bryter(k, "Makuler hvis det ikke kommer fram", "Ellers sendes brevet tilbake til returadressen.", makuler) {
                        makuler = !makuler
                        vis()
                    })
                    addView(ui.text(k, standard.retur?.let { "Returadresse: ${it.tekst()}" } ?: "Mangler returadresse. Legg den inn i innstillingene.", WaddleTokens.TEXT_SM, muted = true))
                }
            })

            val m = mottaker
            if (m != null && m.adresse.matches(Regex("\\d{11}"))) addView(ui.box(k).apply {
                addView(ui.text(k, "Registrering", WaddleTokens.TEXT_LG, bold = true))
                addView(Ui.bryter(k, "Be om registrering", "Hvis personen ikke har Digipost, får de en SMS om å registrere seg og hente brevet.", registrer) {
                    registrer = !registrer
                    vis()
                })
                if (registrer) {
                    addView(Ui.felt(k, "Mobilnummer", telefon, InputType.TYPE_CLASS_PHONE) { telefon = it })
                    addView(Ui.bytt(k, "Frist for å registrere seg", "$registrerDager dager") {
                        registrerDager = Ui.neste(dagvalg, registrerDager)
                        vis()
                    })
                    if (papir != "AV") addView(ui.text(k, "Kommer de ikke innen fristen, går brevet på papir.", WaddleTokens.TEXT_SM, muted = true))
                }
            })

            addView(ui.box(k).apply {
                addView(ui.text(k, "Ekstra i Digipost", WaddleTokens.TEXT_LG, bold = true))
                val navn = mapOf("INGEN" to "Ingen", "FAKTURA" to "Faktura med betaling", "LENKE" to "Knapp med lenke", "AVTALE" to "Avtale i kalenderen")
                addView(Ui.bytt(k, "Legg til", navn[data] ?: data) {
                    data = Ui.neste(datavalg, data)
                    vis()
                })
                when (data) {
                    "FAKTURA" -> {
                        skriv(this, "Beløp i kroner", "belop", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
                        skriv(this, "Kontonummer", "konto", InputType.TYPE_CLASS_NUMBER)
                        skriv(this, "KID (valgfri)", "kid", InputType.TYPE_CLASS_NUMBER)
                        tidsrad(this, "Forfall", forfall, false) { forfall = it }
                        addView(ui.text(k, "Mottakeren kan betale fakturaen rett fra Digipost.", WaddleTokens.TEXT_SM, muted = true))
                    }
                    "LENKE" -> {
                        skriv(this, "Adresse (https://)", "url", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
                        skriv(this, "Tekst på knappen (valgfri)", "knapp")
                        skriv(this, "Beskrivelse (valgfri)", "beskrivelse")
                        tidsrad(this, "Frist", frist, true) { frist = it }
                    }
                    "AVTALE" -> {
                        tidsrad(this, "Start", start, false) { start = it }
                        tidsrad(this, "Slutt", slutt, true) { slutt = it }
                        skriv(this, "Oppmøte, for eksempel 15 minutter før (valgfri)", "oppmote")
                        skriv(this, "Sted (valgfri)", "sted")
                        skriv(this, "Gateadresse (valgfri)", "gate")
                        skriv(this, "Postnummer (valgfri)", "postnr", InputType.TYPE_CLASS_NUMBER)
                        skriv(this, "Poststed (valgfri)", "by")
                        skriv(this, "Undertittel (valgfri)", "undertittel")
                        skriv(this, "Informasjon til mottakeren (valgfri)", "info")
                    }
                }
            })

            val feil = mangler()
            val hvem = if (papir == "BARE") post.navn.ifBlank { "papirmottakeren" } else m?.navn ?: ""
            if (bekreft && feil == null) {
                addView(ui.box(k).apply {
                    addView(ui.text(k, "Sende «${emne.trim()}» til $hvem${if (papir == "BARE") " på papir" else ""}${if (Lager.test(k)) " i testmiljøet" else ""}?", bold = true))
                    addView(ui.button(k, "Send", primary = true) { if (!opptatt) send() })
                    addView(ui.button(k, "Avbryt") {
                        bekreft = false
                        vis()
                    })
                })
            } else {
                addView(ui.button(k, "Send", primary = true) {
                    if (opptatt) return@button
                    if (feil != null) {
                        status = feil
                        vis()
                    } else if (Lager.bryter(k, "bekreft", true)) {
                        bekreft = true
                        vis()
                    } else send()
                })
            }
            (if (opptatt) "Venter på Digipost." else status)?.let { addView(ui.text(k, it, muted = true)) }
        }
        Ui.skjerm(this, innhold)
        Deler.tastatur(this)
    }
}
