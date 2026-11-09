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
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class Oppsett(val avsender: String, val sertifikat: ByteArray, val passord: String, val adresse: String)

class Mottaker(val navn: String, val adresse: String, val detalj: String)

class Feil(melding: String) : Exception(melding)

object Digipost {
    private const val TYPE = "application/vnd.digipost-v8+xml"
    private const val NS = "http://api.digipost.no/schema/v8"
    private val dato = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH).withZone(ZoneOffset.UTC)

    private fun nokkel(o: Oppsett): PrivateKey {
        val lager = KeyStore.getInstance("PKCS12")
        lager.load(o.sertifikat.inputStream(), o.passord.toCharArray())
        val alias = lager.aliases().toList().first { lager.isKeyEntry(it) }
        return lager.getKey(alias, o.passord.toCharArray()) as PrivateKey
    }

    private fun kall(o: Oppsett, metode: String, url: URL, type: String? = null, kropp: ByteArray? = null): Element {
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
        tilkobling.setRequestProperty("Accept", TYPE)
        if (kropp != null) {
            tilkobling.doOutput = true
            tilkobling.setRequestProperty("Content-Type", type)
            tilkobling.setRequestProperty("MIME-Version", "1.0")
            tilkobling.outputStream.use { it.write(kropp) }
        }
        val kode = tilkobling.responseCode
        val svar = (if (kode < 400) tilkobling.inputStream else tilkobling.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        val rot = runCatching { DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(svar.inputStream()).documentElement }.getOrNull()
        if (kode >= 400 || rot == null) throw Feil(rot?.let { tekst(it, "error-message") } ?: "Digipost svarte $kode.")
        return rot
    }

    private fun tekst(e: Element, navn: String): String? =
        e.getElementsByTagNameNS("*", navn).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun barn(e: Element, navn: String): List<Element> {
        val liste = e.getElementsByTagNameNS("*", navn)
        return (0 until liste.length).map { liste.item(it) as Element }
    }

    private fun lenke(o: Oppsett, rel: String): URL {
        val inngang = kall(o, "GET", URL(o.adresse.trimEnd('/') + "/"))
        val treff = barn(inngang, "link").firstOrNull { it.getAttribute("rel").endsWith("/$rel") } ?: throw Feil("Fant ikke $rel hos Digipost.")
        return URL(treff.getAttribute("uri"))
    }

    private fun xml(verdi: String) =
        verdi.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun mottakerXml(mottaker: String) =
        if (mottaker.matches(Regex("\\d{11}"))) "<personal-identification-number>$mottaker</personal-identification-number>"
        else "<digipost-address>${xml(mottaker)}</digipost-address>"

    fun sok(o: Oppsett, tekst: String): List<Mottaker> {
        val url = URL(lenke(o, "search").toString().trimEnd('/') + "/" + URLEncoder.encode(tekst, "UTF-8").replace("+", "%20"))
        return barn(kall(o, "GET", url), "recipient").map { r ->
            val navn = listOfNotNull(tekst(r, "firstname"), tekst(r, "middlename"), tekst(r, "lastname")).joinToString(" ").ifEmpty { tekst(r, "organisation-name") ?: "" }
            val sted = barn(r, "address").firstOrNull()?.let { a -> listOfNotNull(tekst(a, "zip-code"), tekst(a, "city")).joinToString(" ") } ?: ""
            val adresse = tekst(r, "digipost-address") ?: ""
            Mottaker(navn.ifEmpty { adresse }, adresse, listOf(adresse, sted).filter { it.isNotEmpty() }.joinToString(", "))
        }
    }

    fun identifiser(o: Oppsett, fodselsnummer: String): String {
        val kropp = """<?xml version="1.0" encoding="UTF-8"?><identification xmlns="$NS">${mottakerXml(fodselsnummer)}</identification>"""
        return tekst(kall(o, "POST", lenke(o, "identify_recipient"), TYPE, kropp.toByteArray()), "result") ?: ""
    }

    fun send(o: Oppsett, mottaker: String, emne: String, pdf: ByteArray) {
        val dokument = UUID.randomUUID().toString()
        val melding = """<?xml version="1.0" encoding="UTF-8"?><message xmlns="$NS"><message-id>${UUID.randomUUID()}</message-id><recipient>${mottakerXml(mottaker)}</recipient><primary-document><uuid>$dokument</uuid><subject>${xml(emne)}</subject><file-type>pdf</file-type><authentication-level>PASSWORD</authentication-level><sensitivity-level>NORMAL</sensitivity-level></primary-document></message>"""
        val grense = UUID.randomUUID().toString().replace("-", "")
        val kropp = ByteArrayOutputStream().apply {
            write("--$grense\r\nContent-Type: $TYPE; charset=UTF-8\r\nContent-Disposition: attachment; filename=\"message\"\r\n\r\n$melding\r\n".toByteArray())
            write("--$grense\r\nContent-Type: application/pdf\r\nContent-Disposition: attachment; filename=\"$dokument\"\r\n\r\n".toByteArray())
            write(pdf)
            write("\r\n--$grense--\r\n".toByteArray())
        }.toByteArray()
        kall(o, "POST", lenke(o, "create_message"), "multipart/vnd.digipost-v8+xml; boundary=$grense", kropp)
    }
}
