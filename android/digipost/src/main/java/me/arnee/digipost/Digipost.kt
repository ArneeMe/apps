package me.arnee.digipost

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class Oppsett(val avsender: String, val sertifikat: ByteArray, val passord: String, val adresse: String, val paVegneAv: String = "")

class Papir(val navn: String, val linje1: String, val linje2: String, val postnr: String, val sted: String) {
    fun tom() = navn.isBlank() || postnr.isBlank() || sted.isBlank()
    fun tekst() = listOf(navn, linje1, linje2, "$postnr $sted".trim()).filter { it.isNotBlank() }.joinToString(", ")
}

class Mottaker(val navn: String, val adresse: String, val detalj: String, val post: Papir? = null, val harDigipost: Boolean = true, val flett: Map<String, String> = emptyMap())

class Dokument(val navn: String, val type: String, val innhold: ByteArray)

sealed class Data
class Faktura(val belop: String, val konto: String, val kid: String, val forfall: OffsetDateTime) : Data()
class Lenke(val url: String, val knapp: String, val beskrivelse: String, val frist: OffsetDateTime?) : Data()
class Deling(val formal: String, val dager: Int) : Data()
class Avtale(val start: OffsetDateTime, val slutt: OffsetDateTime?, val oppmote: String, val sted: String, val gate: String, val postnr: String, val by: String, val undertittel: String, val info: String) : Data()

class Valg(
    val sensitiv: Boolean = false,
    val niva: String = "PASSWORD",
    val smsTimer: Int = 0,
    val smsAlltid: Boolean = false,
    val leveres: OffsetDateTime? = null,
    val papir: Papir? = null,
    val retur: Papir? = null,
    val farger: Boolean = false,
    val makuler: Boolean = false,
    val ulestDager: Int = 0,
    val registrerTelefon: String = "",
    val registrerDager: Int = 0,
    val data: Data? = null,
    val epost: String = "",
    val epostTekst: String = "",
)

class Levering(val dokument: String, val status: String, val kanal: String, val tid: String)

class Status(val status: String, val kanal: String, val levert: String, val lest: String)

class Hendelse(val dokument: String, val type: String, val tid: String)

class Innkommende(val id: String, val emne: String, val avsender: String, val tid: String, val type: String, val innhold: String, val slett: String, val vedlegg: Int)

class Delt(val emne: String, val tid: String, val type: String, val storrelse: Long, val fra: String, val strom: String)

class Delingsstatus(val delt: String, val utloper: String, val trukket: String, val dokumenter: List<Delt>, val stopp: String)

class Feil(melding: String) : Exception(melding)

object Digipost {
    private const val TYPE = "application/vnd.digipost-v8+xml"
    private const val NS = "http://api.digipost.no/schema/v8"
    private const val DATA = "http://api.digipost.no/schema/datatypes"
    private val dato = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH).withZone(ZoneOffset.UTC)
    private val sok = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx")

    private fun nokkel(o: Oppsett): PrivateKey {
        val lager = KeyStore.getInstance("PKCS12")
        lager.load(o.sertifikat.inputStream(), o.passord.toCharArray())
        val alias = lager.aliases().toList().first { lager.isKeyEntry(it) }
        return lager.getKey(alias, o.passord.toCharArray()) as PrivateKey
    }

    private fun hent(o: Oppsett, metode: String, url: URL, type: String? = null, kropp: ByteArray? = null, godta: String = TYPE): Pair<Int, ByteArray> {
        val hoder = sortedMapOf("Date" to dato.format(Instant.now()), "X-Digipost-UserId" to o.avsender)
        if (kropp != null) hoder["X-Content-SHA256"] = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(kropp))
        val tekst = metode + "\n" + url.path.lowercase() + "\n" + hoder.entries.joinToString("") { "${it.key.lowercase()}: ${it.value}\n" } + (url.query ?: "").lowercase() + "\n"
        val signatur = Signature.getInstance("SHA256withRSA").run {
            initSign(nokkel(o))
            update(tekst.toByteArray())
            sign()
        }
        val tilkobling = url.openConnection() as HttpURLConnection
        tilkobling.requestMethod = metode
        tilkobling.connectTimeout = 15_000
        tilkobling.readTimeout = 30_000
        hoder.forEach { (navn, verdi) -> tilkobling.setRequestProperty(navn, verdi) }
        tilkobling.setRequestProperty("X-Digipost-Signature", Base64.getEncoder().encodeToString(signatur))
        tilkobling.setRequestProperty("Accept", godta)
        if (kropp != null) {
            tilkobling.doOutput = true
            tilkobling.setRequestProperty("Content-Type", type)
            tilkobling.setRequestProperty("MIME-Version", "1.0")
            tilkobling.outputStream.use { it.write(kropp) }
        }
        val kode = tilkobling.responseCode
        val svar = (if (kode < 400) tilkobling.inputStream else tilkobling.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        if (kode >= 400) throw Feil(runCatching { tolk(svar) }.getOrNull()?.let { tekst(it, "error-message") } ?: "Digipost svarte $kode.")
        return kode to svar
    }

    private fun tolk(svar: ByteArray): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(svar.inputStream()).documentElement

    private fun kall(o: Oppsett, metode: String, url: URL, type: String? = null, kropp: ByteArray? = null): Element {
        val (kode, svar) = hent(o, metode, url, type, kropp)
        return runCatching { tolk(svar) }.getOrNull() ?: throw Feil("Digipost svarte $kode.")
    }

    private fun tekst(e: Element, navn: String): String? =
        e.getElementsByTagNameNS("*", navn).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun barn(e: Element, navn: String): List<Element> {
        val liste = e.getElementsByTagNameNS("*", navn)
        return (0 until liste.length).map { liste.item(it) as Element }
    }

    private fun direkte(e: Element, navn: String): List<Element> {
        val liste = e.childNodes
        return (0 until liste.length).mapNotNull { liste.item(it) as? Element }.filter { it.localName == navn }
    }

    private fun lenke(o: Oppsett, rel: String, sti: String = "/"): URL {
        val inngang = kall(o, "GET", URL(o.adresse.trimEnd('/') + sti))
        val treff = barn(inngang, "link").firstOrNull { it.getAttribute("rel").endsWith("/$rel") } ?: throw Feil("Fant ikke $rel hos Digipost.")
        return URL(treff.getAttribute("uri"))
    }

    private fun avsenderId(o: Oppsett) = o.paVegneAv.ifEmpty { o.avsender }

    private fun xml(verdi: String) =
        verdi.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun tid(verdi: OffsetDateTime) = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(verdi.withNano(0))

    private fun felt(navn: String, verdi: String) = if (verdi.isBlank()) "" else "<$navn>${xml(verdi.trim())}</$navn>"

    private fun mottakerXml(mottaker: String) = when {
        mottaker.isEmpty() -> ""
        mottaker.matches(Regex("\\d{11}")) -> "<personal-identification-number>$mottaker</personal-identification-number>"
        mottaker.matches(Regex("\\d{9}")) -> "<organisation-number>$mottaker</organisation-number>"
        else -> "<digipost-address>${xml(mottaker)}</digipost-address>"
    }

    private fun postXml(navn: String, p: Papir) =
        "<$navn><name>${xml(p.navn.trim())}</name><norwegian-address>${felt("addressline1", p.linje1)}${felt("addressline2", p.linje2)}<zip-code>${xml(p.postnr.trim())}</zip-code><city>${xml(p.sted.trim())}</city></norwegian-address></$navn>"

    private fun utskriftXml(v: Valg): String {
        val p = v.papir ?: return ""
        val r = v.retur ?: return ""
        return "<print-details>${postXml("recipient", p)}${postXml("return-address", r)}<color>${if (v.farger) "COLORS" else "MONOCHROME"}</color><nondeliverable-handling>${if (v.makuler) "SHRED" else "RETURN_TO_SENDER"}</nondeliverable-handling></print-details>"
    }

    private fun dataXml(d: Data?): String = when (d) {
        null -> ""
        is Faktura -> "<data-type><invoice xmlns=\"$DATA\"><due-date>${tid(d.forfall)}</due-date><sum>${d.belop.replace(',', '.').filter { it.isDigit() || it == '.' }}</sum><creditor-account>${xml(d.konto.filter { it.isDigit() })}</creditor-account>${felt("kid", d.kid)}</invoice></data-type>"
        is Lenke -> "<data-type><externalLink xmlns=\"$DATA\"><url>${xml(d.url.trim())}</url>${d.frist?.let { "<deadline>${tid(it)}</deadline>" } ?: ""}${felt("description", d.beskrivelse)}${felt("button-text", d.knapp)}</externalLink></data-type>"
        is Deling -> "<data-type><share-documents-request xmlns=\"$DATA\"><max-share-duration-seconds>${d.dager.toLong() * 86400}</max-share-duration-seconds><purpose>${xml(d.formal.trim())}</purpose></share-documents-request></data-type>"
        is Avtale -> {
            val adresse = if (d.gate.isBlank() && d.postnr.isBlank() && d.by.isBlank()) "" else "<address>${felt("street-address", d.gate)}${felt("postal-code", d.postnr)}${felt("city", d.by)}</address>"
            val info = if (d.info.isBlank()) "" else "<info><title>Informasjon</title><text>${xml(d.info.trim())}</text></info>"
            "<data-type><appointment xmlns=\"$DATA\"><start-time>${tid(d.start)}</start-time>${d.slutt?.let { "<end-time>${tid(it)}</end-time>" } ?: ""}${felt("arrival-time", d.oppmote)}${felt("place", d.sted)}$adresse${felt("sub-title", d.undertittel)}$info<language>NB</language></appointment></data-type>"
        }
    }

    fun sok(o: Oppsett, tekst: String): List<Mottaker> {
        val url = URL(lenke(o, "search").toString().trimEnd('/') + "/" + URLEncoder.encode(tekst, "UTF-8").replace("+", "%20"))
        return barn(kall(o, "GET", url), "recipient").map { r ->
            val navn = listOfNotNull(tekst(r, "firstname"), tekst(r, "middlename"), tekst(r, "lastname")).joinToString(" ").ifEmpty { tekst(r, "organisation-name") ?: "" }
            val a = barn(r, "address").firstOrNull()
            val post = a?.let {
                val gate = listOfNotNull(tekst(it, "street"), tekst(it, "house-number"), tekst(it, "house-letter")).joinToString(" ")
                Papir(navn, gate, tekst(it, "additional-addressline") ?: "", tekst(it, "zip-code") ?: "", tekst(it, "city") ?: "")
            }
            val adresse = tekst(r, "digipost-address") ?: ""
            val sted = post?.let { "${it.postnr} ${it.sted}".trim() } ?: ""
            Mottaker(navn.ifEmpty { adresse }, adresse, listOf(adresse, sted).filter { it.isNotEmpty() }.joinToString(", "), post)
        }
    }

    fun identifiser(o: Oppsett, nummer: String): String {
        val kropp = """<?xml version="1.0" encoding="UTF-8"?><identification xmlns="$NS">${mottakerXml(nummer)}</identification>"""
        return tekst(kall(o, "POST", lenke(o, "identify_recipient"), TYPE, kropp.toByteArray()), "result") ?: ""
    }

    fun avsender(o: Oppsett): String {
        val svar = kall(o, "GET", URL(lenke(o, "get_sender_information").toString().trimEnd('/') + "/" + avsenderId(o)))
        return tekst(svar, "status") ?: ""
    }

    fun status(o: Oppsett, dokument: String): Status {
        val svar = kall(o, "GET", URL(o.adresse.trimEnd('/') + "/documents/${avsenderId(o)}/$dokument/status"))
        return Status(svar.getAttribute("status"), svar.getAttribute("channel"), svar.getAttribute("delivered"), svar.getAttribute("read"))
    }

    fun hendelser(o: Oppsett, dager: Long): List<Hendelse> {
        val til = OffsetDateTime.now()
        val sporring = "from=" + URLEncoder.encode(sok.format(til.minusDays(dager)), "UTF-8") + "&to=" + URLEncoder.encode(sok.format(til), "UTF-8") +
            "&offset=0&maxResults=1000" + if (o.paVegneAv.isEmpty()) "" else "&sender=${o.paVegneAv}"
        val svar = kall(o, "GET", URL(lenke(o, "document_events").toString() + "?" + sporring))
        return barn(svar, "event").map { Hendelse(it.getAttribute("uuid"), it.getAttribute("type"), it.getAttribute("created")) }
    }

    fun innboks(o: Oppsett): List<Innkommende> {
        val url = URL(lenke(o, "get_inbox", "/" + avsenderId(o)).toString() + "?offset=0&limit=100")
        return direkte(kall(o, "GET", url), "document").map { d ->
            Innkommende(
                tekst(d, "id") ?: "", direkte(d, "subject").firstOrNull()?.textContent?.trim() ?: "", tekst(d, "sender") ?: "",
                direkte(d, "delivery-time").firstOrNull()?.textContent?.trim() ?: "", direkte(d, "content-type").firstOrNull()?.textContent?.trim() ?: "",
                direkte(d, "content-uri").firstOrNull()?.textContent?.trim() ?: "", direkte(d, "delete-uri").firstOrNull()?.textContent?.trim() ?: "",
                direkte(d, "attachment").size,
            )
        }
    }

    fun deling(o: Oppsett, dokument: String): Delingsstatus {
        val svar = kall(o, "GET", URL(lenke(o, "get_share_documents_request_state", "/" + avsenderId(o)).toString() + dokument))
        val dokumenter = barn(svar, "shared-document").map { d ->
            val opphav = barn(d, "organisation").firstOrNull() ?: barn(d, "private-person").firstOrNull()
            val strom = barn(d, "link").firstOrNull { it.getAttribute("rel").endsWith("/get_shared_document_content_stream") }?.getAttribute("uri") ?: ""
            Delt(tekst(d, "subject") ?: "", tekst(d, "delivery-time") ?: "", tekst(d, "file-type") ?: "", tekst(d, "file-size-bytes")?.toLongOrNull() ?: 0, opphav?.getAttribute("name") ?: "", strom)
        }
        val stopp = direkte(svar, "link").firstOrNull { it.getAttribute("rel").endsWith("/stop_sharing") }?.getAttribute("uri") ?: ""
        return Delingsstatus(direkte(svar, "shared-at-time").firstOrNull()?.textContent ?: "", direkte(svar, "expiry-time").firstOrNull()?.textContent ?: "", direkte(svar, "withdrawn-time").firstOrNull()?.textContent ?: "", dokumenter, stopp)
    }

    fun stoppDeling(o: Oppsett, uri: String) {
        val kropp = """<?xml version="1.0" encoding="UTF-8"?><additional-data xmlns="$NS"><data-type><share-documents-request-sharing-stopped xmlns="$DATA"/></data-type><sender-id>${avsenderId(o)}</sender-id></additional-data>"""
        hent(o, "POST", URL(uri), TYPE, kropp.toByteArray())
    }

    fun innhold(o: Oppsett, uri: String): ByteArray = hent(o, "GET", URL(uri), godta = "*/*").second

    fun slett(o: Oppsett, uri: String) {
        hent(o, "DELETE", URL(uri))
    }

    private fun dokumentXml(navn: String, uuid: String, emne: String, d: Dokument, v: Valg, forste: Boolean): String {
        val sms = if (forste && v.smsTimer > 0) "<sms-notification>${if (v.smsAlltid) "<always-send>true</always-send>" else ""}<after-hours>${v.smsTimer}</after-hours></sms-notification>" else ""
        val data = if (forste) dataXml(v.data) else ""
        val epost = if (forste && v.epost.isNotBlank()) "<email-notification><email-address>${xml(v.epost.trim())}</email-address><subject>${xml(emne.take(100))}</subject><text>${xml(v.epostTekst.ifBlank { "Du har fått et nytt brev i Digipost." }.take(500))}</text><at time=\"${tid((v.leveres ?: OffsetDateTime.now()).plusMinutes(5))}\"/></email-notification>" else ""
        return "<$navn><uuid>$uuid</uuid><subject>${xml(emne)}</subject><file-type>${d.type}</file-type>$sms$epost<authentication-level>${v.niva}</authentication-level><sensitivity-level>${if (v.sensitiv) "SENSITIVE" else "NORMAL"}</sensitivity-level>$data</$navn>"
    }

    fun meldingXml(o: Oppsett, mottaker: String, emne: String, dokumenter: List<Dokument>, v: Valg, uuider: List<String>): String {
        val avsender = if (o.paVegneAv.isEmpty()) "" else "<sender-id>${xml(o.paVegneAv)}</sender-id>"
        val leveres = v.leveres?.let { "<delivery-time>${tid(it.withSecond(0))}</delivery-time>" } ?: ""
        val vedlegg = dokumenter.indices.drop(1).joinToString("") { dokumentXml("attachment", uuider[it], dokumenter[it].navn, dokumenter[it], v, false) }
        val utskrift = utskriftXml(v)
        val start = v.leveres ?: OffsetDateTime.now()
        val ulest = if (v.ulestDager > 0 && utskrift.isNotEmpty() && mottaker.isNotEmpty()) "<print-if-unread><print-if-unread-after>${tid(start.plusDays(v.ulestDager.toLong()))}</print-if-unread-after>$utskrift</print-if-unread>" else ""
        val registrering = if (v.registrerTelefon.isNotBlank() && mottaker.matches(Regex("\\d{11}")))
            "<request-for-registration><registration-deadline>${tid(start.plusDays(maxOf(1, v.registrerDager).toLong()))}</registration-deadline><phone-number>${xml(v.registrerTelefon.trim())}</phone-number>$utskrift</request-for-registration>"
        else ""
        val reserve = if (registrering.isEmpty() && ulest.isEmpty()) utskrift else ""
        return """<?xml version="1.0" encoding="UTF-8"?><message xmlns="$NS"><message-id>${UUID.randomUUID()}</message-id>$avsender<recipient>${mottakerXml(mottaker)}$reserve</recipient>$leveres${dokumentXml("primary-document", uuider[0], emne, dokumenter[0], v, true)}$vedlegg$ulest$registrering</message>"""
    }

    fun send(o: Oppsett, mottaker: String, emne: String, dokumenter: List<Dokument>, v: Valg): Levering {
        val uuider = dokumenter.map { UUID.randomUUID().toString() }
        val melding = meldingXml(o, mottaker, emne, dokumenter, v, uuider)
        val grense = UUID.randomUUID().toString().replace("-", "")
        val kropp = ByteArrayOutputStream().apply {
            write("--$grense\r\nContent-Type: $TYPE; charset=UTF-8\r\nContent-Disposition: attachment; filename=\"message\"\r\n\r\n$melding\r\n".toByteArray())
            dokumenter.forEachIndexed { i, d ->
                write("--$grense\r\nContent-Type: application/${d.type}\r\nContent-Disposition: attachment; filename=\"${uuider[i]}\"\r\n\r\n".toByteArray())
                write(d.innhold)
                write("\r\n".toByteArray())
            }
            write("--$grense--\r\n".toByteArray())
        }.toByteArray()
        val svar = kall(o, "POST", lenke(o, "create_message"), "multipart/vnd.digipost-v8+xml; boundary=$grense", kropp)
        return Levering(uuider[0], tekst(svar, "status") ?: "", tekst(svar, "delivery-method") ?: "", tekst(svar, "delivery-time") ?: "")
    }
}
