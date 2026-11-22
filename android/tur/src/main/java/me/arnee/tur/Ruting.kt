package me.arnee.tur

import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class Forslag(val data: ByteArray, val spor: Spor)

object Ruting {
    private const val MAKS_SPENN = 30000.0

    @Volatile
    private var graf: Graf? = null
    var mappe: File? = null
    private var grafLop = false
    @Volatile
    var fase = ""

    private fun marg(km: Int) = km * 1000.0 / 5 * 1.15 + 300

    @Synchronized
    private fun graf(punkter: List<Punkt>, marg: Double, lop: Boolean): Graf {
        val kx = cos(punkter[0].lat * PI / 180) * 111320
        val sor = punkter.minOf { it.lat } - marg / 110540
        val nord = punkter.maxOf { it.lat } + marg / 110540
        val vest = punkter.minOf { it.lon } - marg / kx
        val ost = punkter.maxOf { it.lon } + marg / kx
        if ((nord - sor) * 110540 > MAKS_SPENN || (ost - vest) * kx > MAKS_SPENN) throw IllegalStateException("Området er for stort. Prøv kortere eller færre punkter.")
        graf?.let { if (grafLop == lop && it.dekker(sor, vest, nord, ost)) return it }
        fase = "Leser veier og stier"
        val g = Graf.hent(sor, vest, nord, ost, lop, mappe ?: throw IllegalStateException("Mangler lagringsplass."))
        if (g.antall == 0) throw IllegalStateException("Fant ingen veier eller stier her.")
        graf = g
        grafLop = lop
        return g
    }

    private fun flytt(p: Punkt, meter: Double, grader: Double): Punkt {
        val v = grader * PI / 180
        return Punkt(p.lat + meter * cos(v) / 110540, p.lon + meter * sin(v) / (111320 * cos(p.lat * PI / 180)))
    }

    private fun lengde(g: Graf, sti: List<Int>) = lengde(sti.map { Punkt(g.lat[it], g.lon[it]) })

    private fun gjennom(g: Graf, noder: List<Int>): List<Int>? {
        val sti = ArrayList<Int>()
        val brukt = HashSet<Long>()
        for (i in 0 until noder.size - 1) {
            val del = g.rute(noder[i], noder[i + 1], brukt) ?: return null
            for (j in 0 until del.size - 1) brukt.add(Graf.nokkel(del[j], del[j + 1]))
            if (sti.isEmpty()) sti.addAll(del.asList()) else sti.addAll(del.asList().drop(1))
        }
        return sti
    }

    fun rundtur(start: Punkt, km: Int, retning: Int, navn: String, lop: Boolean): Forslag {
        val mal = km * 1000.0
        var radius = mal / 5
        val g = graf(listOf(start), marg(km), lop)
        fase = "Regner ut ruta"
        val lov = g.hovednett
        val fra = g.naermeste(start, lov)
        if (fra < 0) throw IllegalStateException("Fant ingen vei her.")
        var beste: List<Int>? = null
        var bom = Double.MAX_VALUE
        repeat(4) {
            val punkter = listOf(-60.0, 0.0, 60.0).map { g.naermeste(flytt(start, radius, retning + it), lov) }
            val sti = gjennom(g, listOf(fra) + punkter + fra)
            if (sti != null && sti.size > 1) {
                val l = lengde(g, sti)
                if (abs(l - mal) < bom) {
                    bom = abs(l - mal)
                    beste = sti
                }
                if (abs(l / mal - 1) < 0.1) return lag(g, beste!!, navn)
                radius *= (mal / l).coerceIn(0.5, 2.0)
            } else radius *= 0.8
        }
        return lag(g, beste ?: throw IllegalStateException("Fant ingen rundtur her."), navn)
    }

    fun mellom(punkter: List<Punkt>, navn: String, lop: Boolean): Forslag {
        val g = graf(punkter, 1000.0, lop)
        fase = "Regner ut ruta"
        val lov = g.hovednett
        val noder = punkter.map { g.naermeste(it, lov) }
        if (noder.any { it < 0 }) throw IllegalStateException("Fant ingen vei her.")
        val sti = gjennom(g, noder) ?: throw IllegalStateException("Fant ingen vei mellom punktene.")
        return lag(g, sti, navn)
    }

    private fun lag(g: Graf, sti: List<Int>, navn: String): Forslag {
        val spor = Spor(navn, "", sti.map { Punkt(g.lat[it], g.lon[it], rute = true) })
        return Forslag(Gpx.skriv(spor).toByteArray(), spor)
    }

    fun medHoyde(f: Forslag, hoyde: File): Forslag {
        val p = Hoyde.leggPa(hoyde, f.spor.punkter)
        if (p === f.spor.punkter) return f
        val spor = Spor(f.spor.navn, "", p)
        return Forslag(Gpx.skriv(spor).toByteArray(), spor)
    }

    fun tid(spor: Spor, type: String): Long {
        val km = spor.lengde / 1000
        val timer = if (type == "running") km / 10 else km / 4 + spor.stigning / 600
        return (timer * 3600000).toLong()
    }
}
