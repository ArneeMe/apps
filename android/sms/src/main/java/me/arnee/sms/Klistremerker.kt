package me.arnee.sms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Merkepakke(val id: String, val tittel: String, val filer: List<File>)

object Klistremerker {
    private fun mappe(context: Context) = File(context.filesDir, "klistremerker").apply { mkdirs() }

    fun pakker(context: Context): List<Merkepakke> =
        mappe(context).listFiles()?.filter { it.isDirectory }?.sortedBy { it.lastModified() }?.map { m ->
            val tittel = File(m, "tittel.txt").takeIf { it.exists() }?.readText() ?: m.name.take(8)
            Merkepakke(m.name, tittel, m.listFiles { f -> f.name.endsWith(".webp") }?.sortedBy { it.nameWithoutExtension.toIntOrNull() ?: 0 } ?: emptyList())
        } ?: emptyList()

    fun slett(context: Context, id: String) = File(mappe(context), id).deleteRecursively()

    fun lenke(tekst: String): Pair<String, String>? {
        val id = Regex("pack_id=([0-9a-fA-F]{32})").find(tekst)?.groupValues?.get(1) ?: return null
        val nokkel = Regex("pack_key=([0-9a-fA-F]{64})").find(tekst)?.groupValues?.get(1) ?: return null
        return id to nokkel
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun hmac(nokkel: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(nokkel, "HmacSHA256")) }.doFinal(data)

    fun hkdf(ikm: ByteArray, info: ByteArray, lengde: Int): ByteArray {
        val prk = hmac(ByteArray(32), ikm)
        var forrige = ByteArray(0)
        val ut = ByteArrayOutputStream()
        var teller = 1
        while (ut.size() < lengde) {
            forrige = hmac(prk, forrige + info + byteArrayOf(teller.toByte()))
            ut.write(forrige)
            teller++
        }
        return ut.toByteArray().copyOfRange(0, lengde)
    }

    fun nokler(pakkenokkel: ByteArray): Pair<ByteArray, ByteArray> {
        val alle = hkdf(pakkenokkel, "Sticker Pack".toByteArray(), 64)
        return alle.copyOfRange(0, 32) to alle.copyOfRange(32, 64)
    }

    fun dekrypter(data: ByteArray, aes: ByteArray, mac: ByteArray): ByteArray {
        if (data.size < 16 + 32) throw IllegalArgumentException("For kort")
        val innhold = data.copyOfRange(0, data.size - 32)
        val kode = data.copyOfRange(data.size - 32, data.size)
        if (!hmac(mac, innhold).contentEquals(kode)) throw SecurityException("Feil nøkkel")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aes, "AES"), IvParameterSpec(innhold.copyOfRange(0, 16)))
        return cipher.doFinal(innhold, 16, innhold.size - 16)
    }

    private fun hent(url: String): ByteArray = Doh.hent(url)

    private class Proto(val b: ByteArray) {
        var i = 0
        fun varint(): Long {
            var v = 0L
            var s = 0
            while (true) {
                val c = b[i++].toInt() and 0xFF
                v = v or ((c and 0x7F).toLong() shl s)
                if (c and 0x80 == 0) return v
                s += 7
            }
        }
        fun felter(): List<Pair<Int, Any>> {
            val ut = mutableListOf<Pair<Int, Any>>()
            while (i < b.size) {
                val nokkel = varint()
                val nr = (nokkel shr 3).toInt()
                when ((nokkel and 7).toInt()) {
                    0 -> ut += nr to varint()
                    2 -> {
                        val n = varint().toInt()
                        ut += nr to b.copyOfRange(i, i + n)
                        i += n
                    }
                    5 -> i += 4
                    1 -> i += 8
                    else -> return ut
                }
            }
            return ut
        }
    }

    fun manifest(data: ByteArray): Pair<String, List<Int>> {
        val felter = Proto(data).felter()
        val tittel = felter.firstOrNull { it.first == 1 }?.let { String(it.second as ByteArray) } ?: "Klistremerker"
        val ider = felter.filter { it.first == 4 }.map { f ->
            (Proto(f.second as ByteArray).felter().firstOrNull { it.first == 1 }?.second as? Long ?: 0L).toInt()
        }
        return tittel to ider
    }

    fun importer(context: Context, id: String, nokkelHex: String, framdrift: (Int, Int) -> Unit): Merkepakke {
        val (aes, mac) = nokler(hex(nokkelHex))
        val (tittel, ider) = manifest(dekrypter(hent("https://cdn.signal.org/stickers/$id/manifest.proto"), aes, mac))
        val m = File(mappe(context), id.lowercase()).apply { mkdirs() }
        File(m, "tittel.txt").writeText(tittel)
        ider.forEachIndexed { n, s ->
            framdrift(n, ider.size)
            val fil = File(m, "$s.webp")
            if (!fil.exists()) fil.writeBytes(dekrypter(hent("https://cdn.signal.org/stickers/$id/full/$s"), aes, mac))
        }
        return pakker(context).first { it.id == id.lowercase() }
    }

    fun forhand(fil: File, side: Int): Bitmap? {
        val grenser = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(fil.path, grenser)
        var skala = 1
        while (grenser.outWidth / (skala * 2) >= side) skala *= 2
        return BitmapFactory.decodeFile(fil.path, BitmapFactory.Options().apply { inSampleSize = skala })
    }

    fun tilMms(fil: File): Pair<ByteArray, String> {
        val bilde = BitmapFactory.decodeFile(fil.path) ?: throw IllegalArgumentException("Kunne ikke lese klistremerket")
        val png = ByteArrayOutputStream().also { bilde.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (png.size() <= 280_000) return png.toByteArray() to "image/png"
        val hvit = Bitmap.createBitmap(bilde.width, bilde.height, Bitmap.Config.ARGB_8888)
        Canvas(hvit).apply {
            drawColor(Color.WHITE)
            drawBitmap(bilde, 0f, 0f, null)
        }
        val jpg = ByteArrayOutputStream().also { hvit.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return jpg.toByteArray() to "image/jpeg"
    }
}
