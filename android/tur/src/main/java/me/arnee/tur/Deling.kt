package me.arnee.tur

import android.app.Activity
import android.content.ClipData
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sqrt

object Deling {
    private const val SIDE = "https://arnee.me/tur"

    fun lenke(spor: Spor): String {
        val ut = ByteArrayOutputStream()
        var la = 0L
        var lo = 0L
        forenkle(spor.punkter, 8.0).forEach {
            val a = (it.lat * 1e5).roundToLong()
            val b = (it.lon * 1e5).roundToLong()
            tall(ut, a - la)
            tall(ut, b - lo)
            la = a
            lo = b
        }
        val data = Base64.encodeToString(ut.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val navn = URLEncoder.encode(spor.navn.ifEmpty { "Tur" }, "UTF-8").replace("+", "%20")
        return "$SIDE#v=1&n=$navn&p=$data"
    }

    fun les(uri: Uri): Spor? {
        val fragment = uri.encodedFragment ?: return null
        val felt = fragment.split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
        val data = runCatching { Base64.decode(felt["p"] ?: return null, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }.getOrNull() ?: return null
        val punkter = ArrayList<Punkt>()
        var i = 0
        var la = 0L
        var lo = 0L
        fun neste(): Long? {
            var verdi = 0L
            var skift = 0
            while (i < data.size) {
                val b = data[i++].toInt() and 0xff
                verdi = verdi or ((b and 0x7f).toLong() shl skift)
                skift += 7
                if (b < 0x80) return (verdi ushr 1) xor -(verdi and 1)
            }
            return null
        }
        while (i < data.size) {
            la += neste() ?: break
            lo += neste() ?: break
            punkter.add(Punkt(la / 1e5, lo / 1e5, rute = true))
        }
        if (punkter.size < 2) return null
        val navn = runCatching { URLDecoder.decode(felt["n"].orEmpty(), "UTF-8") }.getOrDefault("")
        return Spor(navn.ifEmpty { "Delt tur" }, "", punkter)
    }

    fun erLenke(uri: Uri) = uri.scheme == "me.arnee.tur" || (uri.host == "arnee.me" && uri.path?.trimEnd('/') == "/tur")

    private fun tall(ut: ByteArrayOutputStream, v: Long) {
        var z = (v shl 1) xor (v shr 63)
        while (z >= 0x80) {
            ut.write(((z and 0x7f) or 0x80).toInt())
            z = z ushr 7
        }
        ut.write(z.toInt())
    }

    fun forenkle(p: List<Punkt>, toleranse: Double): List<Punkt> {
        if (p.size < 3) return p
        val kx = cos(p[0].lat * PI / 180) * 111320
        val x = DoubleArray(p.size) { p[it].lon * kx }
        val y = DoubleArray(p.size) { p[it].lat * 110540 }
        val behold = BooleanArray(p.size)
        behold[0] = true
        behold[p.size - 1] = true
        val stabel = ArrayDeque<Pair<Int, Int>>()
        stabel.addLast(0 to p.size - 1)
        while (stabel.isNotEmpty()) {
            val (a, b) = stabel.removeLast()
            var lengst = 0.0
            var indeks = -1
            val dx = x[b] - x[a]
            val dy = y[b] - y[a]
            val l = sqrt(dx * dx + dy * dy)
            for (i in a + 1 until b) {
                val d = if (l == 0.0) sqrt((x[i] - x[a]) * (x[i] - x[a]) + (y[i] - y[a]) * (y[i] - y[a])) else abs(dy * x[i] - dx * y[i] + x[b] * y[a] - y[b] * x[a]) / l
                if (d > lengst) {
                    lengst = d
                    indeks = i
                }
            }
            if (lengst > toleranse && indeks > 0) {
                behold[indeks] = true
                stabel.addLast(a to indeks)
                stabel.addLast(indeks to b)
            }
        }
        return p.filterIndexed { i, _ -> behold[i] }
    }

    private fun fil(a: Activity, spor: Spor, gpx: ByteArray): Pair<String, Uri> {
        val navn = spor.navn.ifEmpty { "tur" }.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifEmpty { "tur" }
        val mappe = File(a.cacheDir, "del").apply {
            deleteRecursively()
            mkdirs()
        }
        File(mappe, "$navn.gpx").writeBytes(gpx)
        return navn to Uri.parse("content://${a.packageName}.deling/${Uri.encode("$navn.gpx")}")
    }

    fun tilKlokka(a: Activity, spor: Spor, gpx: ByteArray) {
        val (navn, uri) = fil(a, spor, gpx)
        val i = Intent(Intent.ACTION_SEND)
            .setType("application/gpx+xml")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.clipData = ClipData.newRawUri(navn, uri)
        a.startActivity(Intent.createChooser(i, "Send GPX til klokka"))
    }

    fun del(a: Activity, spor: Spor, gpx: ByteArray) {
        val (navn, uri) = fil(a, spor, gpx)
        val lenke = lenke(spor)
        val tekst = "${spor.navn.ifEmpty { "Tur" }}, ${Vis.km(spor.lengde)}\n$lenke"
        val i = Intent(Intent.ACTION_SEND)
            .setType("application/gpx+xml")
            .putExtra(Intent.EXTRA_TEXT, tekst)
            .putExtra(Intent.EXTRA_SUBJECT, spor.navn.ifEmpty { "Tur" })
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.clipData = ClipData.newRawUri(navn, uri)
        a.startActivity(Intent.createChooser(i, "Del turen"))
    }
}

class DelFil : ContentProvider() {
    private fun fil(uri: Uri): File? {
        val navn = uri.lastPathSegment ?: return null
        val mappe = File(context!!.cacheDir, "del").canonicalFile
        val f = File(mappe, navn).canonicalFile
        return if (f.parentFile == mappe && f.isFile) f else null
    }

    override fun onCreate() = true

    override fun getType(uri: Uri) = "application/gpx+xml"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? =
        fil(uri)?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val f = fil(uri) ?: return null
        val kolonner = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(kolonner).apply {
            addRow(kolonner.map { if (it == OpenableColumns.SIZE) f.length() else if (it == OpenableColumns.DISPLAY_NAME) f.name else null }.toTypedArray())
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
