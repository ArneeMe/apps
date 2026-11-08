package me.arnee.vaer

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.ZoneId
import org.json.JSONObject

class Sted(val navn: String, val lat: Double, val lon: Double)

class Time(val tid: Long, val time: Int, val temp: Double, val vind: Double, val nedbor: Double, val symbol: String)

class Varsel(val hentet: Long, val utloper: Long, val timer: List<Time>)

object Met {
    private const val AGENT = "arneeme-vaer/1.0 https://github.com/ArneeMe/apps"

    private fun hent(url: String): Pair<JSONObject, Long> {
        val tilkobling = URL(url).openConnection() as HttpURLConnection
        tilkobling.setRequestProperty("User-Agent", AGENT)
        tilkobling.connectTimeout = 10_000
        tilkobling.readTimeout = 15_000
        val utloper = tilkobling.getHeaderFieldDate("Expires", System.currentTimeMillis() + 30 * 60_000)
        return tilkobling.inputStream.bufferedReader().use { JSONObject(it.readText()) } to utloper
    }

    fun sok(tekst: String): List<Sted> {
        val (svar, _) = hent("https://ws.geonorge.no/stedsnavn/v1/navn?fuzzy=true&utkoordsys=4258&treffPerSide=8&sok=" + URLEncoder.encode(tekst, "UTF-8"))
        val navn = svar.getJSONArray("navn")
        return (0 until navn.length()).map {
            val n = navn.getJSONObject(it)
            val punkt = n.getJSONObject("representasjonspunkt")
            val kommune = n.optJSONArray("kommuner")?.optJSONObject(0)?.optString("kommunenavn")
            val type = n.optString("navneobjekttype", "")
            val skrevet = n.optJSONArray("stedsnavn")?.optJSONObject(0)?.optString("skrivemåte") ?: n.optString("skrivemåte", "?")
            Sted(listOfNotNull(skrevet, kommune?.takeIf { it != skrevet }, type.takeIf { it.isNotEmpty() }).joinToString(", "), punkt.getDouble("nord"), punkt.getDouble("øst"))
        }
    }

    fun varsel(sted: Sted): Varsel {
        val (svar, utloper) = hent("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f".format(java.util.Locale.ROOT, sted.lat, sted.lon))
        val serie = svar.getJSONObject("properties").getJSONArray("timeseries")
        val sone = ZoneId.systemDefault()
        val timer = (0 until minOf(serie.length(), 36)).mapNotNull {
            val t = serie.getJSONObject(it)
            val data = t.getJSONObject("data")
            val na = data.getJSONObject("instant").getJSONObject("details")
            val neste = data.optJSONObject("next_1_hours") ?: return@mapNotNull null
            val tid = OffsetDateTime.parse(t.getString("time"))
            Time(
                tid.toInstant().toEpochMilli(),
                tid.atZoneSameInstant(sone).hour,
                na.getDouble("air_temperature"),
                na.optDouble("wind_speed", 0.0),
                neste.optJSONObject("details")?.optDouble("precipitation_amount", 0.0) ?: 0.0,
                neste.getJSONObject("summary").getString("symbol_code"),
            )
        }
        return Varsel(System.currentTimeMillis(), utloper, timer)
    }

    fun ord(symbol: String) = when {
        symbol.contains("thunder") -> "torden"
        symbol.contains("sleet") -> "sludd"
        symbol.contains("snow") -> "snø"
        symbol.contains("heavyrain") -> "kraftig regn"
        symbol.contains("lightrain") -> "lett regn"
        symbol.contains("rain") -> "regn"
        symbol.startsWith("fog") -> "tåke"
        symbol.startsWith("cloudy") -> "skyet"
        symbol.startsWith("partlycloudy") -> "delvis skyet"
        symbol.startsWith("fair") -> "lettskyet"
        symbol.startsWith("clearsky") -> "klart"
        else -> symbol
    }

    fun ikon(symbol: String) = when {
        symbol.contains("snow") || symbol.contains("sleet") -> "snowflake"
        symbol.contains("rain") || symbol.contains("thunder") || symbol.startsWith("cloudy") || symbol.startsWith("fog") -> "cloud"
        symbol.contains("night") -> "cloud-moon"
        symbol.startsWith("partlycloudy") || symbol.startsWith("fair") -> "cloud-sun"
        else -> "sun"
    }
}
