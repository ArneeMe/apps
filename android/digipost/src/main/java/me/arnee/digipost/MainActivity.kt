package me.arnee.digipost

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
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
    private val datavalg = listOf("INGEN", "FAKTURA", "LENKE", "AVTALE", "DELING")
    private val delingsdager = listOf(7, 30, 60, 90)

    private var sokTekst = ""
    private var treff: List<Mottaker> = emptyList()
    private var mottaker: Mottaker? = null
    private val flere = mutableListOf<Mottaker>()
    private var epost = ""
    private var epostTekst = ""
    private var delDager = 30
    private var brevtekst = ""
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
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Varsel.planlegg(this)
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
        papir = if (Lager.bryter(this, "papir_reserve", true)) "RESERVE" else "AV"
        epost = ""
        epostTekst = ""
        delDager = 30
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
        if (m != null && !m.harDigipost && papir == "AV") papir = "RESERVE"
        if (m != null && post.navn.isBlank() && !m.navn.startsWith("Fødselsnummer") && !m.navn.startsWith("Org.nr.")) post = Papir(m.navn, post.linje1, post.linje2, post.postnr, post.sted)
    }

    @Suppress("DEPRECATION")
    private fun mottak(intent: Intent?) {
        intent ?: return
        val navn = intent.getStringExtra("navn")
        val adresse = intent.getStringExtra("adresse")
        if (navn != null && adresse != null) velgMottaker(Lager.favoritter(this).firstOrNull { it.adresse == adresse } ?: Mottaker(navn, adresse, adresse))
        intent.getStringArrayExtra("kunder")?.let { ider ->
            val valgte = ider.mapNotNull { Kunder.en(this, it) }.map { it.mottaker() }
            flere.clear()
            velgMottaker(null)
            if (valgte.size == 1) velgMottaker(valgte[0]) else flere += valgte
        }
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
                        treff = listOf(Mottaker(navn, tekst, if (person) "Har ikke Digipost. Går på papir, eller be om registrering." else "Har ikke Digipost. Går på papir.", harDigipost = false))
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

    private fun dataverdi(f: Map<String, String> = emptyMap()): Data? {
        val t = { navn: String, reserve: List<String> -> Kunder.flett(tekst(navn), f).ifBlank { reserve.firstNotNullOfOrNull { f[it]?.takeIf { v -> v.isNotBlank() } } ?: "" } }
        val dag = f["forfall"]?.let { v ->
            listOf("d.M.yyyy", "yyyy-MM-dd", "dd.MM.yyyy").firstNotNullOfOrNull { mønster ->
                runCatching { java.time.LocalDate.parse(v.trim(), java.time.format.DateTimeFormatter.ofPattern(mønster)).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime() }.getOrNull()
            }
        }
        return when (data) {
            "FAKTURA" -> (forfall ?: dag)?.let { Faktura(t("belop", listOf("beløp", "belop", "sum")), t("konto", listOf("konto", "kontonummer")), t("kid", listOf("kid")), it) }
            "LENKE" -> Lenke(t("url", listOf("url", "lenke")), t("knapp", emptyList()), t("beskrivelse", emptyList()), frist)
            "DELING" -> Deling(t("formal", emptyList()), delDager)
            "AVTALE" -> start?.let { Avtale(it, slutt, t("oppmote", emptyList()), t("sted", emptyList()), t("gate", emptyList()), t("postnr", emptyList()), t("by", emptyList()), t("undertittel", emptyList()), t("info", emptyList())) }
            else -> null
        }
    }

    private fun mangler(): String? {
        val m = mottaker
        return when {
            m == null && flere.isEmpty() && papir != "BARE" -> "Velg en mottaker, eller velg Bare papir."
            emne.isBlank() -> "Skriv et emne."
            dokumenter.isEmpty() && brevtekst.isBlank() -> "Skriv brevet eller legg til en fil."
            papir == "BARE" && flere.isNotEmpty() -> "Bare papir går til én mottaker om gangen."
            (papir == "BARE" || papir == "ULEST" || (papir == "RESERVE" && m != null && !m.harDigipost)) && post.tom() -> "Fyll inn navn, postnummer og sted for papirbrevet."
            papir == "AV" && (listOfNotNull(m) + flere).any { !it.harDigipost } -> "En mottaker har ikke Digipost. Slå på papir, eller fjern mottakeren."
            flere.any { !it.harDigipost && (it.post == null || it.post.tom()) } -> "En av de ekstra mottakerne mangler Digipost og postadresse."
            papir != "AV" && standard.retur == null -> "Legg inn returadresse i innstillingene for å sende på papir."
            registrer && m != null && m.adresse.matches(Regex("\\d{11}")) && telefon.isBlank() -> "Skriv telefonnummeret til den som skal registrere seg."
            data == "FAKTURA" && flere.isEmpty() && (tekst("belop").isBlank() || tekst("konto").isBlank() || forfall == null) -> "Fakturaen trenger beløp, kontonummer og forfall."
            data == "LENKE" && !tekst("url").startsWith("https://") -> "Lenken må starte med https://."
            data == "AVTALE" && start == null -> "Avtalen trenger starttid."
            data == "DELING" && tekst("formal").isBlank() -> "Skriv hvorfor du vil se dokumentene."
            epost.isNotBlank() && !epost.contains("@") -> "E-postadressen ser feil ut."
            else -> null
        }
    }

    private fun send() {
        val m = mottaker
        val filer = dokumenter.toList()
        val tittel = emne.trim()
        val alle = if (papir == "BARE") listOf(Mottaker(post.navn, "", post.tekst(), post, false)) else flere.toList() + listOfNotNull(m)
        val test = Lager.test(this)
        val tom = Lager.bryter(this, "tom", true)
        val husk = Lager.bryter(this, "historikk", true)
        val hovedpost = post
        val mal = brevtekst
        val dato = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d.M.yyyy"))
        bekreft = false
        bakgrunn { o ->
            val svar = alle.map { r ->
                val papiret = when {
                    papir == "AV" -> null
                    r === m || papir == "BARE" -> hovedpost.takeIf { !it.tom() }
                    else -> r.post?.takeIf { !it.tom() }
                }
                val f = mapOf("navn" to r.navn, "fornavn" to r.navn.substringBefore(" "), "dato" to dato) + r.flett
                val tittelR = Kunder.flett(tittel, f)
                val data = dataverdi(f)
                val filerR = if (mal.isBlank()) filer else listOf(Dokument(tittelR, "pdf", Pdf.brev(standard.retur, papiret ?: r.post, dato, tittelR, Kunder.flett(mal, f)))) + filer
                val v = Valg(
                    sensitiv, niva, smsTimer, standard.smsAlltid, leveres,
                    papir = papiret, retur = standard.retur, farger = farger, makuler = makuler,
                    ulestDager = if (papir == "ULEST") ulestDager else 0,
                    registrerTelefon = if (registrer && r === m) telefon else "", registrerDager = registrerDager, data = data,
                    epost = Kunder.flett(epost, f), epostTekst = Kunder.flett(epostTekst, f),
                )
                runCatching { Digipost.send(o, r.adresse, tittelR, filerR, v) }.map { levering ->
                    if (husk) Lager.settHistorikk(this, Lager.historikk(this) + Sending(
                        levering.dokument, test, System.currentTimeMillis(), r.navn, r.adresse, tittelR, filerR.size,
                        levering.status, levering.kanal, "", "", v.leveres?.toString() ?: "", deling = data is Deling,
                    ))
                    "${r.navn}: ${if (v.leveres != null) "leveres ${Ui.tid(v.leveres)}" else Ui.status(levering.status).lowercase()}${if (levering.kanal.isNotEmpty()) " via ${Ui.kanal(levering.kanal)}" else ""}"
                }.getOrElse { "${r.navn}: feilet. ${if (it is Feil) it.message else "Fikk ikke kontakt."}" }
            }
            val feilet = svar.count { it.contains(": feilet.") }
            if (tom && feilet == 0) runOnUiThread {
                dokumenter.clear()
                emne = ""
                brevtekst = ""
                flere.clear()
                nullstill()
            }
            (if (feilet == 0) "Sendt.\n" else "$feilet av ${svar.size} feilet.\n") + svar.joinToString("\n")
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
                addView(ui.button(k, "Kunder") { startActivity(Intent(k, KunderActivity::class.java)) }, lik())
            })

            addView(ui.box(k).apply {
                addView(ui.text(k, "Mottaker", WaddleTokens.TEXT_LG, bold = true))
                val m = mottaker
                val favoritter = Lager.favoritter(k)
                flere.toList().forEach { f ->
                    addView(Deler.klikkbar(ui.listRow(k, f.navn, "${f.detalj}. Trykk for å fjerne.", iconName = "users")) {
                        flere.remove(f)
                        vis()
                    })
                }
                if (m != null) {
                    addView(Deler.klikkbar(ui.listRow(k, m.navn, m.detalj + ". Trykk for å bytte.", iconName = "user")) {
                        velgMottaker(null)
                        vis()
                    })
                    val favoritt = favoritter.any { it.adresse == m.adresse }
                    addView(ui.button(k, if (favoritt) "Fjern favoritt" else "Lagre som favoritt") {
                        val lagret = Mottaker(m.navn, m.adresse, m.detalj, if (post.tom()) m.post else post, m.harDigipost)
                        Lager.settFavoritter(k, if (favoritt) favoritter.filter { it.adresse != m.adresse } else favoritter + lagret)
                        vis()
                    })
                    addView(ui.button(k, "Legg til flere mottakere") {
                        flere += Mottaker(m.navn, m.adresse, m.detalj, if (post.tom()) m.post else post, m.harDigipost)
                        post = Papir("", "", "", "", "")
                        velgMottaker(null)
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
                addView(Deler.felt(k, "Skriv brevet her, eller legg til en fil under", flerlinje = true).apply {
                    minLines = 4
                    setText(brevtekst)
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) {
                            brevtekst = s.toString()
                        }
                    })
                })
                addView(ui.text(k, "Teksten blir en PDF med returadresse, mottaker og dato. {navn}, {fornavn}, {dato} og flettefeltene fra kundelisten fylles inn per mottaker, også i emnet.", WaddleTokens.TEXT_SM, muted = true))
                if (dokumenter.isEmpty()) addView(ui.text(k, if (brevtekst.isBlank()) "Ingen filer valgt." else "Ingen vedlegg.", muted = true))
                dokumenter.toList().forEachIndexed { i, d ->
                    val rolle = if (i == 0 && brevtekst.isBlank()) "Hoveddokument" else "Vedlegg"
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
                addView(Ui.felt(k, "E-postvarsel til (valgfri)", epost, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) { epost = it })
                addView(Ui.felt(k, "Tekst i e-postvarselet (valgfri)", epostTekst) { epostTekst = it })
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
                if (papir == "RESERVE") addView(ui.text(k, "Adressen brukes bare hvis mottakeren ikke har Digipost. Da går brevet i posten.", WaddleTokens.TEXT_SM, muted = true))
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
                val navn = mapOf("INGEN" to "Ingen", "FAKTURA" to "Faktura med betaling", "LENKE" to "Knapp med lenke", "AVTALE" to "Avtale i kalenderen", "DELING" to "Be om dokumenter")
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
                    "DELING" -> {
                        skriv(this, "Hvorfor vil du se dokumentene?", "formal")
                        addView(Ui.bytt(k, "Hvor lenge du får se dem", "$delDager dager") {
                            delDager = Ui.neste(delingsdager, delDager)
                            vis()
                        })
                        addView(ui.text(k, "Mottakeren velger selv hvilke dokumenter de deler. Du ser dem under Sendte brev.", WaddleTokens.TEXT_SM, muted = true))
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
            val antall = flere.size + (if (m != null) 1 else 0)
            val hvem = if (papir == "BARE") post.navn.ifBlank { "papirmottakeren" } else if (antall > 1) "$antall mottakere" else m?.navn ?: flere.firstOrNull()?.navn ?: ""
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
