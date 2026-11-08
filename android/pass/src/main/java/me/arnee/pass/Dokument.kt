package me.arnee.pass

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec

class Person(
    val type: String,
    val utsteder: String,
    val etternavn: String,
    val fornavn: String,
    val nummer: String,
    val nasjonalitet: String,
    val fodt: String,
    val kjonn: String,
    val utloper: String,
    val mrz: String,
)

class Sjekk(val dataUendret: Boolean, val signaturGyldig: Boolean, val utsteder: String, val algoritme: String, val feil: String?)

class Dokument(val dg1: ByteArray, val sod: ByteArray, val test: Boolean = false) {

    val person: Person by lazy { person(dg1) }

    companion object {
        private val hashOid = mapOf(
            "1.3.14.3.2.26" to "SHA-1",
            "2.16.840.1.101.3.4.2.4" to "SHA-224",
            "2.16.840.1.101.3.4.2.1" to "SHA-256",
            "2.16.840.1.101.3.4.2.2" to "SHA-384",
            "2.16.840.1.101.3.4.2.3" to "SHA-512",
        )

        fun mrz(dg1: ByteArray): String = Asn1.en(dg1).finn(0x5F1F)?.verdi?.let { String(it, Charsets.US_ASCII) } ?: ""

        fun person(dg1: ByteArray): Person {
            val m = mrz(dg1)
            return if (m.length >= 88) {
                val l1 = m.substring(0, 44)
                val l2 = m.substring(44, 88)
                val navn = l1.substring(5).trimEnd('<').split("<<", limit = 2)
                Person(
                    l1.substring(0, 2).trimEnd('<'), l1.substring(2, 5).trimEnd('<'),
                    navn[0].replace('<', ' ').trim(), navn.getOrElse(1) { "" }.replace('<', ' ').trim(),
                    l2.substring(0, 9).trimEnd('<'), l2.substring(10, 13).trimEnd('<'), l2.substring(13, 19),
                    l2.substring(20, 21), l2.substring(21, 27), m,
                )
            } else if (m.length >= 90) {
                val l1 = m.substring(0, 30)
                val l2 = m.substring(30, 60)
                val l3 = m.substring(60, 90)
                val navn = l3.trimEnd('<').split("<<", limit = 2)
                Person(
                    l1.substring(0, 2).trimEnd('<'), l1.substring(2, 5).trimEnd('<'),
                    navn[0].replace('<', ' ').trim(), navn.getOrElse(1) { "" }.replace('<', ' ').trim(),
                    l1.substring(5, 14).trimEnd('<'), l2.substring(15, 18).trimEnd('<'), l2.substring(0, 6),
                    l2.substring(7, 8), l2.substring(8, 14), m,
                )
            } else {
                Person("?", "?", "?", "?", "?", "?", "000000", "?", "000000", m)
            }
        }
    }

    private val signert: Tlv by lazy {
        val innhold = Asn1.en(sod).let { if (it.tag == 0x77) Asn1.en(it.verdi) else it }
        innhold.finn(0xA0)!!.barn.first()
    }

    val eContent: ByteArray by lazy { signert.finn(0x30)!!.finn(0xA0)!!.barn.first().verdi }

    val sertifikat: X509Certificate? by lazy {
        signert.finn(0xA0)?.barn?.firstOrNull()?.let {
            CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(it.hele)) as X509Certificate
        }
    }

    fun hasher(): Pair<String, Map<Int, ByteArray>> {
        val lds = Asn1.en(eContent)
        val algoritme = hashOid[Asn1.oid(lds.barn[1].barn[0].verdi)] ?: "SHA-256"
        val grupper = lds.barn[2].barn.associate { (it.barn[0].verdi.last().toInt() and 0xFF) to it.barn[1].verdi }
        return algoritme to grupper
    }

    fun sjekk(): Sjekk = runCatching {
        val (algoritme, grupper) = hasher()
        val dataUendret = grupper[1]?.contentEquals(MessageDigest.getInstance(algoritme).digest(dg1)) == true
        val signer = signert.alle(0x31).last().barn.first()
        val iAttr = signer.barn.indexOfFirst { it.tag == 0xA0 }
        val attributter = signer.barn[iAttr]
        val sigAlg = signer.barn[iAttr + 1]
        val signatur = signer.barn[iAttr + 2].verdi
        val digestAlg = hashOid[Asn1.oid(signer.barn[iAttr - 1].barn[0].verdi)] ?: algoritme
        val melding = attributter.barn.firstOrNull { Asn1.oid(it.barn[0].verdi) == "1.2.840.113549.1.9.4" }?.barn?.get(1)?.barn?.first()?.verdi
        val innholdOk = melding?.contentEquals(MessageDigest.getInstance(digestAlg).digest(eContent)) ?: false
        val signerteAttributter = byteArrayOf(0x31) + attributter.hele.copyOfRange(1, attributter.hele.size)
        val sert = sertifikat ?: throw IllegalStateException("Mangler sertifikat i SOD")
        val oid = Asn1.oid(sigAlg.barn[0].verdi)
        val navn = signaturNavn(oid, digestAlg)
        val s = Signature.getInstance(navn)
        if (oid == "1.2.840.113549.1.1.10") {
            val salt = sigAlg.barn.getOrNull(1)?.finn(0xA2)?.barn?.firstOrNull()?.verdi?.let { java.math.BigInteger(1, it).toInt() } ?: 32
            s.setParameter(PSSParameterSpec(digestAlg, "MGF1", MGF1ParameterSpec(digestAlg), salt, 1))
        }
        s.initVerify(sert.publicKey)
        s.update(signerteAttributter)
        val gyldig = innholdOk && s.verify(if (navn.contains("ECDSA")) ecdsa(signatur, sert) else signatur)
        Sjekk(dataUendret, gyldig, sert.issuerX500Principal.name, navn, null)
    }.getOrElse { Sjekk(false, false, "", "", "${it.javaClass.simpleName}: ${it.message}") }

    private fun ecdsa(signatur: ByteArray, sert: X509Certificate): ByteArray {
        if (signatur.isNotEmpty() && signatur[0] == 0x30.toByte()) return signatur
        val n = signatur.size / 2
        fun heltall(b: ByteArray): ByteArray {
            var v = b.dropWhile { it == 0.toByte() }.toByteArray()
            if (v.isEmpty()) v = byteArrayOf(0)
            if (v[0].toInt() and 0x80 != 0) v = byteArrayOf(0) + v
            return Asn1.tlv(0x02, v)
        }
        return Asn1.tlv(0x30, heltall(signatur.copyOf(n)), heltall(signatur.copyOfRange(n, signatur.size)))
    }

    private fun signaturNavn(oid: String, digest: String): String {
        val kort = digest.replace("-", "")
        return when (oid) {
            "1.2.840.113549.1.1.1" -> "${kort}withRSA"
            "1.2.840.113549.1.1.5" -> "SHA1withRSA"
            "1.2.840.113549.1.1.11" -> "SHA256withRSA"
            "1.2.840.113549.1.1.12" -> "SHA384withRSA"
            "1.2.840.113549.1.1.13" -> "SHA512withRSA"
            "1.2.840.113549.1.1.10" -> "${kort}withRSA/PSS"
            "1.2.840.10045.4.1" -> "SHA1withECDSA"
            "1.2.840.10045.4.3.2" -> "SHA256withECDSA"
            "1.2.840.10045.4.3.3" -> "SHA384withECDSA"
            "1.2.840.10045.4.3.4" -> "SHA512withECDSA"
            "1.2.840.10045.2.1" -> "${kort}withECDSA"
            else -> throw IllegalArgumentException("Ukjent signaturalgoritme $oid")
        }
    }
}
