package me.arnee.felles

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.Rect
import android.os.Build
import android.text.InputType
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi

object Deler {

    fun pingvin(context: Context): ImageView {
        val bilde = context.resources.getDrawable(R.drawable.pingvin, context.theme) as BitmapDrawable
        bilde.setFilterBitmap(false)
        val skala = maxOf(1, WaddleUi.dp(context, WaddleTokens.SPACE_12) / bilde.bitmap.width)
        val side = bilde.bitmap.width * skala
        return ImageView(context).apply {
            setImageDrawable(bilde)
            scaleType = ImageView.ScaleType.FIT_XY
            layoutParams = LinearLayout.LayoutParams(side, side)
        }
    }

    fun tittel(activity: Activity, tekst: String, ikon: String? = null, beskrivelse: String? = null, trykk: (() -> Unit)? = null) =
        WaddleUi.row(activity, WaddleTokens.SPACE_3).apply {
            addView(pingvin(activity))
            addView(WaddleUi.text(activity, tekst, WaddleTokens.TEXT_XL, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (ikon != null && trykk != null) {
                val side = WaddleUi.dp(activity, WaddleTokens.SPACE_12)
                val pad = WaddleUi.dp(activity, WaddleTokens.SPACE_3)
                addView(WaddleUi.icon(activity, ikon).apply {
                    layoutParams = LinearLayout.LayoutParams(side, side)
                    setPadding(pad, pad, pad, pad)
                    contentDescription = beskrivelse
                    isClickable = true
                    setOnClickListener { trykk() }
                })
            }
        }

    fun tastatur(activity: Activity, nederst: Boolean = false) {
        val rot = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) ?: return
        val pad = WaddleUi.dp(activity, WaddleTokens.SPACE_4)
        var hadde = false
        rot.setPadding(pad, pad, pad, pad)
        rot.setOnApplyWindowInsetsListener { view, insets ->
            val bunn: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                bunn = maxOf(bars.bottom, ime.bottom)
                view.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bunn)
                val har = ime.bottom > 0
                if (har && !hadde && view is ScrollView) view.post {
                    if (nederst) {
                        view.scrollTo(0, view.getChildAt(0).height)
                    } else {
                        view.findFocus()?.let { fokus ->
                            val r = Rect(0, 0, fokus.width, fokus.height)
                            view.offsetDescendantRectToMyCoords(fokus, r)
                            val synligBunn = view.scrollY + view.height - view.paddingBottom
                            if (r.bottom > synligBunn) view.scrollBy(0, r.bottom - synligBunn + pad)
                        }
                    }
                }
                hadde = har
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(pad + insets.systemWindowInsetLeft, pad + insets.systemWindowInsetTop, pad + insets.systemWindowInsetRight, pad + insets.systemWindowInsetBottom)
            }
            insets
        }
        rot.requestApplyInsets()
        if (nederst && rot is ScrollView) {
            rot.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    rot.viewTreeObserver.removeOnPreDrawListener(this)
                    rot.scrollTo(0, rot.getChildAt(0).height)
                    return true
                }
            })
        }
    }

    fun glider(context: Context, maks: Int, verdi: Int, endret: (Int) -> Unit) = SeekBar(context).apply {
        val p = WaddleUi.palette(context)
        max = maks
        progress = verdi
        minimumHeight = WaddleUi.dp(context, WaddleTokens.SPACE_12)
        progressTintList = ColorStateList.valueOf(p.text)
        progressBackgroundTintList = ColorStateList.valueOf(p.border)
        thumb = GradientDrawable().apply {
            setColor(p.text)
            setSize(WaddleUi.dp(context, WaddleTokens.SPACE_3), WaddleUi.dp(context, WaddleTokens.SPACE_6))
        }
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, verdi: Int, fraBruker: Boolean) {
                if (fraBruker) endret(verdi)
            }

            override fun onStartTrackingTouch(bar: SeekBar) {}

            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
    }

    fun felt(context: Context, hint: String, flerlinje: Boolean = false) = EditText(context).apply {
        val p = WaddleUi.palette(context)
        val pad = WaddleUi.dp(context, WaddleTokens.SPACE_3)
        this.hint = hint
        textSize = WaddleTokens.TEXT_BASE
        typeface = WaddleUi.font(context)
        setTextColor(p.text)
        setHintTextColor(p.muted)
        minHeight = WaddleUi.dp(context, WaddleTokens.SPACE_12)
        setPadding(pad, pad, pad, pad)
        background = WaddleUi.outline(context, p.surface, p.border)
        inputType = if (flerlinje) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        if (!flerlinje) isSingleLine = true
    }

    fun verdi(context: Context, etikett: String, verdi: String, trykk: (() -> Unit)? = null) =
        WaddleUi.row(context, WaddleTokens.SPACE_3).apply {
            val pad = WaddleUi.dp(context, WaddleTokens.SPACE_3)
            setPadding(0, pad, 0, pad)
            minimumHeight = WaddleUi.dp(context, WaddleTokens.SPACE_12)
            addView(WaddleUi.text(context, etikett, muted = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(WaddleUi.text(context, verdi, bold = trykk != null))
            if (trykk != null) {
                isClickable = true
                setOnClickListener { trykk() }
            }
        }

    fun klikkbar(view: android.view.View, handling: () -> Unit) = view.apply {
        isClickable = true
        setOnClickListener { handling() }
    }
}
