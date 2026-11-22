package me.arnee.tur

import android.content.Context
import java.io.File
import java.io.InputStream
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Calendar
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class Punkt(val lat: Double, val lon: Double, val tid: Long = 0, val hoyde: Double? = null, val seg: Int = 0)

class Merke(val lat: Double, val lon: Double, val navn: String)

class Spor(val navn: String, val type: String, val punkter: List<Punkt>, val merker: List<Merke> = emptyList(), private val varighet: Long = 0) {
    val start get() = punkter.firstOrNull { it.tid > 0 }?.tid ?: 0L
    val lengde by lazy { lengde(punkter) }
    val tid by lazy { if (varighet > 0) varighet else punkter.groupBy { it.seg }.values.sumOf { s -> s.last().tid - s.first().tid } }
    val stigning by lazy {
        var sum = 0.0
        var ref: Double? = null
        punkter.forEach { p ->
            val h = p.hoyde ?: return@forEach
            val r = ref
            if (r == null || r - h > 5) ref = h else if (h - r > 5) {
                sum += h - r
                ref = h
            }
        }
        sum
    }
}

fun avstand(a: Punkt, b: Punkt): Double {
    val r = Math.PI / 180
    val dLat = (b.lat - a.lat) * r
    val dLon = (b.lon - a.lon) * r
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(a.lat * r) * cos(b.lat * r) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * 6371000 * asin(sqrt(h))
}

fun lengde(p: List<Punkt>) = p.zipWithNext().sumOf { (a, b) -> if (a.seg == b.seg) avstand(a, b) else 0.0 }

object Vis {
    private val nb = Locale("nb", "NO")
    private val hele = listOf("Januar", "Februar", "Mars", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Desember")
    private val maneder = listOf("jan", "feb", "mar", "apr", "mai", "jun", "jul", "aug", "sep", "okt", "nov", "des")

    fun km(m: Double) = String.format(nb, "%.2f km", m / 1000)

    fun tid(ms: Long): String {
        val s = maxOf(0, ms / 1000)
        return if (s >= 3600) String.format(nb, "%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60) else String.format(nb, "%d:%02d", s / 60, s % 60)
    }

    fun tempo(ms: Long, m: Double): String {
        if (m < 20 || ms <= 0) return "–"
        val s = (ms / 1000.0 / (m / 1000)).roundToLong()
        return if (s >= 3600) "–" else String.format(nb, "%d:%02d", s / 60, s % 60)
    }

    fun fart(ms: Long, m: Double) = if (ms <= 0 || m < 20) "–" else String.format(nb, "%.1f", m / 1000 / (ms / 3600000.0))

    fun dato(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return String.format(nb, "%d. %s %02d:%02d", c.get(Calendar.DAY_OF_MONTH), maneder[c.get(Calendar.MONTH)], c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    fun maned(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "${hele[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}"
    }

    fun type(t: String) = if (t == "running") "Løp" else "Tur"
}

object Gpx {
    fun les(inn: InputStream): Spor {
        val x = XmlPullParserFactory.newInstance().newPullParser()
        x.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        x.setInput(inn, null)
        val punkter = ArrayList<Punkt>()
        val merker = ArrayList<Merke>()
        val tekst = StringBuilder()
        var navn = ""
        var type = ""
        var seg = -1
        var i = ""
        var lat = 0.0
        var lon = 0.0
        var hoyde: Double? = null
        var tid = 0L
        var pNavn = ""
        var varighet = 0L
        while (true) {
            when (x.next()) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.START_TAG -> {
                    tekst.setLength(0)
                    when (val tag = x.name.substringAfter(':')) {
                        "trkseg", "rte" -> seg++
                        "trkpt", "rtept", "wpt" -> {
                            i = tag
                            lat = x.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: Double.NaN
                            lon = x.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: Double.NaN
                            hoyde = null
                            tid = 0L
                            pNavn = ""
                        }
                    }
                }
                XmlPullParser.TEXT -> tekst.append(x.text)
                XmlPullParser.END_TAG -> {
                    val t = tekst.toString().trim()
                    tekst.setLength(0)
                    when (x.name.substringAfter(':')) {
                        "ele" -> hoyde = t.toDoubleOrNull()
                        "time" -> if (i.isNotEmpty()) tid = tidFra(t)
                        "name" -> if (i.isNotEmpty()) pNavn = t else if (navn.isEmpty()) navn = t
                        "type" -> if (i.isEmpty() && type.isEmpty()) type = t
                        "varighet" -> varighet = t.toLongOrNull() ?: 0
                        "trkpt", "rtept" -> {
                            if (!lat.isNaN() && !lon.isNaN()) punkter.add(Punkt(lat, lon, tid, hoyde, maxOf(seg, 0)))
                            i = ""
                        }
                        "wpt" -> {
                            if (!lat.isNaN() && !lon.isNaN()) merker.add(Merke(lat, lon, pNavn))
                            i = ""
                        }
                    }
                }
            }
        }
        return Spor(navn, type, punkter, merker, varighet)
    }

    private fun tidFra(t: String) = runCatching { Instant.parse(t).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }
        .getOrDefault(0L)

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun skriv(spor: Spor) = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Tur\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append(" <trk>\n  <name>${esc(spor.navn)}</name>\n  <type>${esc(spor.type)}</type>\n")
        if (spor.tid > 0) append("  <extensions><varighet>${spor.tid}</varighet></extensions>\n")
        spor.punkter.groupBy { it.seg }.values.forEach { s ->
            append("  <trkseg>\n")
            s.forEach { p ->
                append("   <trkpt lat=\"${String.format(Locale.ROOT, "%.7f", p.lat)}\" lon=\"${String.format(Locale.ROOT, "%.7f", p.lon)}\">")
                p.hoyde?.let { append("<ele>${String.format(Locale.ROOT, "%.1f", it)}</ele>") }
                if (p.tid > 0) append("<time>${Instant.ofEpochMilli(p.tid / 1000 * 1000)}</time>")
                append("</trkpt>\n")
            }
            append("  </trkseg>\n")
        }
        append(" </trk>\n</gpx>\n")
    }
}

class Sammendrag(val fil: String, val navn: String, val type: String, val start: Long, val lengde: Double, val tid: Long, val stigning: Double) {
    val egetNavn get() = navn.isNotEmpty() && navn != standardNavn(type, start)
}

fun standardNavn(type: String, start: Long) = "${Vis.type(type)} ${Vis.dato(start)}"

object Turer {
    fun mappe(k: Context) = File(k.filesDir, "turer").apply { mkdirs() }

    fun alle(k: Context) = (mappe(k).listFiles() ?: emptyArray()).filter { it.name.endsWith(".gpx") }.sortedByDescending { it.name }

    fun les(f: File) = runCatching { f.inputStream().use { Gpx.les(it) } }.getOrNull()

    private fun indeks(k: Context) = File(k.filesDir, "turer.json")

    @Synchronized
    fun oversikt(k: Context): List<Sammendrag> {
        val kjent = HashMap<String, Sammendrag>()
        runCatching {
            val a = JSONArray(indeks(k).readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                kjent[o.getString("fil")] = Sammendrag(o.getString("fil"), o.optString("navn"), o.optString("type"), o.optLong("start"), o.optDouble("lengde"), o.optLong("tid"), o.optDouble("stigning"))
            }
        }
        var endret = false
        val liste = alle(k).mapNotNull { f ->
            kjent[f.name] ?: les(f)?.let { s ->
                endret = true
                Sammendrag(f.name, s.navn, s.type, s.start, s.lengde, s.tid, s.stigning)
            }
        }
        if (endret || liste.size != kjent.size) {
            val a = JSONArray()
            liste.forEach { t -> a.put(JSONObject().put("fil", t.fil).put("navn", t.navn).put("type", t.type).put("start", t.start).put("lengde", t.lengde).put("tid", t.tid).put("stigning", t.stigning)) }
            indeks(k).writeText(a.toString())
        }
        return liste
    }

    @Synchronized
    fun glem(k: Context, fil: File) {
        runCatching {
            val a = JSONArray(indeks(k).readText())
            val ny = JSONArray()
            for (i in 0 until a.length()) if (a.getJSONObject(i).getString("fil") != fil.name) ny.put(a.getJSONObject(i))
            indeks(k).writeText(ny.toString())
        }
    }

    fun slett(k: Context, fil: File) {
        fil.delete()
        glem(k, fil)
    }

    fun giNavn(k: Context, fil: File, spor: Spor, navn: String) {
        val ny = File(fil.path + ".ny")
        ny.writeText(Gpx.skriv(Spor(navn, spor.type, spor.punkter, varighet = spor.tid)))
        ny.renameTo(fil)
        glem(k, fil)
    }
}

object Rute {
    var spor: Spor? = null
        private set
    private var sum = DoubleArray(0)

    private fun fil(k: Context) = File(k.filesDir, "rute.gpx")

    fun last(k: Context) {
        if (spor == null && fil(k).exists()) sett(Turer.les(fil(k)))
    }

    fun importer(k: Context, data: ByteArray): Spor? {
        val s = runCatching { Gpx.les(data.inputStream()) }.getOrNull() ?: return null
        if (s.punkter.isEmpty() && s.merker.isEmpty()) return null
        fil(k).writeBytes(data)
        sett(s)
        return s
    }

    fun fjern(k: Context) {
        fil(k).delete()
        sett(null)
    }

    private fun sett(s: Spor?) {
        spor = s
        sist = 0
        val p = s?.punkter ?: emptyList()
        sum = DoubleArray(p.size)
        for (i in 1 until p.size) sum[i] = sum[i - 1] + if (p[i].seg == p[i - 1].seg) avstand(p[i - 1], p[i]) else 0.0
    }

    private var sist = 0

    fun igjen(her: Punkt): Pair<Double, Double>? {
        val p = spor?.punkter ?: return null
        if (p.size < 2) return null
        val d = DoubleArray(p.size) { avstand(her, p[it]) }
        val kortest = d.min()
        var beste = -1
        for (i in p.indices) if (d[i] <= kortest + 25 && (beste == -1 || (i >= sist && (beste < sist || i < beste)))) beste = i
        sist = beste
        return Pair(sum.last() - sum[beste], kortest)
    }
}
