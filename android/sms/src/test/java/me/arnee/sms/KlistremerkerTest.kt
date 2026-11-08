package me.arnee.sms

import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class KlistremerkerTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test
    fun hkdfRfc5869Test3() {
        val okm = Klistremerker.hkdf(ByteArray(22) { 0x0b }, ByteArray(0), 42)
        assertArrayEquals(hex("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"), okm)
    }

    @Test
    fun dekrypterRundtur() {
        val (aes, mac) = Klistremerker.nokler(ByteArray(32) { it.toByte() })
        val iv = ByteArray(16) { 7 }
        val klartekst = "manifest".toByteArray()
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(aes, "AES"), IvParameterSpec(iv)) }
        val innhold = iv + c.doFinal(klartekst)
        val kode = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(mac, "HmacSHA256")) }.doFinal(innhold)
        assertArrayEquals(klartekst, Klistremerker.dekrypter(innhold + kode, aes, mac))
    }

    @Test
    fun manifest() {
        val sticker = { id: Int -> byteArrayOf(0x08, id.toByte(), 0x12, 0x02, 0x41, 0x42) }
        val tittel = "Pingviner".toByteArray()
        val data = byteArrayOf(0x0A, tittel.size.toByte()) + tittel +
            byteArrayOf(0x22, 6) + sticker(0) + byteArrayOf(0x22, 6) + sticker(3)
        val (t, ider) = Klistremerker.manifest(data)
        assertEquals("Pingviner", t)
        assertEquals(listOf(0, 3), ider)
    }

    @Test
    fun lenke() {
        val id = "9acc9e8aba563d26a4994e69263e3b25"
        val key = "5a6dff3948c28efb9b7aaf93ecc375c69fc316e78077ed26867a14d10a0f6a12"
        assertEquals(id to key, Klistremerker.lenke("https://signal.art/addstickers/#pack_id=$id&pack_key=$key"))
    }
}
