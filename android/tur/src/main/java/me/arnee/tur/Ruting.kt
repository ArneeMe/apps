package me.arnee.tur

import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

class Forslag(val data: ByteArray, val spor: Spor)

object Ruting {
    private const val PROFIL = "hiking-mountain"

    fun rundtur(start: Punkt, km: Int, retning: Int, navn: String): Forslag {
        val mal = km * 1000.0
        var radius = mal / 5
        var forslag = hent(listOf(start), "&engineMode=4&roundTripPoints=5&roundTripStartDirection=$retning&roundTripDistance=${radius.toInt()}", navn)
        val lengde = forslag.spor.lengde
        if (lengde > 0 && abs(lengde / mal - 1) > 0.15) {
            radius *= mal / lengde
            forslag = hent(listOf(start), "&engineMode=4&roundTripPoints=5&roundTripStartDirection=$retning&roundTripDistance=${radius.toInt()}", navn)
        }
        return forslag
    }

    fun mellom(punkter: List<Punkt>, navn: String) = hent(punkter, "", navn)

    private fun hent(punkter: List<Punkt>, ekstra: String, navn: String): Forslag {
        val lonlats = punkter.joinToString("%7C") { String.format(Locale.ROOT, "%.6f,%.6f", it.lon, it.lat) }
        val t = URL("https://brouter.de/brouter?lonlats=$lonlats&profile=$PROFIL&alternativeidx=0&format=gpx$ekstra").openConnection() as HttpURLConnection
        t.setRequestProperty("User-Agent", "Tur/1 (Android)")
        t.connectTimeout = 15000
        t.readTimeout = 60000
        if (t.responseCode != 200) {
            val feil = t.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty().trim()
            throw IllegalStateException(feil.lineSequence().firstOrNull()?.take(160)?.ifEmpty { null } ?: "Fikk ikke laget ruta.")
        }
        val svar = t.inputStream.use { it.readBytes() }
        val s = Gpx.les(svar.inputStream())
        if (s.punkter.size < 2) throw IllegalStateException("Fant ingen vei her.")
        val spor = Spor(navn, "", s.punkter.map { Punkt(it.lat, it.lon, 0, it.hoyde, 0, true) })
        return Forslag(Gpx.skriv(spor).toByteArray(), spor)
    }

    fun tid(spor: Spor, type: String): Long {
        val km = spor.lengde / 1000
        val timer = if (type == "running") km / 10 else km / 4 + spor.stigning / 600
        return (timer * 3600000).toLong()
    }
}
