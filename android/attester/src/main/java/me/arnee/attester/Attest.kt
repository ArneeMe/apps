package me.arnee.attester

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object AttestTokens {
    const val PAPIR = 0xFFFAF8F3.toInt()
    const val FLATE = 0xFFFFFFFF.toInt()
    const val BLEKK = 0xFF1B1A17.toInt()
    const val DEMPET = 0xFF4A4740.toInt()
    const val SVAK = 0xFF8A8477.toInt()
    const val LINJE = 0xFFE5E0D5.toInt()
    const val KANT = 0xFFCFC8B8.toInt()
    const val AKSENT = 0xFF1F5C44.toInt()
    const val PAA_AKSENT = 0xFFFFFFFF.toInt()

    const val AVSTAND_1 = 4
    const val AVSTAND_2 = 8
    const val AVSTAND_3 = 12
    const val AVSTAND_4 = 16
    const val AVSTAND_6 = 24

    const val TEKST_LITEN = 12.5f
    const val TEKST = 14.5f
    const val TEKST_INGRESS = 17f
    const val TEKST_OVERSKRIFT = 22f
    const val TEKST_TITTEL = 30f

    const val FELT_HOYDE = 44
    const val KNAPP_HOYDE = 44
    const val RADIUS = 2
    const val STREK = 1
}

object Attest {
    private val serif = Typeface.create("serif", Typeface.NORMAL)
    private val sans = Typeface.create("sans-serif", Typeface.NORMAL)
    private val sansFet = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val mono = Typeface.MONOSPACE

    fun dp(context: Context, verdi: Int) = (verdi * context.resources.displayMetrics.density).toInt()

    fun tekst(context: Context, verdi: CharSequence, storrelse: Float = AttestTokens.TEKST, farge: Int = AttestTokens.BLEKK, fet: Boolean = false) =
        TextView(context).apply {
            text = verdi
            textSize = storrelse
            typeface = if (fet) sansFet else sans
            setTextColor(farge)
            setLineSpacing(0f, 1.35f)
        }

    fun dempet(context: Context, verdi: CharSequence) = tekst(context, verdi, farge = AttestTokens.DEMPET)

    fun merke(context: Context, verdi: CharSequence) = tekst(context, verdi, AttestTokens.TEKST_LITEN, AttestTokens.SVAK).apply { typeface = mono }

    fun overskrift(context: Context, verdi: CharSequence, storrelse: Float = AttestTokens.TEKST_OVERSKRIFT) =
        tekst(context, verdi, storrelse).apply { typeface = serif }

    private fun mellomrom(context: Context, verdi: Int, loddrett: Boolean) = GradientDrawable().apply {
        if (loddrett) setSize(0, dp(context, verdi)) else setSize(dp(context, verdi), 0)
    }

    fun kolonne(context: Context, avstand: Int = AttestTokens.AVSTAND_3) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        dividerDrawable = mellomrom(context, avstand, true)
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
    }

    fun rad(context: Context, avstand: Int = AttestTokens.AVSTAND_2) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        dividerDrawable = mellomrom(context, avstand, false)
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
    }

    private fun ramme(context: Context, fyll: Int, kant: Int) = GradientDrawable().apply {
        setColor(fyll)
        setStroke(dp(context, AttestTokens.STREK), kant)
        cornerRadius = dp(context, AttestTokens.RADIUS).toFloat()
    }

    fun boks(context: Context) = kolonne(context, 0).apply {
        val pad = dp(context, AttestTokens.AVSTAND_4)
        background = ramme(context, AttestTokens.FLATE, AttestTokens.KANT)
        setPadding(pad, 0, pad, 0)
        dividerDrawable = GradientDrawable().apply {
            setColor(AttestTokens.LINJE)
            setSize(0, dp(context, AttestTokens.STREK))
        }
    }

    fun knapp(context: Context, verdi: String, primaer: Boolean = false, klikk: () -> Unit) =
        Button(context).apply {
            text = verdi
            isAllCaps = false
            stateListAnimator = null
            textSize = AttestTokens.TEKST
            typeface = sansFet
            minHeight = dp(context, AttestTokens.KNAPP_HOYDE)
            minimumHeight = dp(context, AttestTokens.KNAPP_HOYDE)
            setPadding(dp(context, AttestTokens.AVSTAND_4), 0, dp(context, AttestTokens.AVSTAND_4), 0)
            setTextColor(if (primaer) AttestTokens.PAA_AKSENT else AttestTokens.BLEKK)
            background = ramme(context, if (primaer) AttestTokens.AKSENT else AttestTokens.FLATE, if (primaer) AttestTokens.AKSENT else AttestTokens.KANT)
            setOnClickListener { klikk() }
        }

    fun felt(context: Context, hint: String, type: Int) = EditText(context).apply {
        val pad = dp(context, AttestTokens.AVSTAND_3)
        this.hint = hint
        inputType = type
        isSingleLine = true
        textSize = AttestTokens.TEKST
        typeface = sans
        setTextColor(AttestTokens.BLEKK)
        setHintTextColor(AttestTokens.SVAK)
        minHeight = dp(context, AttestTokens.FELT_HOYDE)
        setPadding(pad, 0, pad, 0)
        background = ramme(context, AttestTokens.FLATE, AttestTokens.KANT)
    }

    fun epost(context: Context) = felt(context, "E-post", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)

    fun passord(context: Context) = felt(context, "Passord", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)

    fun linje(context: Context, tittel: CharSequence, detalj: CharSequence? = null, klikk: (() -> Unit)? = null) =
        kolonne(context, AttestTokens.AVSTAND_1).apply {
            val pad = dp(context, AttestTokens.AVSTAND_3)
            setPadding(0, pad, 0, pad)
            addView(tekst(context, tittel, fet = klikk != null))
            if (detalj != null) addView(merke(context, detalj))
            if (klikk != null) {
                isClickable = true
                setOnClickListener { klikk() }
            }
        }

    fun skjerm(activity: Activity, innhold: View) {
        val pad = dp(activity, AttestTokens.AVSTAND_4)
        val rull = ScrollView(activity).apply {
            setBackgroundColor(AttestTokens.PAPIR)
            addView(innhold)
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    val ime = insets.getInsets(WindowInsets.Type.ime())
                    view.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + maxOf(bars.bottom, ime.bottom))
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(pad + insets.systemWindowInsetLeft, pad + insets.systemWindowInsetTop, pad + insets.systemWindowInsetRight, pad + insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        activity.setContentView(rull)
        activity.window.decorView.setBackgroundColor(AttestTokens.PAPIR)
        if (Build.VERSION.SDK_INT >= 30) {
            val lys = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            activity.window.insetsController?.setSystemBarsAppearance(lys, lys)
        }
        rull.requestApplyInsets()
    }
}
