package me.arnee.avganger

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.OffsetDateTime
import org.json.JSONObject

class Stopp(val id: String, val navn: String)

class Avgang(val linje: String, val mot: String, val minutter: Long, val sanntid: Boolean)

object Entur {
    private const val KLIENT = "arneeme-avganger"

    private fun hent(url: String, kropp: String? = null): JSONObject {
        val tilkobling = URL(url).openConnection() as HttpURLConnection
        tilkobling.setRequestProperty("ET-Client-Name", KLIENT)
        tilkobling.connectTimeout = 10_000
        tilkobling.readTimeout = 10_000
        if (kropp != null) {
            tilkobling.requestMethod = "POST"
            tilkobling.doOutput = true
            tilkobling.setRequestProperty("Content-Type", "application/json")
            tilkobling.outputStream.use { it.write(kropp.toByteArray()) }
        }
        return tilkobling.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    }

    fun sok(tekst: String): List<Stopp> {
        val svar = hent("https://api.entur.io/geocoder/v1/autocomplete?layers=venue&size=8&text=" + URLEncoder.encode(tekst, "UTF-8"))
        val treff = svar.getJSONArray("features")
        return (0 until treff.length()).map {
            val p = treff.getJSONObject(it).getJSONObject("properties")
            Stopp(p.getString("id"), p.optString("label", p.getString("name")))
        }
    }

    fun avganger(id: String): List<Avgang> {
        val sporring = """{ stopPlace(id: "$id") { estimatedCalls(numberOfDepartures: 8) { expectedDepartureTime realtime destinationDisplay { frontText } serviceJourney { line { publicCode } } } } }"""
        val svar = hent("https://api.entur.io/journey-planner/v3/graphql", JSONObject().put("query", sporring).toString())
        val kall = svar.getJSONObject("data").getJSONObject("stopPlace").getJSONArray("estimatedCalls")
        val na = OffsetDateTime.now()
        return (0 until kall.length()).map {
            val k = kall.getJSONObject(it)
            Avgang(
                k.getJSONObject("serviceJourney").getJSONObject("line").optString("publicCode", ""),
                k.getJSONObject("destinationDisplay").optString("frontText", ""),
                Duration.between(na, OffsetDateTime.parse(k.getString("expectedDepartureTime"))).toMinutes(),
                k.optBoolean("realtime", false),
            )
        }
    }
}
