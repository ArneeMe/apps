package me.arnee.tur

import android.util.JsonReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

class Graf(
    val lat: DoubleArray,
    val lon: DoubleArray,
    private val forste: IntArray,
    private val til: IntArray,
    private val lengde: FloatArray,
    private val vekt: FloatArray,
    val sor: Double,
    val vest: Double,
    val nord: Double,
    val ost: Double,
) {
    val antall get() = lat.size

    fun dekker(s: Double, w: Double, n: Double, e: Double) = s >= sor && w >= vest && n <= nord && e <= ost

    val hovednett: BooleanArray by lazy {
        val merke = IntArray(antall) { -1 }
        var beste = -1
        var storst = 0
        for (i in 0 until antall) {
            if (merke[i] != -1) continue
            val del = naa(i, merke, i)
            if (del > storst) {
                storst = del
                beste = i
            }
        }
        BooleanArray(antall) { merke[it] == beste }
    }

    private fun naa(fra: Int, merke: IntArray, verdi: Int): Int {
        val ko = IntArray(antall)
        var les = 0
        var skriv = 0
        merke[fra] = verdi
        ko[skriv++] = fra
        while (les < skriv) {
            val a = ko[les++]
            for (i in forste[a] until forste[a + 1]) {
                val b = til[i]
                if (merke[b] == -1) {
                    merke[b] = verdi
                    ko[skriv++] = b
                }
            }
        }
        return skriv
    }

    fun naermeste(p: Punkt, lov: BooleanArray? = null): Int {
        val kx = cos(p.lat * PI / 180)
        var beste = -1
        var kortest = Double.MAX_VALUE
        for (i in 0 until antall) {
            if (lov != null && !lov[i]) continue
            val dx = (lon[i] - p.lon) * kx
            val dy = lat[i] - p.lat
            val d = dx * dx + dy * dy
            if (d < kortest) {
                kortest = d
                beste = i
            }
        }
        return beste
    }

    fun rute(fra: Int, mal: Int, brukt: Set<Long> = emptySet()): IntArray? {
        if (fra == mal) return intArrayOf(fra)
        val kost = DoubleArray(antall) { Double.MAX_VALUE }
        val forrige = IntArray(antall) { -1 }
        val ferdig = BooleanArray(antall)
        val haug = Haug()
        val kx = cos(lat[mal] * PI / 180) * 111320
        val h = { i: Int ->
            val dx = (lon[i] - lon[mal]) * kx
            val dy = (lat[i] - lat[mal]) * 110540
            sqrt(dx * dx + dy * dy)
        }
        kost[fra] = 0.0
        haug.legg(h(fra), fra)
        while (haug.storrelse > 0) {
            val a = haug.ta()
            if (ferdig[a]) continue
            if (a == mal) break
            ferdig[a] = true
            for (i in forste[a] until forste[a + 1]) {
                val b = til[i]
                if (ferdig[b]) continue
                val ekstra = if (brukt.isNotEmpty() && nokkel(a, b) in brukt) 4.0 else 1.0
                val ny = kost[a] + lengde[i] * vekt[i] * ekstra
                if (ny < kost[b]) {
                    kost[b] = ny
                    forrige[b] = a
                    haug.legg(ny + h(b), b)
                }
            }
        }
        if (forrige[mal] == -1) return null
        val sti = ArrayList<Int>()
        var n = mal
        while (n != -1) {
            sti.add(n)
            n = forrige[n]
        }
        return sti.reversed().toIntArray()
    }

    private class Haug {
        private var nokler = DoubleArray(1024)
        private var verdier = IntArray(1024)
        var storrelse = 0
            private set

        fun legg(n: Double, v: Int) {
            if (storrelse == nokler.size) {
                nokler = nokler.copyOf(storrelse * 2)
                verdier = verdier.copyOf(storrelse * 2)
            }
            var i = storrelse++
            while (i > 0) {
                val p = (i - 1) / 2
                if (nokler[p] <= n) break
                nokler[i] = nokler[p]
                verdier[i] = verdier[p]
                i = p
            }
            nokler[i] = n
            verdier[i] = v
        }

        fun ta(): Int {
            val topp = verdier[0]
            storrelse--
            val n = nokler[storrelse]
            val v = verdier[storrelse]
            var i = 0
            while (true) {
                var c = i * 2 + 1
                if (c >= storrelse) break
                if (c + 1 < storrelse && nokler[c + 1] < nokler[c]) c++
                if (nokler[c] >= n) break
                nokler[i] = nokler[c]
                verdier[i] = verdier[c]
                i = c
            }
            nokler[i] = n
            verdier[i] = v
            return topp
        }
    }

    companion object {
        fun nokkel(a: Int, b: Int) = (minOf(a, b).toLong() shl 32) or maxOf(a, b).toLong()

        private val utelatt = setOf("motorway", "motorway_link", "trunk", "trunk_link", "construction", "proposed", "abandoned", "platform", "raceway", "bus_guideway", "corridor", "elevator")

        fun vekt(t: Map<String, String>, lop: Boolean): Float? {
            val vei = t["highway"] ?: return null
            if (vei in utelatt) return null
            if (t["foot"] == "no" || t["access"] in setOf("no", "private") && t["foot"] !in setOf("yes", "designated", "permissive")) return null
            val sac = t["sac_scale"]
            if (sac != null && sac.startsWith("demanding_alpine") || sac == "difficult_alpine_hiking") return null
            var v = when (vei) {
                "path", "footway", "track", "bridleway", "pedestrian", "living_street" -> 1.0f
                "steps" -> if (lop) 2.0f else 1.3f
                "cycleway" -> 1.1f
                "service", "residential", "unclassified", "road" -> 1.25f
                "tertiary", "tertiary_link" -> 1.6f
                "secondary", "secondary_link" -> 2.2f
                "primary", "primary_link" -> 3.0f
                else -> 1.5f
            }
            if (t["sidewalk"] in setOf("both", "left", "right", "yes") || t["foot"] in setOf("yes", "designated")) v = minOf(v, 1.3f)
            if (sac != null && sac != "hiking") v *= if (lop) 2.5f else 1.4f
            if (lop && vei == "path" && t["surface"] !in setOf("gravel", "fine_gravel", "compacted", "asphalt", "paved")) v *= 1.3f
            return v
        }

        fun hent(sor: Double, vest: Double, nord: Double, ost: Double, lop: Boolean): Graf {
            val boks = String.format(java.util.Locale.ROOT, "%.5f,%.5f,%.5f,%.5f", sor, vest, nord, ost)
            val sporring = "[out:json][timeout:90];way[highway][highway!~\"^(motorway|motorway_link|trunk|trunk_link|construction|proposed|platform|corridor|elevator|raceway|bus_guideway)$\"][service!~\"^(parking_aisle|driveway|drive-through)$\"][access!~\"^(private|no)$\"]($boks);out body qt;>;out skel qt;"
            val t = URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection
            t.requestMethod = "POST"
            t.doOutput = true
            t.setRequestProperty("User-Agent", "Tur/1 (Android)")
            t.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            t.connectTimeout = 15000
            t.readTimeout = 120000
            t.outputStream.use { it.write(("data=" + URLEncoder.encode(sporring, "UTF-8")).toByteArray()) }
            if (t.responseCode != 200) {
                t.errorStream?.close()
                throw IllegalStateException(if (t.responseCode == 429 || t.responseCode == 504) "Kartdatatjenesten er opptatt. Prøv igjen om litt." else "Fikk ikke hentet kartdata (${t.responseCode}).")
            }
            return t.inputStream.use { les(it, lop, sor, vest, nord, ost) }
        }

        fun les(inn: InputStream, lop: Boolean, sor: Double, vest: Double, nord: Double, ost: Double): Graf {
            val veier = ArrayList<LongArray>()
            val vekter = ArrayList<Float>()
            val nodeLat = HashMap<Long, Double>()
            val nodeLon = HashMap<Long, Double>()
            val r = JsonReader(InputStreamReader(inn, Charsets.UTF_8))
            r.beginObject()
            while (r.hasNext()) {
                if (r.nextName() != "elements") {
                    r.skipValue()
                    continue
                }
                r.beginArray()
                while (r.hasNext()) {
                    var type = ""
                    var id = 0L
                    var la = Double.NaN
                    var lo = Double.NaN
                    var noder: LongArray? = null
                    val tagger = HashMap<String, String>()
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "type" -> type = r.nextString()
                            "id" -> id = r.nextLong()
                            "lat" -> la = r.nextDouble()
                            "lon" -> lo = r.nextDouble()
                            "nodes" -> {
                                val l = ArrayList<Long>()
                                r.beginArray()
                                while (r.hasNext()) l.add(r.nextLong())
                                r.endArray()
                                noder = l.toLongArray()
                            }
                            "tags" -> {
                                r.beginObject()
                                while (r.hasNext()) tagger[r.nextName()] = r.nextString()
                                r.endObject()
                            }
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                    if (type == "node" && !la.isNaN()) {
                        nodeLat[id] = la
                        nodeLon[id] = lo
                    } else if (type == "way" && noder != null && noder.size > 1) {
                        val v = vekt(tagger, lop) ?: continue
                        veier.add(noder)
                        vekter.add(v)
                    }
                }
                r.endArray()
            }
            r.endObject()
            val indeks = HashMap<Long, Int>()
            val lat = ArrayList<Double>()
            val lon = ArrayList<Double>()
            val par = ArrayList<IntArray>()
            val parVekt = ArrayList<Float>()
            veier.forEachIndexed { w, noder ->
                var forrige = -1
                for (id in noder) {
                    val la = nodeLat[id]
                    if (la == null) {
                        forrige = -1
                        continue
                    }
                    val i = indeks.getOrPut(id) {
                        lat.add(la)
                        lon.add(nodeLon[id]!!)
                        lat.size - 1
                    }
                    if (forrige >= 0 && forrige != i) {
                        par.add(intArrayOf(forrige, i))
                        parVekt.add(vekter[w])
                    }
                    forrige = i
                }
            }
            val n = lat.size
            val grad = IntArray(n + 1)
            par.forEach {
                grad[it[0] + 1]++
                grad[it[1] + 1]++
            }
            for (i in 1..n) grad[i] += grad[i - 1]
            val forste = grad.copyOf()
            val pos = grad.copyOf()
            val til = IntArray(par.size * 2)
            val lengde = FloatArray(par.size * 2)
            val vekt = FloatArray(par.size * 2)
            val la = lat.toDoubleArray()
            val lo = lon.toDoubleArray()
            par.forEachIndexed { k, (a, b) ->
                val d = avstand(Punkt(la[a], lo[a]), Punkt(la[b], lo[b])).toFloat()
                for ((x, y) in listOf(a to b, b to a)) {
                    val j = pos[x]++
                    til[j] = y
                    lengde[j] = d
                    vekt[j] = parVekt[k]
                }
            }
            return Graf(la, lo, forste, til, lengde, vekt, sor, vest, nord, ost)
        }
    }
}
