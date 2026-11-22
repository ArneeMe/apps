package me.arnee.tur

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.location.Location
import android.util.LruCache
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi

class Kart(k: Context) : View(k) {
    var kilde = Innst.kart(k)
        set(v) {
            field = v
            invalidate()
        }
    var spor: List<Punkt> = emptyList()
    var rute: Spor? = null
        set(v) {
            field = v
            invalidate()
        }
    var flyttet: (() -> Unit)? = null
    var folg = true
        private set
    private var posisjon: Location? = null
    private var mx = merX(12.0)
    private var my = merY(64.0)
    private var zoom = 4.0
    private val flis = WaddleUi.dp(k, WaddleTokens.SPACE_12) * 4.0
    private val p = WaddleUi.palette(k)
    private val tykk = WaddleUi.dp(k, WaddleTokens.SPACE_1).toFloat()
    private val bilde = Paint(Paint.FILTER_BITMAP_FLAG)
    private val strek = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = tykk
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fyll = Paint(Paint.ANTI_ALIAS_FLAG)
    private val skrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = WaddleUi.font(k)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, WaddleTokens.TEXT_SM, resources.displayMetrics)
    }

    private val skala = ScaleGestureDetector(k, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            zoomRundt(zoom + log2(d.scaleFactor.toDouble()), d.focusX, d.focusY)
            return true
        }
    })

    private val gester = GestureDetector(k, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            val v = verden()
            mx = (mx + dx / v).mod(1.0)
            my = (my + dy / v).coerceIn(0.0, 1.0)
            slipp()
            invalidate()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            zoomRundt(zoom + 1, e.x, e.y)
            return true
        }
    })

    init {
        contentDescription = "Kart"
    }

    fun posisjon(l: Location?) {
        posisjon = l
        if (folg && l != null) {
            mx = merX(l.longitude)
            my = merY(l.latitude)
        }
        invalidate()
    }

    fun sentrer() {
        folg = true
        zoom = max(zoom, 15.0)
        posisjon(posisjon)
    }

    fun vis(punkter: List<Punkt>) {
        if (punkter.isEmpty()) return
        if (width == 0) {
            post { vis(punkter) }
            return
        }
        folg = false
        val xs = punkter.map { merX(it.lon) }
        val ys = punkter.map { merY(it.lat) }
        mx = (xs.min() + xs.max()) / 2
        my = (ys.min() + ys.max()) / 2
        val b = max(xs.max() - xs.min(), 1e-9)
        val h = max(ys.max() - ys.min(), 1e-9)
        zoom = (log2(min(width / (b * flis), height / (h * flis))) - 0.2).coerceIn(3.0, 16.0)
        invalidate()
    }

    private fun slipp() {
        if (!folg) return
        folg = false
        flyttet?.invoke()
    }

    private fun verden() = flis * 2.0.pow(zoom)

    private fun zoomRundt(ny: Double, fx: Float, fy: Float) {
        val z = ny.coerceIn(2.0, 19.0)
        if (folg) {
            zoom = z
            invalidate()
            return
        }
        val v = verden()
        val wx = mx + (fx - width / 2) / v
        val wy = my + (fy - height / 2) / v
        zoom = z
        val v2 = verden()
        mx = wx - (fx - width / 2) / v2
        my = wy - (fy - height / 2) / v2
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        skala.onTouchEvent(e)
        gester.onTouchEvent(e)
        return true
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(p.surface)
        val z = floor(zoom).toInt().coerceIn(0, maks(kilde))
        val antall = 1 shl z
        val side = flis * 2.0.pow(zoom - z)
        val v = verden()
        val sx = mx * v - width / 2.0
        val sy = my * v - height / 2.0
        val dst = RectF()
        for (ty in floor(sy / side).toInt()..floor((sy + height) / side).toInt()) {
            if (ty < 0 || ty >= antall) continue
            for (tx in floor(sx / side).toInt()..floor((sx + width) / side).toInt()) {
                val left = (tx * side - sx).toFloat()
                val top = (ty * side - sy).toFloat()
                dst.set(left, top, left + side.toFloat() + 1, top + side.toFloat() + 1)
                tegnFlis(c, z, tx.mod(antall), ty, dst)
            }
        }
        val x = { lon: Double -> (merX(lon) * v - sx).toFloat() }
        val y = { lat: Double -> (merY(lat) * v - sy).toFloat() }
        rute?.let { r ->
            strek.color = WaddleTokens.Light.TEXT
            strek.alpha = 170
            strek.strokeWidth = tykk * 1.5f
            c.drawPath(sti(r.punkter, x, y), strek)
            fyll.color = WaddleTokens.Light.TEXT
            skrift.color = WaddleTokens.Light.TEXT
            r.merker.forEach { m ->
                val ax = x(m.lon)
                val ay = y(m.lat)
                c.drawRect(ax - tykk * 1.5f, ay - tykk * 1.5f, ax + tykk * 1.5f, ay + tykk * 1.5f, fyll)
                if (m.navn.isNotEmpty()) c.drawText(m.navn, ax + tykk * 3, ay + skrift.textSize / 3, skrift)
            }
        }
        if (spor.size > 1) {
            strek.color = p.accent
            strek.alpha = 255
            strek.strokeWidth = tykk * 1.5f
            c.drawPath(sti(spor, x, y), strek)
        }
        posisjon?.let { l ->
            val px = x(l.longitude)
            val py = y(l.latitude)
            if (l.hasAccuracy()) {
                fyll.color = p.accent
                fyll.alpha = 40
                c.drawCircle(px, py, (l.accuracy / (cos(l.latitude * PI / 180) * 40075016.686) * v).toFloat(), fyll)
            }
            fyll.color = WaddleTokens.Light.SURFACE
            fyll.alpha = 255
            c.drawCircle(px, py, tykk * 3, fyll)
            fyll.color = p.accent
            c.drawCircle(px, py, tykk * 2, fyll)
        }
        val tekst = if (kilde == "osm") "© OpenStreetMap" else "© Kartverket"
        val pad = tykk * 2
        val bredde = skrift.measureText(tekst)
        fyll.color = WaddleTokens.Light.SURFACE
        fyll.alpha = 200
        c.drawRect(0f, height - skrift.textSize - pad * 2, bredde + pad * 2, height.toFloat(), fyll)
        skrift.color = WaddleTokens.Light.TEXT
        c.drawText(tekst, pad, height - pad - skrift.descent(), skrift)
    }

    private fun sti(punkter: List<Punkt>, x: (Double) -> Float, y: (Double) -> Float) = Path().apply {
        var seg = -1
        punkter.forEach { q ->
            if (q.seg != seg) moveTo(x(q.lon), y(q.lat)) else lineTo(x(q.lon), y(q.lat))
            seg = q.seg
        }
    }

    private fun tegnFlis(c: Canvas, z: Int, x: Int, y: Int, dst: RectF) {
        hent(z, x, y)?.let {
            c.drawBitmap(it, null, dst, bilde)
            return
        }
        for (n in 1..5) {
            if (z - n < 0) return
            val b = minne.get("$kilde/${z - n}/${x shr n}/${y shr n}") ?: continue
            val del = b.width shr n
            val ox = (x and ((1 shl n) - 1)) * del
            val oy = (y and ((1 shl n) - 1)) * del
            c.drawBitmap(b, Rect(ox, oy, ox + del, oy + del), dst, bilde)
            return
        }
    }

    private fun hent(z: Int, x: Int, y: Int): Bitmap? {
        val kilde = kilde
        val nokkel = "$kilde/$z/$x/$y"
        minne.get(nokkel)?.let { return it }
        synchronized(laster) {
            if (nokkel in laster || (feil[nokkel] ?: 0) > System.currentTimeMillis()) return null
            laster.add(nokkel)
        }
        val fil = File(context.cacheDir, "fliser/$nokkel.png")
        ko.execute {
            var b = if (fil.exists()) BitmapFactory.decodeFile(fil.path) else null
            if (b == null) runCatching {
                val tilkobling = URL(adresse(kilde, z, x, y)).openConnection() as HttpURLConnection
                tilkobling.setRequestProperty("User-Agent", "Tur/1 (Android)")
                tilkobling.connectTimeout = 10000
                tilkobling.readTimeout = 15000
                if (tilkobling.responseCode == 200) {
                    val data = tilkobling.inputStream.use { it.readBytes() }
                    b = BitmapFactory.decodeByteArray(data, 0, data.size)
                    if (b != null) {
                        fil.parentFile?.mkdirs()
                        fil.writeBytes(data)
                    }
                }
                tilkobling.disconnect()
            }
            synchronized(laster) {
                laster.remove(nokkel)
                if (b == null) feil[nokkel] = System.currentTimeMillis() + 30000
            }
            b?.let {
                minne.put(nokkel, it)
                postInvalidate()
            }
        }
        return null
    }

    companion object {
        private val minne = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
        }
        private val laster = HashSet<String>()
        private val feil = HashMap<String, Long>()
        private val ko = ThreadPoolExecutor(3, 3, 0, TimeUnit.MILLISECONDS, object : LinkedBlockingDeque<Runnable>() {
            override fun offer(e: Runnable) = offerFirst(e)
        })

        fun merX(lon: Double) = (lon + 180) / 360

        fun merY(lat: Double): Double {
            val r = lat.coerceIn(-85.0, 85.0) * PI / 180
            return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2
        }

        fun maks(kilde: String) = if (kilde == "osm") 19 else 18

        fun adresse(kilde: String, z: Int, x: Int, y: Int) =
            if (kilde == "osm") "https://tile.openstreetmap.org/$z/$x/$y.png"
            else "https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/$z/$y/$x.png"

        fun buffer(k: Context) = File(k.cacheDir, "fliser")
    }
}
