package me.arnee.pass

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Aes {
    fun ecb(k: ByteArray, d: ByteArray): ByteArray =
        Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES")) }.doFinal(d)

    fun cbc(k: ByteArray, iv: ByteArray, d: ByteArray, krypter: Boolean): ByteArray =
        Cipher.getInstance("AES/CBC/NoPadding").apply { init(if (krypter) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), IvParameterSpec(iv)) }.doFinal(d)

    fun pad(d: ByteArray): ByteArray {
        val ut = ByteArrayOutputStream()
        ut.write(d)
        ut.write(0x80)
        while (ut.size() % 16 != 0) ut.write(0)
        return ut.toByteArray()
    }

    private fun dobbel(b: ByteArray): ByteArray {
        val ut = ByteArray(16)
        var mente = 0
        for (i in 15 downTo 0) {
            val v = ((b[i].toInt() and 0xFF) shl 1) or mente
            ut[i] = v.toByte()
            mente = (b[i].toInt() and 0x80) ushr 7
        }
        if (b[0].toInt() and 0x80 != 0) ut[15] = (ut[15].toInt() xor 0x87).toByte()
        return ut
    }

    fun cmac(k: ByteArray, m: ByteArray): ByteArray {
        val l = ecb(k, ByteArray(16))
        val k1 = dobbel(l)
        val k2 = dobbel(k1)
        val hel = m.isNotEmpty() && m.size % 16 == 0
        val blokker = if (m.isEmpty()) 1 else (m.size + 15) / 16
        val siste = if (hel) {
            ByteArray(16) { (m[m.size - 16 + it].toInt() xor k1[it].toInt()).toByte() }
        } else {
            val rest = m.copyOfRange((blokker - 1) * 16, m.size)
            val p = ByteArray(16).also { rest.copyInto(it); it[rest.size] = 0x80.toByte() }
            ByteArray(16) { (p[it].toInt() xor k2[it].toInt()).toByte() }
        }
        var x = ByteArray(16)
        for (i in 0 until blokker - 1) {
            x = ecb(k, ByteArray(16) { (x[it].toInt() xor m[i * 16 + it].toInt()).toByte() })
        }
        return ecb(k, ByteArray(16) { (x[it].toInt() xor siste[it].toInt()).toByte() })
    }

    fun kdf(hemmelig: ByteArray, teller: Int, lengde: Int): ByteArray {
        val alg = if (lengde <= 16) "SHA-1" else "SHA-256"
        return MessageDigest.getInstance(alg).digest(hemmelig + byteArrayOf(0, 0, 0, teller.toByte())).copyOf(lengde)
    }
}

class SmAes(val ksEnc: ByteArray, val ksMac: ByteArray) : Kanal {
    var ssc = ByteArray(16)

    private fun okSsc() {
        for (i in 15 downTo 0) {
            val v = (ssc[i].toInt() and 0xFF) + 1
            ssc[i] = v.toByte()
            if (v <= 0xFF) break
        }
    }

    private fun iv() = Aes.ecb(ksEnc, ssc)

    override fun beskytt(cla: Int, ins: Int, p1: Int, p2: Int, data: ByteArray?, le: Int?): ByteArray {
        okSsc()
        val hode = byteArrayOf((cla or 0x0C).toByte(), ins.toByte(), p1.toByte(), p2.toByte())
        val do87 = data?.let { Asn1.tlv(0x87, byteArrayOf(1), Aes.cbc(ksEnc, iv(), Aes.pad(it), true)) } ?: ByteArray(0)
        val do97 = le?.let { Asn1.tlv(0x97, byteArrayOf(it.toByte())) } ?: ByteArray(0)
        val m = Aes.cmac(ksMac, Aes.pad(ssc + Aes.pad(hode) + do87 + do97)).copyOf(8)
        val kropp = do87 + do97 + Asn1.tlv(0x8E, m)
        return hode + byteArrayOf(kropp.size.toByte()) + kropp + byteArrayOf(0)
    }

    override fun pakkUt(svar: ByteArray): ByteArray {
        okSsc()
        val sw = svar.copyOfRange(svar.size - 2, svar.size)
        val felt = Asn1.les(svar, 0, svar.size - 2)
        val do87 = felt.firstOrNull { it.tag == 0x87 }
        val do99 = felt.firstOrNull { it.tag == 0x99 }
        val do8e = felt.firstOrNull { it.tag == 0x8E } ?: throw SecurityException("Svar uten MAC, status ${sw.hex()}")
        val m = Aes.cmac(ksMac, Aes.pad(ssc + (do87?.hele ?: ByteArray(0)) + (do99?.hele ?: ByteArray(0)))).copyOf(8)
        if (!m.contentEquals(do8e.verdi)) throw SecurityException("Feil MAC i svar")
        val status = do99?.verdi ?: sw
        if (!(status[0] == 0x90.toByte() && status[1] == 0.toByte())) throw IllegalStateException("Passet svarte ${status.hex()}")
        return do87?.let { Krypto.fjernPad(Aes.cbc(ksEnc, iv(), it.verdi.copyOfRange(1, it.verdi.size), false)) } ?: ByteArray(0)
    }
}

class PaceInfo(val oid: String, val kurve: Kurve, val nokkellengde: Int)

object Pace {
    private val oider = mapOf(
        "0.4.0.127.0.7.2.2.4.2.2" to 16,
        "0.4.0.127.0.7.2.2.4.2.3" to 24,
        "0.4.0.127.0.7.2.2.4.2.4" to 32,
    )

    fun velg(cardAccess: ByteArray): PaceInfo? {
        val infos = Asn1.en(cardAccess).barn
        for (info in infos) {
            if (info.tag != 0x30 || info.barn.isEmpty() || info.barn[0].tag != 0x06) continue
            val oid = Asn1.oid(info.barn[0].verdi)
            val lengde = oider[oid] ?: continue
            val param = info.barn.getOrNull(2)?.verdi?.let { BigInteger(1, it).toInt() } ?: continue
            val kurve = runCatching { Kurve.fraParameter(param) }.getOrNull() ?: continue
            return PaceInfo(oid, kurve, lengde)
        }
        return null
    }

    fun passordnokkel(mrzInfo: String, lengde: Int): ByteArray =
        Aes.kdf(MessageDigest.getInstance("SHA-1").digest(mrzInfo.toByteArray()), 3, lengde)

    fun avbildning(k: Kurve, s: BigInteger, h: Punkt): Punkt = k.pluss(k.gange(s, k.g), h)

    fun nokkelobjekt(oid: String, k: Kurve, q: Punkt): ByteArray =
        Asn1.tlv(0x7F49, Asn1.tlv(0x06, Asn1.oidBytes(oid)), Asn1.tlv(0x86, k.kode(q)))

    fun kjor(send: (ByteArray) -> ByteArray, info: PaceInfo, mrzInfo: String): SmAes {
        val k = info.kurve
        val kPi = passordnokkel(mrzInfo, info.nokkellengde)
        val ok = { r: ByteArray ->
            if (r.size < 2 || r[r.size - 2] != 0x90.toByte() || r[r.size - 1] != 0.toByte()) throw IllegalStateException("PACE feilet med ${r.takeLast(2).toByteArray().hex()}")
            r.copyOf(r.size - 2)
        }
        val ga = { cla: Int, data: ByteArray ->
            val kropp = Asn1.tlv(0x7C, data)
            ok(send(byteArrayOf(cla.toByte(), 0x86.toByte(), 0, 0, kropp.size.toByte()) + kropp + byteArrayOf(0)))
        }
        val param = if (k === Kurve.P256) 12 else 13
        val mse = Asn1.tlv(0x80, Asn1.oidBytes(info.oid)) + Asn1.tlv(0x83, byteArrayOf(1)) + Asn1.tlv(0x84, byteArrayOf(param.toByte()))
        ok(send(byteArrayOf(0, 0x22, 0xC1.toByte(), 0xA4.toByte(), mse.size.toByte()) + mse))

        val z = Asn1.en(ga(0x10, ByteArray(0))).finn(0x80)!!.verdi
        val s = BigInteger(1, Aes.cbc(kPi, ByteArray(16), z, false))

        val skMap = k.hemmelig()
        val pkMapIc = k.punkt(Asn1.en(ga(0x10, Asn1.tlv(0x81, k.kode(k.gange(skMap, k.g))))).finn(0x82)!!.verdi)
        val h = k.gange(skMap, pkMapIc)
        val g2 = avbildning(k, s, h)

        val skEph = k.hemmelig()
        val pkEph = k.gange(skEph, g2)
        val pkEphIc = k.punkt(Asn1.en(ga(0x10, Asn1.tlv(0x83, k.kode(pkEph)))).finn(0x84)!!.verdi)
        if (pkEphIc == pkEph) throw SecurityException("Passet sendte samme nøkkel tilbake")
        val delt = k.tall(k.gange(skEph, pkEphIc).x!!)

        val ksEnc = Aes.kdf(delt, 1, info.nokkellengde)
        val ksMac = Aes.kdf(delt, 2, info.nokkellengde)
        val tPcd = Aes.cmac(ksMac, nokkelobjekt(info.oid, k, pkEphIc)).copyOf(8)
        val tIc = Asn1.en(ga(0x00, Asn1.tlv(0x85, tPcd))).finn(0x86)!!.verdi
        if (!tIc.contentEquals(Aes.cmac(ksMac, nokkelobjekt(info.oid, k, pkEph)).copyOf(8))) throw SecurityException("Passet ga feil bekreftelse")
        return SmAes(ksEnc, ksMac)
    }
}
