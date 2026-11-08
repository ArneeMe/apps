package me.arnee.pass

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

fun ByteArray.hex() = joinToString("") { "%02X".format(it) }
fun String.unhex() = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

object Krypto {
    fun sha1(d: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(d)

    fun nokkel(seed: ByteArray, teller: Int): ByteArray {
        val h = sha1(seed + byteArrayOf(0, 0, 0, teller.toByte()))
        return paritet(h.copyOf(16))
    }

    private fun paritet(k: ByteArray) = ByteArray(k.size) { i ->
        val b = k[i].toInt() and 0xFE
        (b or (if (Integer.bitCount(b) % 2 == 0) 1 else 0)).toByte()
    }

    private fun tdesNokkel(k: ByteArray) = SecretKeySpec(k + k.copyOf(8), "DESede")

    fun krypter(k: ByteArray, data: ByteArray): ByteArray =
        Cipher.getInstance("DESede/CBC/NoPadding").apply { init(Cipher.ENCRYPT_MODE, tdesNokkel(k), IvParameterSpec(ByteArray(8))) }.doFinal(data)

    fun dekrypter(k: ByteArray, data: ByteArray): ByteArray =
        Cipher.getInstance("DESede/CBC/NoPadding").apply { init(Cipher.DECRYPT_MODE, tdesNokkel(k), IvParameterSpec(ByteArray(8))) }.doFinal(data)

    fun pad(d: ByteArray): ByteArray {
        val ut = ByteArrayOutputStream()
        ut.write(d)
        ut.write(0x80)
        while (ut.size() % 8 != 0) ut.write(0)
        return ut.toByteArray()
    }

    fun fjernPad(d: ByteArray): ByteArray {
        var i = d.size - 1
        while (i >= 0 && d[i] == 0.toByte()) i--
        return d.copyOf(i)
    }

    fun mac(k: ByteArray, data: ByteArray): ByteArray {
        val p = pad(data)
        val ka = SecretKeySpec(k.copyOf(8), "DES")
        val kb = SecretKeySpec(k.copyOfRange(8, 16), "DES")
        val des = Cipher.getInstance("DES/CBC/NoPadding").apply { init(Cipher.ENCRYPT_MODE, ka, IvParameterSpec(ByteArray(8))) }
        val h = des.doFinal(p).copyOfRange(p.size - 8, p.size)
        val d = Cipher.getInstance("DES/ECB/NoPadding").apply { init(Cipher.DECRYPT_MODE, kb) }.doFinal(h)
        return Cipher.getInstance("DES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, ka) }.doFinal(d)
    }
}

object Mrz {
    private val vekt = intArrayOf(7, 3, 1)

    fun kontroll(s: String): Char {
        var sum = 0
        s.forEachIndexed { i, c ->
            val v = when (c) {
                in '0'..'9' -> c - '0'
                in 'A'..'Z' -> c - 'A' + 10
                else -> 0
            }
            sum += v * vekt[i % 3]
        }
        return '0' + sum % 10
    }

    fun nokkelInfo(dokument: String, fodt: String, utloper: String): String {
        val d = dokument.uppercase().padEnd(9, '<')
        return d + kontroll(d) + fodt + kontroll(fodt) + utloper + kontroll(utloper)
    }
}

class Bac(dokument: String, fodt: String, utloper: String) {
    private val seed = Krypto.sha1(Mrz.nokkelInfo(dokument, fodt, utloper).toByteArray()).copyOf(16)
    val kEnc = Krypto.nokkel(seed, 1)
    val kMac = Krypto.nokkel(seed, 2)

    fun utfordring(rndIc: ByteArray, rndIfd: ByteArray = tilfeldig(8), kIfd: ByteArray = tilfeldig(16)): Triple<ByteArray, ByteArray, ByteArray> {
        val s = rndIfd + rndIc + kIfd
        val e = Krypto.krypter(kEnc, s)
        return Triple(e + Krypto.mac(kMac, e), rndIfd, kIfd)
    }

    fun okt(svar: ByteArray, rndIc: ByteArray, rndIfd: ByteArray, kIfd: ByteArray): Sm {
        val e = svar.copyOf(32)
        val m = svar.copyOfRange(32, 40)
        if (!Krypto.mac(kMac, e).contentEquals(m)) throw SecurityException("Feil MAC fra passet")
        val r = Krypto.dekrypter(kEnc, e)
        if (!r.copyOfRange(0, 8).contentEquals(rndIc) || !r.copyOfRange(8, 16).contentEquals(rndIfd)) throw SecurityException("Passet svarte feil")
        val kIc = r.copyOfRange(16, 32)
        val ny = ByteArray(16) { (kIfd[it].toInt() xor kIc[it].toInt()).toByte() }
        val ssc = rndIc.copyOfRange(4, 8) + rndIfd.copyOfRange(4, 8)
        return Sm(Krypto.nokkel(ny, 1), Krypto.nokkel(ny, 2), ssc)
    }

    companion object {
        fun tilfeldig(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }
    }
}

class Sm(val ksEnc: ByteArray, val ksMac: ByteArray, var ssc: ByteArray) {
    private fun okSsc() {
        for (i in ssc.indices.reversed()) {
            val v = (ssc[i].toInt() and 0xFF) + 1
            ssc[i] = v.toByte()
            if (v <= 0xFF) break
        }
    }

    fun beskytt(cla: Int, ins: Int, p1: Int, p2: Int, data: ByteArray?, le: Int?): ByteArray {
        okSsc()
        val hode = byteArrayOf((cla or 0x0C).toByte(), ins.toByte(), p1.toByte(), p2.toByte())
        val do87 = data?.let { Asn1.tlv(0x87, byteArrayOf(1), Krypto.krypter(ksEnc, Krypto.pad(it))) } ?: ByteArray(0)
        val do97 = le?.let { Asn1.tlv(0x97, byteArrayOf(it.toByte())) } ?: ByteArray(0)
        val m = Krypto.mac(ksMac, ssc + Krypto.pad(hode) + do87 + do97)
        val do8e = Asn1.tlv(0x8E, m)
        val kropp = do87 + do97 + do8e
        return hode + byteArrayOf(kropp.size.toByte()) + kropp + byteArrayOf(0)
    }

    fun pakkUt(svar: ByteArray): ByteArray {
        okSsc()
        val sw = svar.copyOfRange(svar.size - 2, svar.size)
        val felt = Asn1.les(svar, 0, svar.size - 2)
        val do87 = felt.firstOrNull { it.tag == 0x87 }
        val do99 = felt.firstOrNull { it.tag == 0x99 }
        val do8e = felt.firstOrNull { it.tag == 0x8E } ?: throw SecurityException("Svar uten MAC, status ${sw.hex()}")
        val m = Krypto.mac(ksMac, ssc + (do87?.hele ?: ByteArray(0)) + (do99?.hele ?: ByteArray(0)))
        if (!m.contentEquals(do8e.verdi)) throw SecurityException("Feil MAC i svar")
        val status = do99?.verdi ?: sw
        if (!(status[0] == 0x90.toByte() && status[1] == 0.toByte())) throw IllegalStateException("Passet svarte ${status.hex()}")
        return do87?.let { Krypto.fjernPad(Krypto.dekrypter(ksEnc, it.verdi.copyOfRange(1, it.verdi.size))) } ?: ByteArray(0)
    }
}
