package me.arnee.attester

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime
import org.json.JSONArray
import org.json.JSONObject

class Org(val slug: String, val navn: String)

class Innsending(val id: String, val mal: String, val data: Map<String, String>, val sendt: OffsetDateTime, val utstedt: OffsetDateTime?)

class Felt(val nokkel: String, val navn: String)

class Mal(val navn: String, val felt: List<Felt>)

class Utlogget : Exception()

class Feil(melding: String) : Exception(melding)

object Api {
    const val NETTSTED = "https://attester.no"
    private const val AUTH = "https://fjolhedkolpveurwmylm.auth.eu-central-1.nhost.run/v1"

    private fun kall(metode: String, url: String, kropp: JSONObject? = null, token: String? = null): Pair<Int, String> {
        val tilkobling = URL(url).openConnection() as HttpURLConnection
        tilkobling.requestMethod = metode
        tilkobling.connectTimeout = 15_000
        tilkobling.readTimeout = 15_000
        tilkobling.setRequestProperty("Accept", "application/json")
        if (token != null) tilkobling.setRequestProperty("Authorization", "Bearer $token")
        if (kropp != null) {
            tilkobling.doOutput = true
            tilkobling.setRequestProperty("Content-Type", "application/json")
            tilkobling.outputStream.use { it.write(kropp.toString().toByteArray()) }
        }
        val kode = tilkobling.responseCode
        val strom = if (kode < 400) tilkobling.inputStream else tilkobling.errorStream
        return kode to (strom?.bufferedReader()?.use { it.readText() } ?: "")
    }

    private fun lager(context: Context) = context.getSharedPreferences("attester", Context.MODE_PRIVATE)

    fun innlogget(context: Context) = lager(context).contains("forny")

    fun epost(context: Context) = lager(context).getString("epost", "")!!

    private fun lagreOkt(context: Context, okt: JSONObject) {
        lager(context).edit()
            .putString("token", okt.getString("accessToken"))
            .putLong("utloper", System.currentTimeMillis() + okt.getLong("accessTokenExpiresIn") * 1000)
            .putString("forny", okt.getString("refreshToken"))
            .apply()
    }

    fun loggInn(context: Context, epost: String, passord: String) {
        val (kode, svar) = kall("POST", "$AUTH/signin/email-password", JSONObject().put("email", epost).put("password", passord))
        val json = runCatching { JSONObject(svar) }.getOrNull()
        val okt = json?.optJSONObject("session")
        if (kode == 200 && okt != null) {
            lagreOkt(context, okt)
            lager(context).edit().putString("epost", epost).apply()
            return
        }
        if (kode == 200 && json?.optJSONObject("mfa") != null) throw Feil("Kontoen har tofaktor. Det støtter ikke appen ennå.")
        throw Feil(
            when (json?.optString("error")) {
                "invalid-email-password" -> "Feil e-post eller passord."
                "unverified-user" -> "Bekreft e-posten først."
                "disabled-user" -> "Kontoen er sperret."
                else -> "Fikk ikke logget inn. Sjekk nettet."
            }
        )
    }

    fun loggUt(context: Context) {
        val forny = lager(context).getString("forny", null)
        lager(context).edit().remove("token").remove("utloper").remove("forny").remove("org").apply()
        if (forny != null) Thread { runCatching { kall("POST", "$AUTH/signout", JSONObject().put("refreshToken", forny)) } }.start()
    }

    private fun forny(context: Context): String {
        val forny = lager(context).getString("forny", null) ?: throw Utlogget()
        val (kode, svar) = kall("POST", "$AUTH/token", JSONObject().put("refreshToken", forny))
        if (kode == 401 || kode == 403) {
            lager(context).edit().remove("token").remove("forny").apply()
            throw Utlogget()
        }
        if (kode != 200) throw Feil("Fikk ikke kontakt med attester.no.")
        val okt = JSONObject(svar)
        lagreOkt(context, okt)
        return okt.getString("accessToken")
    }

    private fun token(context: Context): String {
        val l = lager(context)
        val token = l.getString("token", null)
        if (token != null && l.getLong("utloper", 0) - 60_000 > System.currentTimeMillis()) return token
        return forny(context)
    }

    private fun hent(context: Context, metode: String, sti: String): String {
        var (kode, svar) = kall(metode, NETTSTED + sti, token = token(context))
        if (kode == 401) {
            val nytt = kall(metode, NETTSTED + sti, token = forny(context))
            kode = nytt.first
            svar = nytt.second
        }
        if (kode == 401) throw Utlogget()
        if (kode == 403) throw Feil("Du har ikke tilgang til denne organisasjonen.")
        if (kode >= 400) throw Feil(runCatching { JSONObject(svar).getString("error") }.getOrDefault("Feil fra attester.no ($kode)."))
        return svar
    }

    private fun kode(tekst: String) = URLEncoder.encode(tekst, "UTF-8").replace("+", "%20")

    fun organisasjoner(context: Context): List<Org> {
        val liste = JSONObject(hent(context, "GET", "/api/me/organizations")).getJSONArray("organizations")
        return (0 until liste.length()).map {
            val o = liste.getJSONObject(it)
            Org(o.getString("slug"), o.getString("name"))
        }
    }

    fun innsendinger(context: Context, slug: String): List<Innsending> {
        val liste = JSONObject(hent(context, "GET", "/api/org/${kode(slug)}/submissions")).getJSONArray("submissions")
        return (0 until liste.length()).map {
            val s = liste.getJSONObject(it)
            val data = s.optJSONObject("data") ?: JSONObject()
            Innsending(
                s.getString("id"),
                s.getString("template_id"),
                data.keys().asSequence().associateWith { data.optString(it) },
                OffsetDateTime.parse(s.getString("created_at")),
                if (s.isNull("issued_at")) null else OffsetDateTime.parse(s.getString("issued_at")),
            )
        }
    }

    fun slett(context: Context, slug: String, id: String) {
        hent(context, "DELETE", "/api/org/${kode(slug)}/submissions/${kode(id)}")
    }

    fun mal(slug: String, id: String): Mal {
        val (kode, svar) = kall("GET", "$NETTSTED/api/org/${kode(slug)}/templates/${kode(id)}")
        if (kode != 200) throw Feil("Fant ikke malen.")
        val m = JSONObject(svar).getJSONObject("template")
        val skjema = m.optJSONArray("form_schema") ?: JSONArray()
        return Mal(m.getString("name"), (0 until skjema.length()).map {
            val f = skjema.getJSONObject(it)
            Felt(f.getString("key"), f.optString("label", f.getString("key")))
        })
    }
}
