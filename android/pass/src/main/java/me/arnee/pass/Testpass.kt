package me.arnee.pass

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

object Testpass {
    const val SHA256 = "2.16.840.1.101.3.4.2.1"
    private const val RSA_SHA256 = "1.2.840.113549.1.1.11"
    private const val SIGNERINGSTID = "1.2.840.113549.1.9.5"
    private const val SIGNED_DATA = "1.2.840.113549.1.7.2"
    private const val LDS = "2.23.136.1.1.1"
    private const val INNHOLDSTYPE = "1.2.840.113549.1.9.3"
    private const val MELDINGSHASH = "1.2.840.113549.1.9.4"

    fun felt(s: String, n: Int) = s.uppercase().map { if (it in 'A'..'Z' || it in '0'..'9') it else '<' }.joinToString("").padEnd(n, '<').take(n)

    fun mrz(etternavn: String, fornavn: String, nummer: String, nasjonalitet: String, fodt: String, kjonn: String, utloper: String): String {
        val navn = felt(etternavn.trim().replace(' ', '<') + "<<" + fornavn.trim().replace(' ', '<'), 39)
        val linje1 = "P<" + felt(nasjonalitet, 3) + navn
        val n = felt(nummer, 9)
        val valgfritt = felt("", 14)
        val del = n + Mrz.kontroll(n) + felt(nasjonalitet, 3) + fodt + Mrz.kontroll(fodt) + felt(kjonn, 1) + utloper + Mrz.kontroll(utloper) + valgfritt + Mrz.kontroll(valgfritt)
        val samlet = n + Mrz.kontroll(n) + fodt + Mrz.kontroll(fodt) + utloper + Mrz.kontroll(utloper) + valgfritt + Mrz.kontroll(valgfritt)
        return linje1 + del + Mrz.kontroll(samlet)
    }

    fun dg1(mrz: String): ByteArray = Asn1.tlv(0x61, Asn1.tlv(0x5F1F, mrz.toByteArray(Charsets.US_ASCII)))

    fun sod(dg1: ByteArray, sertifikat: ByteArray, nokkel: PrivateKey): ByteArray {
        val sha = { d: ByteArray -> MessageDigest.getInstance("SHA-256").digest(d) }
        val lds = Asn1.tlv(
            0x30,
            Asn1.heltall(0),
            Asn1.tlv(0x30, Asn1.tlv(0x06, Asn1.oidBytes(SHA256))),
            Asn1.tlv(
                0x30,
                Asn1.tlv(0x30, Asn1.heltall(1), Asn1.tlv(0x04, sha(dg1))),
                Asn1.tlv(0x30, Asn1.heltall(2), Asn1.tlv(0x04, ByteArray(32) { ((it % 8) + 1).toByte() })),
            ),
        )
        val attributter = Asn1.tlv(
            0x31,
            Asn1.tlv(0x30, Asn1.tlv(0x06, Asn1.oidBytes(INNHOLDSTYPE)), Asn1.tlv(0x31, Asn1.tlv(0x06, Asn1.oidBytes(LDS)))),
            Asn1.tlv(0x30, Asn1.tlv(0x06, Asn1.oidBytes(SIGNERINGSTID)), Asn1.tlv(0x31, Asn1.tlv(0x17, utcNa()))),
            Asn1.tlv(0x30, Asn1.tlv(0x06, Asn1.oidBytes(MELDINGSHASH)), Asn1.tlv(0x31, Asn1.tlv(0x04, sha(lds)))),
        )
        val signatur = Signature.getInstance("SHA256withRSA").apply {
            initSign(nokkel)
            update(attributter)
        }.sign()
        val sert = Asn1.en(sertifikat)
        val tbs = sert.barn[0]
        val utsteder = tbs.barn[if (tbs.barn[0].tag == 0xA0) 3 else 2].hele
        val serie = tbs.barn[if (tbs.barn[0].tag == 0xA0) 1 else 0].hele
        val signer = Asn1.tlv(
            0x30,
            Asn1.heltall(1),
            Asn1.tlv(0x30, utsteder, serie),
            Asn1.tlv(0x30, Asn1.tlv(0x06, Asn1.oidBytes(SHA256))),
            byteArrayOf(0xA0.toByte()) + attributter.copyOfRange(1, attributter.size),
            Asn1.tlv(0x30, Asn1.tlv(0x06, Asn1.oidBytes(RSA_SHA256)), byteArrayOf(0x05, 0x00)),
            Asn1.tlv(0x04, signatur),
        )
        val signedData = Asn1.tlv(
            0x30,
            Asn1.heltall(3),
            Asn1.tlv(0x31, Asn1.tlv(0x30, Asn1.tlv(0x06, Asn1.oidBytes(SHA256)))),
            Asn1.tlv(0x30, Asn1.tlv(0x06, Asn1.oidBytes(LDS)), Asn1.tlv(0xA0, Asn1.tlv(0x04, lds))),
            Asn1.tlv(0xA0, sertifikat),
            Asn1.tlv(0x31, signer),
        )
        return Asn1.tlv(0x77, Asn1.tlv(0x30, Asn1.tlv(0x06, Asn1.oidBytes(SIGNED_DATA)), Asn1.tlv(0xA0, signedData)))
    }

    fun nokkel(der: ByteArray): PrivateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))

    private fun utcNa(): ByteArray {
        val f = java.text.SimpleDateFormat("yyMMddHHmmss'Z'", java.util.Locale.ROOT).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        return f.format(java.util.Date()).toByteArray(Charsets.US_ASCII)
    }

    fun mrzZkr(etternavn: String, fornavn: String, nummer: String, nasjonalitet: String, fodt: String, kjonn: String, utloper: String): String {
        val navn = felt(etternavn.trim().replace(' ', '<') + "<<" + fornavn.trim().replace(' ', '<'), 39)
        return "P<ZKR" + navn + felt(nummer, 9) + "_" + felt(nasjonalitet, 3) + fodt + "_" + felt(kjonn, 1) + utloper + "_" + "<".repeat(16)
    }

    fun lag(etternavn: String, fornavn: String, nummer: String, nasjonalitet: String, fodt: String, kjonn: String, utloper: String, sertifikat: ByteArray, nokkel: PrivateKey): Dokument {
        val d = dg1(mrzZkr(etternavn, fornavn, nummer, nasjonalitet, fodt, kjonn, utloper))
        return Dokument(d, sod(d, sertifikat, nokkel), test = true)
    }
}
