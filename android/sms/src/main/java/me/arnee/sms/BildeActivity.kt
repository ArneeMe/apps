package me.arnee.sms

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi

@SuppressLint("ClickableViewAccessibility")
class ZoomBilde(context: Context, private val bilde: Bitmap) : ImageView(context) {
    private val matrise = Matrix()
    private var skala = 1f
    private var grunn = 1f
    private var forrigeX = 0f
    private var forrigeY = 0f

    private val zoom = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val ny = (skala * d.scaleFactor).coerceIn(1f, 6f)
            val f = ny / skala
            skala = ny
            matrise.postScale(f, f, d.focusX, d.focusY)
            hold()
            return true
        }
    })

    private val dobbel = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (skala > 1.01f) tilpass() else {
                skala = 2.5f
                matrise.postScale(2.5f, 2.5f, e.x, e.y)
                hold()
            }
            return true
        }
    })

    init {
        setImageBitmap(bilde)
        scaleType = ScaleType.MATRIX
        setOnTouchListener { _, e ->
            zoom.onTouchEvent(e)
            dobbel.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    forrigeX = e.x
                    forrigeY = e.y
                }
                MotionEvent.ACTION_MOVE -> if (!zoom.isInProgress && e.pointerCount == 1 && skala > 1.01f) {
                    matrise.postTranslate(e.x - forrigeX, e.y - forrigeY)
                    hold()
                    forrigeX = e.x
                    forrigeY = e.y
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    forrigeX = e.x
                    forrigeY = e.y
                }
            }
            true
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        tilpass()
    }

    private fun tilpass() {
        if (width == 0) return
        grunn = minOf(width.toFloat() / bilde.width, height.toFloat() / bilde.height)
        skala = 1f
        matrise.reset()
        matrise.postScale(grunn, grunn)
        matrise.postTranslate((width - bilde.width * grunn) / 2, (height - bilde.height * grunn) / 2)
        imageMatrix = matrise
    }

    private fun hold() {
        val v = FloatArray(9)
        matrise.getValues(v)
        val b = bilde.width * v[Matrix.MSCALE_X]
        val h = bilde.height * v[Matrix.MSCALE_Y]
        val x = v[Matrix.MTRANS_X]
        val y = v[Matrix.MTRANS_Y]
        val dx = if (b <= width) (width - b) / 2 - x else (x.coerceIn(width - b, 0f) - x)
        val dy = if (h <= height) (height - h) / 2 - y else (y.coerceIn(height - h, 0f) - y)
        matrise.postTranslate(dx, dy)
        imageMatrix = matrise
    }
}

class BildeActivity : Activity() {
    private lateinit var kilde: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kilde = intent.data ?: return finish()
        val type = contentResolver.getType(kilde) ?: "image/jpeg"
        val grenser = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(kilde)?.use { BitmapFactory.decodeStream(it, null, grenser) }
        var skala = 1
        while (maxOf(grenser.outWidth, grenser.outHeight) / (skala * 2) >= 2048) skala *= 2
        val bilde = contentResolver.openInputStream(kilde)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = skala }) }
        if (bilde == null) {
            Toast.makeText(this, "Kunne ikke åpne bildet.", Toast.LENGTH_LONG).show()
            return finish()
        }
        val ui = WaddleUi
        val p = ui.palette(this)
        val pad = ui.dp(this, WaddleTokens.SPACE_4)
        val rot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.bg)
            addView(FrameLayout(this@BildeActivity).apply {
                addView(ZoomBilde(this@BildeActivity, bilde), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(ui.row(this@BildeActivity).apply {
                setPadding(pad, pad, pad, pad)
                addView(ui.button(this@BildeActivity, "Lagre", primary = true) { lagre(type) })
                addView(ui.button(this@BildeActivity, "Del") { del(type) })
                addView(ui.button(this@BildeActivity, "Lukk") { finish() })
            })
            setOnApplyWindowInsetsListener { v, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                }
                insets
            }
        }
        setContentView(rot)
        window.decorView.setBackgroundColor(p.bg)
    }

    private fun endelse(type: String) = when (type) {
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        else -> "jpg"
    }

    private fun lagre(type: String) {
        if (Build.VERSION.SDK_INT < 29) {
            Toast.makeText(this, "Lagring krever Android 10 eller nyere.", Toast.LENGTH_LONG).show()
            return
        }
        val ok = runCatching {
            val verdier = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "melding-${System.currentTimeMillis()}.${endelse(type)}")
                put(MediaStore.Images.Media.MIME_TYPE, type)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Meldinger")
            }
            val ut = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, verdier)!!
            contentResolver.openOutputStream(ut)!!.use { o -> contentResolver.openInputStream(kilde)!!.use { it.copyTo(o) } }
        }.isSuccess
        Toast.makeText(this, if (ok) "Lagret i Bilder/Meldinger" else "Fikk ikke lagret bildet.", Toast.LENGTH_LONG).show()
    }

    private fun del(type: String) {
        val navn = "del-${System.currentTimeMillis()}.${endelse(type)}"
        Filer.fil(this, navn).outputStream().use { o -> contentResolver.openInputStream(kilde)!!.use { it.copyTo(o) } }
        val uri = Filer.uri(this, navn)
        val send = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("bilde", uri)
        startActivity(Intent.createChooser(send, "Del bilde"))
    }
}
