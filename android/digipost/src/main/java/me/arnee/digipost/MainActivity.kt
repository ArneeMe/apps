package me.arnee.digipost

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class MainActivity : Activity() {
    private val typer = mapOf("application/pdf" to "pdf", "text/html" to "html", "image/png" to "png", "image/jpeg" to "jpg", "image/gif" to "gif")
    private var sokTekst = ""
    private var treff: List<Mottaker> = emptyList()
    private var mottaker: Mottaker? = null
    private var emne = ""
    private val dokumenter = mutableListOf<Dokument>()
    private var valg = Valg()
    private var leveres: OffsetDateTime? = null
    private var status: String? = null
    private var opptatt = false
    private var bekreft = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        valg = Lager.valg(this)
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

    @Suppress("DEPRECATION")
    private fun mottak(intent: Intent?) {
        intent ?: return
        val navn = intent.getStringExtra("navn")
        val adresse = intent.getStringExtra("adresse")
        if (navn != null && adresse != null) {
            mottaker = Mottaker(navn, adresse, adresse)
            treff = emptyList()
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
        if (tekst.isEmpty()) return
        treff = emptyList()
        when {
            tekst.contains("#") -> {
                mottaker = Mottaker(tekst, tekst, "Digipost-adresse")
                vis()
            }
            tekst.matches(Regex("\\d{11}|\\d{9}")) -> bakgrunn {
                val person = tekst.length == 11
                when (Digipost.identifiser(it, tekst)) {
                    "DIGIPOST" -> {
                        treff = listOf(Mottaker(if (person) "Fødselsnummer $tekst" else "Org.nr. $tekst", tekst, "Har Digipost"))
                        null
                    }
                    "IDENTIFIED" -> if (person) "Personen finnes, men har ikke Digipost." else "Virksomheten finnes, men har ikke Digipost."
                    "INVALID" -> if (person) "Ugyldig fødselsnummer." else "Ugyldig organisasjonsnummer."
                    else -> "Fant ingen med det nummeret i Digipost."
                }
            }
            else -> bakgrunn {
                treff = Digipost.sok(it, sokTekst.trim())
                if (treff.isEmpty()) "Ingen treff." else null
            }
        }
    }

    private fun send() {
        val m = mottaker ?: return
        val filer = dokumenter.toList()
        val tittel = emne.trim()
        val v = Valg(valg.sensitiv, valg.niva, valg.smsTimer, valg.smsAlltid, leveres)
        val test = Lager.test(this)
        val tom = Lager.bryter(this, "tom", true)
        val husk = Lager.bryter(this, "historikk", true)
        bekreft = false
        bakgrunn {
            val levering = Digipost.send(it, m.adresse, tittel, filer, v)
            if (husk) Lager.settHistorikk(this, Lager.historikk(this) + Sending(
                levering.dokument, test, System.currentTimeMillis(), m.navn, m.adresse, tittel, filer.size,
                levering.status, levering.kanal, "", "", v.leveres?.toString() ?: "",
            ))
            if (tom) runOnUiThread {
                dokumenter.clear()
                emne = ""
                leveres = null
            }
            val nar = if (v.leveres != null) "Leveres ${tid(v.leveres)}" else Ui.status(levering.status)
            "Sendt til ${m.navn}. $nar${if (levering.kanal.isNotEmpty()) " via ${Ui.kanal(levering.kanal)}" else ""}."
        }
    }

    private fun tid(verdi: OffsetDateTime) =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(verdi.toInstant().toEpochMilli()))

    private fun velgTid() {
        val start = leveres?.toLocalDateTime() ?: LocalDateTime.now().plusHours(1)
        DatePickerDialog(this, { _, ar, maned, dag ->
            TimePickerDialog(this, { _, time, minutt ->
                leveres = LocalDateTime.of(ar, maned + 1, dag, time, minutt).atZone(ZoneId.systemDefault()).toOffsetDateTime()
                vis()
            }, start.hour, start.minute, true).show()
        }, start.year, start.monthValue - 1, start.dayOfMonth).show()
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
            ui.screen(this, innhold)
            return
        }
        innhold.apply {
            addView(ui.box(k).apply {
                addView(ui.text(k, "Mottaker", WaddleTokens.TEXT_LG, bold = true))
                val m = mottaker
                val favoritter = Lager.favoritter(k)
                if (m != null) {
                    addView(Deler.klikkbar(ui.listRow(k, m.navn, m.detalj + ". Trykk for å bytte.", iconName = "user")) {
                        mottaker = null
                        bekreft = false
                        vis()
                    })
                    val favoritt = favoritter.any { it.adresse == m.adresse }
                    addView(ui.button(k, if (favoritt) "Fjern favoritt" else "Lagre som favoritt") {
                        Lager.settFavoritter(k, if (favoritt) favoritter.filter { it.adresse != m.adresse } else favoritter + m)
                        vis()
                    })
                } else {
                    favoritter.forEach { f ->
                        addView(Deler.klikkbar(ui.listRow(k, f.navn, f.detalj, iconName = "star")) {
                            mottaker = f
                            vis()
                        })
                    }
                    addView(Ui.felt(k, "Navn, fødselsnr., org.nr. eller adresse", sokTekst) { sokTekst = it })
                    addView(ui.button(k, "Søk") { if (!opptatt) sok() })
                    treff.forEach { t ->
                        addView(Deler.klikkbar(ui.listRow(k, t.navn, t.detalj, iconName = "user")) {
                            mottaker = t
                            treff = emptyList()
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
                addView(Ui.bytt(k, "Innlogging for å åpne", Ui.niva(valg.niva)) {
                    valg = Valg(valg.sensitiv, Ui.neste(Ui.nivaer, valg.niva), valg.smsTimer, valg.smsAlltid)
                    vis()
                })
                addView(ui.divider(k))
                addView(Ui.bryter(k, "Sensitivt", "Emnet skjules til mottakeren har logget inn.", valg.sensitiv) {
                    valg = Valg(!valg.sensitiv, valg.niva, valg.smsTimer, valg.smsAlltid)
                    vis()
                })
                addView(ui.divider(k))
                addView(Ui.bytt(k, "SMS-varsel", Ui.sms(valg.smsTimer)) {
                    valg = Valg(valg.sensitiv, valg.niva, Ui.neste(Ui.smsTimer, valg.smsTimer), valg.smsAlltid)
                    vis()
                })
                addView(ui.divider(k))
                val nar = leveres
                addView(Deler.klikkbar(ui.listRow(k, "Levering", if (nar == null) "Nå. Trykk for å velge tidspunkt." else "${tid(nar)}. Trykk for å endre.")) { velgTid() })
                if (nar != null) addView(ui.button(k, "Send nå i stedet") {
                    leveres = null
                    vis()
                })
            })

            val m = mottaker
            val mangler = when {
                m == null -> "Velg en mottaker."
                emne.isBlank() -> "Skriv et emne."
                dokumenter.isEmpty() -> "Legg til en fil."
                else -> null
            }
            if (bekreft && m != null && mangler == null) {
                addView(ui.box(k).apply {
                    addView(ui.text(k, "Sende «${emne.trim()}» til ${m.navn}${if (Lager.test(k)) " i testmiljøet" else ""}?", bold = true))
                    addView(ui.button(k, "Send", primary = true) { if (!opptatt) send() })
                    addView(ui.button(k, "Avbryt") {
                        bekreft = false
                        vis()
                    })
                })
            } else {
                addView(ui.button(k, "Send", primary = true) {
                    if (opptatt) return@button
                    if (mangler != null) {
                        status = mangler
                        vis()
                    } else if (Lager.bryter(k, "bekreft", true)) {
                        bekreft = true
                        vis()
                    } else send()
                })
            }
            (if (opptatt) "Venter på Digipost." else status)?.let { addView(ui.text(k, it, muted = true)) }
            addView(ui.button(k, "Sendte brev") { startActivity(Intent(k, SendtActivity::class.java)) })
        }
        ui.screen(this, innhold)
        Deler.tastatur(this)
    }
}
