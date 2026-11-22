package me.arnee.tur

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.floor

object Hoyde {
    private const val Z = 12
    private const val ANTALL = 1 shl Z

    fun fliser(sor: Double, vest: Double, nord: Double, ost: Double): List<Pair<Int, Int>> {
        val x0 = floor(Kart.merX(vest) * ANTALL).toInt()
        val x1 = floor(Kart.merX(ost) * ANTALL).toInt()
        val y0 = floor(Kart.merY(nord) * ANTALL).toInt()
        val y1 = floor(Kart.merY(sor) * ANTALL).toInt()
        return (y0..y1).flatMap { y -> (x0..x1).map { x -> x to y } }
    }

    private fun fil(mappe: File, x: Int, y: Int) = File(mappe, "${Z}_${x}_$y.png")

    fun har(mappe: File, x: Int, y: Int) = fil(mappe, x, y).exists()

    fun hent(mappe: File, x: Int, y: Int) {
        mappe.mkdirs()
        val t = URL("https://s3.amazonaws.com/elevation-tiles-prod/terrarium/$Z/$x/$y.png").openConnection() as HttpURLConnection
        t.setRequestProperty("User-Agent", "Tur/1 (Android)")
        t.connectTimeout = 15000
        t.readTimeout = 30000
        if (t.responseCode != 200) {
            t.errorStream?.close()
            throw IllegalStateException("Høydedata svarte ${t.responseCode}")
        }
        val ny = File.createTempFile("hoyde", ".ny", mappe)
        t.inputStream.use { inn -> ny.outputStream().use { inn.copyTo(it) } }
        if (!ny.renameTo(fil(mappe, x, y))) ny.delete()
    }

    fun leggPa(mappe: File, p: List<Punkt>): List<Punkt> {
        if (p.isEmpty()) return p
        val bilder = HashMap<Pair<Int, Int>, Bitmap?>()
        val valg = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPremultiplied = false
        }
        var noen = false
        val ut = p.map { q ->
            val fx = Kart.merX(q.lon) * ANTALL
            val fy = Kart.merY(q.lat) * ANTALL
            val x = floor(fx).toInt()
            val y = floor(fy).toInt()
            val b = bilder.getOrPut(x to y) { fil(mappe, x, y).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path, valg) } }
                ?: return@map q
            val px = ((fx - x) * b.width).toInt().coerceIn(0, b.width - 1)
            val py = ((fy - y) * b.height).toInt().coerceIn(0, b.height - 1)
            val c = b.getPixel(px, py)
            val h = ((c shr 16) and 0xff) * 256.0 + ((c shr 8) and 0xff) + (c and 0xff) / 256.0 - 32768
            noen = true
            Punkt(q.lat, q.lon, q.tid, h, q.seg, q.rute)
        }
        bilder.values.forEach { it?.recycle() }
        return if (noen) ut else p
    }
}
