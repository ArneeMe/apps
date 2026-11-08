package me.arnee.felles

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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

    fun tittel(activity: Activity, tekst: String) = WaddleUi.row(activity, WaddleTokens.SPACE_3).apply {
        addView(pingvin(activity))
        addView(WaddleUi.text(activity, tekst, WaddleTokens.TEXT_XL, bold = true))
    }

    fun glider(context: Context, maks: Int, verdi: Int, endret: (Int) -> Unit) = SeekBar(context).apply {
        val p = WaddleUi.palette(context)
        max = maks
        progress = verdi
        minimumHeight = WaddleUi.dp(context, WaddleTokens.SPACE_12)
        progressTintList = ColorStateList.valueOf(p.text)
        progressBackgroundTintList = ColorStateList.valueOf(p.border)
        thumb = GradientDrawable().apply {
            setColor(p.accent)
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

    fun klikkbar(view: android.view.View, handling: () -> Unit) = view.apply {
        isClickable = true
        setOnClickListener { handling() }
    }
}
