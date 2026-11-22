package me.arnee.tur

import java.io.DataInputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream
import kotlin.math.floor

object Hoyde {
    private const val MERKE = 0x54555248

    private class Rutenett(val n: Int, val m: Int, val sor: Double, val vest: Double, val nord: Double, val ost: Double, val h: ShortArray)

    private var sist: Pair<File, Rutenett?>? = null

    fun fil(mappe: File, y: Int, x: Int) = File(mappe, "hoyde_${y}_$x.bin")

    @Synchronized
    private fun last(f: File): Rutenett? {
        sist?.let { if (it.first == f) return it.second }
        val r = if (!f.exists()) null else runCatching {
            DataInputStream(GZIPInputStream(f.inputStream(), 1 shl 16).buffered(1 shl 16)).use { d ->
                if (d.readInt() != MERKE) return@use null
                val n = d.readInt()
                val m = d.readInt()
                val sor = d.readDouble()
                val vest = d.readDouble()
                val nord = d.readDouble()
                val ost = d.readDouble()
                val b = ByteArray(n * m * 2)
                d.readFully(b)
                val h = ShortArray(n * m)
                ByteBuffer.wrap(b).asShortBuffer().get(h)
                Rutenett(n, m, sor, vest, nord, ost, h)
            }
        }.getOrNull()
        sist = f to r
        return r
    }

    fun leggPa(mappe: File, p: List<Punkt>): List<Punkt> {
        var noen = false
        val ut = p.map { q ->
            val y = floor(q.lat / Graf.BLOKK_LAT).toInt()
            val x = floor(q.lon / Graf.BLOKK_LON).toInt()
            val r = last(fil(mappe, y, x)) ?: return@map q
            val rad = ((r.nord - q.lat) / (r.nord - r.sor) * r.m).toInt().coerceIn(0, r.m - 1)
            val kol = ((q.lon - r.vest) / (r.ost - r.vest) * r.n).toInt().coerceIn(0, r.n - 1)
            val h = r.h[rad * r.n + kol]
            if (h == Short.MIN_VALUE) return@map q
            noen = true
            Punkt(q.lat, q.lon, q.tid, h.toDouble(), q.seg, q.rute)
        }
        return if (noen) ut else p
    }
}
