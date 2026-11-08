package me.arnee.meldinger

import android.content.Context
import android.util.Base64
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

class Melding(val id: String, val tid: Long, val fra: String, val tekst: String)

object Postkasse {
    const val ENHET = "telefon"

    private fun lager(context: Context) = context.getSharedPreferences("meldinger", Context.MODE_PRIVATE)

    private fun tilfeldig(lengde: Int): String {
        val tegn = "abcdefghijkmnopqrstuvwxyz23456789"
        val r = SecureRandom()
        return (1..lengde).map { tegn[r.nextInt(tegn.length)] }.joinToString("")
    }

    fun kobling(context: Context): String {
        val lagret = lager(context).getString("kobling", null)
        if (lagret != null) return lagret
        val ny = "https://ntfy.sh/${tilfeldig(24)}#${tilfeldig(32)}"
        lager(context).edit().putString("kobling", ny).apply()
        return ny
    }

    fun settKobling(context: Context, kobling: String) {
        lager(context).edit().putString("kobling", kobling.trim()).putString("siste", "all").apply()
    }

    private fun adresse(context: Context) = kobling(context).substringBefore("#")

    private fun nokkel(context: Context): SecretKeySpec {
        val passord = kobling(context).substringAfter("#")
        val salt = adresse(context).substringAfterLast("/").toByteArray()
        val fabrikk = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = fabrikk.generateSecret(PBEKeySpec(passord.toCharArray(), salt, 100_000, 256)).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun krypter(context: Context, tekst: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, nokkel(context), GCMParameterSpec(128, iv))
        return Base64.encodeToString(iv + cipher.doFinal(tekst.toByteArray()), Base64.NO_WRAP)
    }

    private fun dekrypter(context: Context, data: String): String {
        val bytes = Base64.decode(data, Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, nokkel(context), GCMParameterSpec(128, bytes, 0, 12))
        return String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }

    private fun logg(context: Context) = File(context.filesDir, "meldinger.jsonl")

    fun meldinger(context: Context): List<Melding> {
        val fil = logg(context)
        if (!fil.exists()) return emptyList()
        return fil.readLines().filter { it.isNotBlank() }.map {
            val o = JSONObject(it)
            Melding(o.getString("id"), o.getLong("tid"), o.getString("fra"), o.getString("tekst"))
        }
    }

    private fun lagre(context: Context, melding: Melding) {
        if (meldinger(context).any { it.id == melding.id }) return
        val o = JSONObject().put("id", melding.id).put("tid", melding.tid).put("fra", melding.fra).put("tekst", melding.tekst)
        logg(context).appendText(o.toString() + "\n")
    }

    fun send(context: Context, tekst: String) {
        val innhold = JSONObject().put("fra", ENHET).put("tekst", tekst).toString()
        val tilkobling = URL(adresse(context)).openConnection() as HttpURLConnection
        tilkobling.requestMethod = "POST"
        tilkobling.doOutput = true
        tilkobling.connectTimeout = 10_000
        tilkobling.outputStream.use { it.write(krypter(context, innhold).toByteArray()) }
        val svar = tilkobling.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        lagre(context, Melding(svar.getString("id"), svar.getLong("time") * 1000, ENHET, tekst))
    }

    fun hent(context: Context) {
        val siste = lager(context).getString("siste", "all")
        val tilkobling = URL("${adresse(context)}/json?poll=1&since=$siste").openConnection() as HttpURLConnection
        tilkobling.connectTimeout = 10_000
        tilkobling.readTimeout = 20_000
        var nySiste = siste
        tilkobling.inputStream.bufferedReader().useLines { linjer ->
            linjer.filter { it.isNotBlank() }.forEach { linje ->
                val o = JSONObject(linje)
                if (o.optString("event") != "message") return@forEach
                nySiste = o.getString("id")
                val innhold = runCatching { JSONObject(dekrypter(context, o.getString("message"))) }.getOrNull() ?: return@forEach
                lagre(context, Melding(o.getString("id"), o.getLong("time") * 1000, innhold.optString("fra", "?"), innhold.optString("tekst", "")))
            }
        }
        lager(context).edit().putString("siste", nySiste).apply()
    }
}
