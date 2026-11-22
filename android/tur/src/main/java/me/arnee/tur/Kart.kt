package me.arnee.tur

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.OverScroller
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
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
import kotlin.math.roundToInt
import kotlin.math.tan
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi

class Kart(k: Context) : View(k) {
    var kilde = Innst.kart(k)
        set(v) {
            if (field == v) return
            field = v
            invalidate()
        }
    var spor: List<Punkt> = emptyList()
        set(v) {
            field = v
            sporSti.nullstill()
            invalidate()
        }
    var rute: Spor? = null
        set(v) {
            if (field === v) return
            field = v
            ruteSti.nullstill()
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
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fyll = Paint(Paint.ANTI_ALIAS_FLAG)
    private val skrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = WaddleUi.font(k)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, WaddleTokens.TEXT_SM, resources.displayMetrics)
    }
    private val dst = RectF()
    private val src = Rect()
    private val sporSti = Sti()
    private val ruteSti = Sti()
    private val kast = OverScroller(k)
    private var kastX = 0
    private var kastY = 0
    private var animasjon: ValueAnimator? = null

    private val skala = ScaleGestureDetector(k, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            zoomRundt(zoom + log2(d.scaleFactor.toDouble()), d.focusX, d.focusY)
            return true
        }
    })

    private val gester = GestureDetector(k, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            stopp()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            flytt(dx.toDouble(), dy.toDouble())
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (skala.isInProgress) return false
            kastX = 0
            kastY = 0
            kast.fling(0, 0, -vx.roundToInt(), -vy.roundToInt(), Int.MIN_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE)
            postInvalidateOnAnimation()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val fra = zoom
            val til = (floor(zoom) + 1).coerceAtMost(MAKS_ZOOM)
            animasjon = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = WaddleTokens.MOTION_BASE
                addUpdateListener { zoomRundt(fra + (til - fra) * it.animatedFraction, e.x, e.y) }
                start()
            }
            return true
        }
    })

    init {
        contentDescription = "Kart"
    }

    fun posisjon(l: Location?) {
        if (folg && l != null) {
            if (posisjon == null && zoom < 13) zoom = 15.0
            mx = merX(l.longitude)
            my = merY(l.latitude)
        }
        posisjon = l
        invalidate()
    }

    fun sentrer() {
        stopp()
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
        stopp()
        folg = false
        var x0 = 1.0
        var x1 = 0.0
        var y0 = 1.0
        var y1 = 0.0
        punkter.forEach {
            val x = merX(it.lon)
            val y = merY(it.lat)
            x0 = min(x0, x)
            x1 = max(x1, x)
            y0 = min(y0, y)
            y1 = max(y1, y)
        }
        mx = (x0 + x1) / 2
        my = (y0 + y1) / 2
        zoom = (log2(min(width / (max(x1 - x0, 1e-9) * flis), height / (max(y1 - y0, 1e-9) * flis))) - 0.3).coerceIn(3.0, 16.0)
        invalidate()
    }

    fun tilstand() = doubleArrayOf(mx, my, zoom, if (folg) 1.0 else 0.0)

    fun sett(t: DoubleArray?) {
        if (t == null || t.size != 4) return
        mx = t[0]
        my = t[1]
        zoom = t[2]
        folg = t[3] == 1.0
        invalidate()
    }

    private fun stopp() {
        kast.forceFinished(true)
        animasjon?.cancel()
        animasjon = null
    }

    private fun flytt(dx: Double, dy: Double) {
        val v = verden()
        mx = (mx + dx / v).mod(1.0)
        my = (my + dy / v).coerceIn(0.0, 1.0)
        if (folg) {
            folg = false
            flyttet?.invoke()
        }
        invalidate()
    }

    override fun computeScroll() {
        if (!kast.computeScrollOffset()) return
        flytt((kast.currX - kastX).toDouble(), (kast.currY - kastY).toDouble())
        kastX = kast.currX
        kastY = kast.currY
        postInvalidateOnAnimation()
    }

    private fun verden() = flis * 2.0.pow(zoom)

    private fun zoomRundt(ny: Double, fx: Float, fy: Float) {
        val z = ny.coerceIn(MIN_ZOOM, MAKS_ZOOM)
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
        mx = (wx - (fx - width / 2) / v2).mod(1.0)
        my = (wy - (fy - height / 2) / v2).coerceIn(0.0, 1.0)
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        skala.onTouchEvent(e)
        gester.onTouchEvent(e)
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        synlige.add(this)
    }

    override fun onDetachedFromWindow() {
        synlige.remove(this)
        stopp()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(p.surface)
        val kilde = kilde
        val z = floor(zoom + 0.3).toInt().coerceIn(0, maks(kilde))
        val antall = 1 shl z
        val side = flis * 2.0.pow(zoom - z)
        val v = verden()
        val sx = mx * v - width / 2.0
        val sy = my * v - height / 2.0
        val na = System.currentTimeMillis()
        for (ty in floor(sy / side).toInt()..floor((sy + height) / side).toInt()) {
            if (ty < 0 || ty >= antall) continue
            for (tx in floor(sx / side).toInt()..floor((sx + width) / side).toInt()) {
                val left = (tx * side - sx).toFloat()
                val top = (ty * side - sy).toFloat()
                dst.set(left, top, left + side.toFloat() + 1, top + side.toFloat() + 1)
                tegnFlis(c, kilde, z, tx.mod(antall), ty, na)
            }
        }
        val x = { lon: Double -> (merX(lon) * v - sx).toFloat() }
        val y = { lat: Double -> (merY(lat) * v - sy).toFloat() }
        rute?.let { r ->
            strek.color = WaddleTokens.Light.TEXT
            strek.alpha = 170
            ruteSti.tegn(c, r.punkter, v, sx, sy)
            fyll.color = WaddleTokens.Light.TEXT
            skrift.color = WaddleTokens.Light.TEXT
            r.merker.forEach { m ->
                val ax = x(m.lon)
                val ay = y(m.lat)
                if (ax < -width || ax > width * 2 || ay < -height || ay > height * 2) return@forEach
                c.drawRect(ax - tykk * 1.5f, ay - tykk * 1.5f, ax + tykk * 1.5f, ay + tykk * 1.5f, fyll)
                if (m.navn.isNotEmpty()) c.drawText(m.navn, ax + tykk * 3, ay + skrift.textSize / 3, skrift)
            }
        }
        if (spor.size > 1) {
            strek.color = p.accent
            strek.alpha = 255
            sporSti.tegn(c, spor, v, sx, sy)
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
        fyll.color = WaddleTokens.Light.SURFACE
        fyll.alpha = 200
        c.drawRect(0f, height - skrift.textSize - pad * 2, skrift.measureText(tekst) + pad * 2, height.toFloat(), fyll)
        skrift.color = WaddleTokens.Light.TEXT
        c.drawText(tekst, pad, height - pad - skrift.descent(), skrift)
    }

    private inner class Sti {
        private val sti = Path()
        private var antall = 0
        private var ox = 0.0
        private var oy = 0.0
        private var seg = -1

        fun nullstill() {
            sti.reset()
            antall = 0
            seg = -1
        }

        fun tegn(c: Canvas, punkter: List<Punkt>, v: Double, sx: Double, sy: Double) {
            if (punkter.size < antall) nullstill()
            if (antall == 0 && punkter.isNotEmpty()) {
                ox = merX(punkter[0].lon)
                oy = merY(punkter[0].lat)
            }
            for (i in antall until punkter.size) {
                val q = punkter[i]
                val x = ((merX(q.lon) - ox) * GRUNN).toFloat()
                val y = ((merY(q.lat) - oy) * GRUNN).toFloat()
                if (q.seg != seg) sti.moveTo(x, y) else sti.lineTo(x, y)
                seg = q.seg
            }
            antall = punkter.size
            val s = (v / GRUNN).toFloat()
            strek.strokeWidth = tykk * 1.5f / s
            c.save()
            c.translate((ox * v - sx).toFloat(), (oy * v - sy).toFloat())
            c.scale(s, s)
            c.drawPath(sti, strek)
            c.restore()
        }
    }

    private fun tegnFlis(c: Canvas, kilde: String, z: Int, x: Int, y: Int, na: Long) {
        hent(kilde, z, x, y, na)?.let {
            c.drawBitmap(it, null, dst, bilde)
            return
        }
        for (n in 1..6) {
            if (z - n < 0) return
            val b = minne.get(nokkel(kilde, z - n, x shr n, y shr n)) ?: continue
            val del = b.width shr n
            val ox = (x and ((1 shl n) - 1)) * del
            val oy = (y and ((1 shl n) - 1)) * del
            src.set(ox, oy, ox + del, oy + del)
            c.drawBitmap(b, src, dst, bilde)
            return
        }
    }

    private fun hent(kilde: String, z: Int, x: Int, y: Int, na: Long): Bitmap? {
        val n = nokkel(kilde, z, x, y)
        minne.get(n)?.let { return it }
        onsket[n] = na
        if ((feil[n] ?: 0) > na || laster.putIfAbsent(n, true) != null) return null
        val fil = File(context.cacheDir, "fliser/$kilde/$z/$x/$y.png")
        disk.execute {
            val b = if (fil.exists()) BitmapFactory.decodeFile(fil.path, valg) else null
            if (b != null) ferdig(n, b) else nett.execute { last(n, kilde, z, x, y, fil) }
        }
        return null
    }

    companion object {
        private const val MIN_ZOOM = 2.0
        private const val MAKS_ZOOM = 19.0
        private const val GRUNN = 65536.0 * 256
        private val synlige = HashSet<Kart>()
        private val hoved = Handler(Looper.getMainLooper())
        private val valg = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        private val minne = object : LruCache<Long, Bitmap>((Runtime.getRuntime().maxMemory() / 6 / 1024).toInt()) {
            override fun sizeOf(key: Long, value: Bitmap) = value.byteCount / 1024
        }
        private val laster = ConcurrentHashMap<Long, Boolean>()
        private val onsket = ConcurrentHashMap<Long, Long>()
        private val feil = ConcurrentHashMap<Long, Long>()
        private val disk = Executors.newSingleThreadExecutor()
        private val nett = ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS, object : LinkedBlockingDeque<Runnable>() {
            override fun offer(e: Runnable) = offerFirst(e)
        })

        private fun nokkel(kilde: String, z: Int, x: Int, y: Int) =
            ((if (kilde == "osm") 1L else 0L) shl 60) or (z.toLong() shl 52) or (x.toLong() shl 26) or y.toLong()

        private fun last(n: Long, kilde: String, z: Int, x: Int, y: Int, fil: File) {
            if (System.currentTimeMillis() - (onsket[n] ?: 0) > 4000) {
                laster.remove(n)
                tegnPaNytt()
                return
            }
            var b: Bitmap? = null
            var vent = 30000L
            runCatching {
                val t = URL(adresse(kilde, z, x, y)).openConnection() as HttpURLConnection
                t.setRequestProperty("User-Agent", "Tur/1 (Android)")
                t.connectTimeout = 10000
                t.readTimeout = 15000
                when (t.responseCode) {
                    200 -> {
                        val data = t.inputStream.use { it.readBytes() }
                        b = BitmapFactory.decodeByteArray(data, 0, data.size, valg)
                        if (b != null) {
                            fil.parentFile?.mkdirs()
                            fil.writeBytes(data)
                        }
                    }
                    in 400..499 -> {
                        vent = 3600000L
                        t.errorStream?.close()
                    }
                    else -> t.errorStream?.close()
                }
            }
            val bilde = b
            if (bilde == null) {
                feil[n] = System.currentTimeMillis() + vent
                laster.remove(n)
            } else ferdig(n, bilde)
        }

        private fun ferdig(n: Long, b: Bitmap) {
            minne.put(n, b)
            laster.remove(n)
            tegnPaNytt()
        }

        private fun tegnPaNytt() {
            hoved.post { synlige.forEach { it.invalidate() } }
        }

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
