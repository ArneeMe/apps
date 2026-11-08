package me.arnee.design

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object WaddleUi {
    class Palette(val bg: Int, val surface: Int, val border: Int, val text: Int, val muted: Int, val accent: Int, val onAccent: Int)

    fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun palette(context: Context): Palette =
        if (isDark(context)) Palette(WaddleTokens.Dark.BG, WaddleTokens.Dark.SURFACE, WaddleTokens.Dark.BORDER, WaddleTokens.Dark.TEXT, WaddleTokens.Dark.MUTED, WaddleTokens.Dark.ACCENT, WaddleTokens.Dark.ON_ACCENT) else Palette(WaddleTokens.Light.BG, WaddleTokens.Light.SURFACE, WaddleTokens.Light.BORDER, WaddleTokens.Light.TEXT, WaddleTokens.Light.MUTED, WaddleTokens.Light.ACCENT, WaddleTokens.Light.ON_ACCENT)

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    fun font(context: Context, bold: Boolean = false): Typeface {
        val id = context.resources.getIdentifier(if (bold) "waddle_mono_bold" else "waddle_mono", "font", context.packageName)
        return if (id != 0) context.resources.getFont(id) else Typeface.MONOSPACE
    }

    fun text(context: Context, value: CharSequence, size: Float = WaddleTokens.TEXT_BASE, bold: Boolean = false, muted: Boolean = false) =
        TextView(context).apply {
            text = value
            textSize = size
            typeface = font(context, bold)
            setTextColor(if (muted) palette(context).muted else palette(context).text)
        }

    private fun gap(context: Context, value: Int, vertical: Boolean) = GradientDrawable().apply {
        if (vertical) setSize(0, dp(context, value)) else setSize(dp(context, value), 0)
    }

    fun column(context: Context, spacing: Int = WaddleTokens.SPACE_3) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        dividerDrawable = gap(context, spacing, true)
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
    }

    fun row(context: Context, spacing: Int = WaddleTokens.SPACE_2) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        dividerDrawable = gap(context, spacing, false)
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
    }

    fun outline(context: Context, fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(context, WaddleTokens.BORDER_WIDTH), stroke)
        cornerRadius = dp(context, WaddleTokens.RADIUS_SM).toFloat()
    }

    fun box(context: Context) = column(context).apply {
        val p = palette(context)
        val pad = dp(context, WaddleTokens.SPACE_4)
        background = outline(context, p.surface, p.border)
        setPadding(pad, pad, pad, pad)
    }

    fun button(context: Context, label: String, primary: Boolean = false, onClick: (View) -> Unit) =
        Button(context).apply {
            val p = palette(context)
            text = label
            isAllCaps = false
            stateListAnimator = null
            textSize = WaddleTokens.TEXT_BASE
            typeface = font(context, primary)
            minHeight = dp(context, 48)
            minimumHeight = dp(context, 48)
            setPadding(dp(context, WaddleTokens.SPACE_4), 0, dp(context, WaddleTokens.SPACE_4), 0)
            setTextColor(if (primary) p.onAccent else p.text)
            background = outline(context, if (primary) p.accent else p.bg, if (primary) p.accent else p.border)
            setOnClickListener(onClick)
        }

    fun icon(context: Context, name: String, size: Int = 24, color: Int = palette(context).text) =
        ImageView(context).apply {
            val id = context.resources.getIdentifier("ic_" + name.replace('-', '_'), "drawable", context.packageName)
            if (id != 0) setImageResource(id)
            imageTintList = ColorStateList.valueOf(color)
            layoutParams = LinearLayout.LayoutParams(dp(context, size), dp(context, size))
        }

    fun divider(context: Context) = View(context).apply {
        setBackgroundColor(palette(context).border)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, WaddleTokens.BORDER_WIDTH))
    }

    fun listRow(context: Context, title: CharSequence, detail: CharSequence? = null, iconName: String? = null) =
        row(context, WaddleTokens.SPACE_3).apply {
            val pad = dp(context, WaddleTokens.SPACE_3)
            setPadding(0, pad, 0, pad)
            if (iconName != null) addView(icon(context, iconName))
            val lines = column(context, WaddleTokens.SPACE_1)
            lines.addView(text(context, title))
            if (detail != null) lines.addView(text(context, detail, WaddleTokens.TEXT_SM, muted = true))
            addView(lines, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

    fun screen(activity: Activity, content: View) {
        val p = palette(activity)
        val pad = dp(activity, WaddleTokens.SPACE_4)
        val scroll = ScrollView(activity).apply {
            setBackgroundColor(p.bg)
            addView(content)
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    view.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(pad + insets.systemWindowInsetLeft, pad + insets.systemWindowInsetTop, pad + insets.systemWindowInsetRight, pad + insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        activity.setContentView(scroll)
        activity.window.decorView.setBackgroundColor(p.bg)
        if (Build.VERSION.SDK_INT >= 30) {
            val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            activity.window.insetsController?.setSystemBarsAppearance(if (isDark(activity)) 0 else light, light)
        }
    }
}
