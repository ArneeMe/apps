package me.arnee.tur

import android.content.Context
import android.location.Location
import android.os.Build
import java.io.File

object Okt {
    val punkter = ArrayList<Punkt>()
    var type = "running"
        private set
    var start = 0L
        private set
    var lengde = 0.0
        private set
    var lytter: (() -> Unit)? = null
    var avbrutt = false
        private set
    private var aktivFra = 0L
    private var ferdig = 0L
    private var seg = 0

    val pagar get() = start > 0
    val aktiv get() = aktivFra > 0

    fun tid() = ferdig + if (aktiv) System.currentTimeMillis() - aktivFra else 0

    fun intervall() = if (type == "running") 1000L else 5000L

    fun tempoNa(): String {
        val siste = punkter.lastOrNull() ?: return "–"
        if (!aktiv || siste.seg != seg || System.currentTimeMillis() - siste.tid > 15000) return "–"
        val vindu = punkter.takeLastWhile { it.seg == seg && siste.tid - it.tid <= 30000 }
        return Vis.tempo(siste.tid - vindu.first().tid, lengde(vindu))
    }

    private fun fil(k: Context) = File(k.filesDir, "pagaende.txt")

    private fun prefs(k: Context) = k.getSharedPreferences("okt", Context.MODE_PRIVATE)

    private fun husk(k: Context) {
        prefs(k).edit().putString("type", type).putLong("start", start).putLong("aktivFra", aktivFra).putLong("ferdig", ferdig).putInt("seg", seg).apply()
    }

    fun last(k: Context) {
        val p = prefs(k)
        if (pagar || p.getLong("start", 0) == 0L) return
        type = p.getString("type", "running") ?: "running"
        start = p.getLong("start", 0)
        ferdig = p.getLong("ferdig", 0)
        seg = p.getInt("seg", 0)
        punkter.clear()
        if (fil(k).exists()) fil(k).forEachLine { linje ->
            val d = linje.split(',')
            if (d.size == 5) runCatching { punkter.add(Punkt(d[1].toDouble(), d[2].toDouble(), d[3].toLong(), d[4].toDoubleOrNull(), d[0].toInt())) }
        }
        lengde = lengde(punkter)
        val fra = p.getLong("aktivFra", 0)
        if (fra > 0) {
            ferdig += maxOf(0, (punkter.lastOrNull()?.tid ?: fra) - fra)
            seg++
            avbrutt = true
        }
        aktivFra = 0
        husk(k)
    }

    fun start(k: Context, type: String) {
        punkter.clear()
        fil(k).delete()
        avbrutt = false
        this.type = type
        start = System.currentTimeMillis()
        aktivFra = start
        ferdig = 0
        seg = 0
        lengde = 0.0
        husk(k)
    }

    fun pause(k: Context) {
        ferdig = tid()
        aktivFra = 0
        seg++
        husk(k)
    }

    fun fortsett(k: Context) {
        avbrutt = false
        aktivFra = System.currentTimeMillis()
        husk(k)
    }

    fun legg(k: Context, l: Location) {
        if (!aktiv || (l.hasAccuracy() && l.accuracy > 30)) return
        val p = Punkt(l.latitude, l.longitude, System.currentTimeMillis(), hoyde(l), seg)
        val forrige = punkter.lastOrNull()
        if (forrige != null && forrige.seg == seg) {
            val d = avstand(forrige, p)
            if (d < maxOf(3f, if (l.hasAccuracy()) l.accuracy / 2 else 0f) || (l.hasSpeed() && l.speed < 0.3f && d < 10)) return
            lengde += d
        }
        punkter.add(p)
        fil(k).appendText("${p.seg},${p.lat},${p.lon},${p.tid},${p.hoyde ?: ""}\n")
        lytter?.invoke()
    }

    private fun hoyde(l: Location) = when {
        Build.VERSION.SDK_INT >= 34 && l.hasMslAltitude() -> l.mslAltitudeMeters
        l.hasAltitude() -> l.altitude
        else -> null
    }

    fun lagre(k: Context): File? {
        if (punkter.size < 2) {
            forkast(k)
            return null
        }
        val f = File(Turer.mappe(k), "$start.gpx")
        val ny = File(f.path + ".ny")
        ny.writeText(Gpx.skriv(Spor(standardNavn(type, start), type, punkter.toList(), varighet = tid(), startTid = start)))
        if (!ny.renameTo(f)) return null
        forkast(k)
        return f
    }

    fun forkast(k: Context) {
        avbrutt = false
        punkter.clear()
        start = 0
        aktivFra = 0
        ferdig = 0
        seg = 0
        lengde = 0.0
        fil(k).delete()
        prefs(k).edit().clear().apply()
    }
}
