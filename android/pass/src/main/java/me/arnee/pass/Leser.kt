package me.arnee.pass

import android.nfc.tech.IsoDep
import java.io.ByteArrayOutputStream

class Leser(private val kort: IsoDep, private val framdrift: (String) -> Unit) {
    private lateinit var sm: Sm

    private fun send(apdu: ByteArray): ByteArray = kort.transceive(apdu)

    private fun ok(svar: ByteArray): ByteArray {
        val n = svar.size
        if (n < 2 || svar[n - 2] != 0x90.toByte() || svar[n - 1] != 0.toByte()) throw IllegalStateException("Passet svarte ${svar.takeLast(2).toByteArray().hex()}")
        return svar.copyOf(n - 2)
    }

    fun les(dokument: String, fodt: String, utloper: String): Dokument {
        kort.timeout = 10_000
        kort.connect()
        try {
            framdrift("Velger passapplikasjonen")
            ok(send("00A4040C07A0000002471001".unhex()))
            framdrift("Låser opp med MRZ")
            val bac = Bac(dokument, fodt, utloper)
            val rndIc = ok(send("0084000008".unhex()))
            val (kommando, rndIfd, kIfd) = bac.utfordring(rndIc)
            val svar = runCatching { ok(send("0082000028".unhex() + kommando + byteArrayOf(0x28))) }
                .getOrElse { throw IllegalStateException("Passet godtok ikke nøkkelen. Sjekk passnummer og datoer.") }
            sm = bac.okt(svar, rndIc, rndIfd, kIfd)
            framdrift("Leser persondata")
            val dg1 = lesFil(0x0101)
            framdrift("Leser signaturen")
            val sod = lesFil(0x011D)
            return Dokument(dg1, sod)
        } finally {
            runCatching { kort.close() }
        }
    }

    private fun velg(fil: Int) {
        sm.pakkUt(send(sm.beskytt(0x00, 0xA4, 0x02, 0x0C, byteArrayOf((fil shr 8).toByte(), fil.toByte()), null)))
    }

    private fun lesBinar(offset: Int, n: Int): ByteArray =
        sm.pakkUt(send(sm.beskytt(0x00, 0xB0, (offset shr 8) and 0x7F, offset and 0xFF, null, n)))

    private fun lesFil(fil: Int): ByteArray {
        velg(fil)
        val start = lesBinar(0, 6)
        var i = if (start[0].toInt() and 0x1F == 0x1F) 2 else 1
        val forste = start[i++].toInt() and 0xFF
        val innhold = if (forste and 0x80 == 0) forste else {
            var v = 0
            repeat(forste and 0x7F) { v = (v shl 8) or (start[i++].toInt() and 0xFF) }
            v
        }
        val lengde = i + innhold
        val ut = ByteArrayOutputStream()
        ut.write(start)
        while (ut.size() < lengde) {
            val n = minOf(0xDF, lengde - ut.size())
            ut.write(lesBinar(ut.size(), n))
        }
        return ut.toByteArray().copyOf(lengde)
    }
}
