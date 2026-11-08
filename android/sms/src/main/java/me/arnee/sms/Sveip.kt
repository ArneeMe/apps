package me.arnee.sms

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.abs
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class Sveip(
    context: Context,
    private val forgrunn: View,
    hoyreTekst: String,
    venstreTekst: String,
    private val hoyre: () -> Unit,
    private val venstre: () -> Unit,
    private val klikk: () -> Unit,
) : FrameLayout(context) {
    private val terskel = ViewConfiguration.get(context).scaledTouchSlop
    private var start = 0f
    private var startY = 0f
    private var drar = false

    init {
        val p = WaddleUi.palette(context)
        val pad = WaddleUi.dp(context, WaddleTokens.SPACE_4)
        val bak = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(p.border)
            setPadding(pad, 0, pad, 0)
            addView(WaddleUi.text(context, hoyreTekst, bold = true), LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(WaddleUi.text(context, venstreTekst, bold = true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(bak, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        forgrunn.setBackgroundColor(p.surface)
        addView(forgrunn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        forgrunn.setOnTouchListener { _, e -> beroring(e) }
    }

    private fun beroring(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                start = e.rawX
                startY = e.rawY
                drar = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - start
                if (!drar && abs(dx) > terskel && abs(dx) > abs(e.rawY - startY)) {
                    drar = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (drar) forgrunn.translationX = dx
            }
            MotionEvent.ACTION_UP -> {
                val dx = e.rawX - start
                val grense = width * 0.35f
                when {
                    drar && dx > grense -> slipp(width.toFloat(), hoyre)
                    drar && dx < -grense -> slipp(-width.toFloat(), venstre)
                    drar -> tilbake()
                    else -> klikk()
                }
                drar = false
            }
            MotionEvent.ACTION_CANCEL -> {
                tilbake()
                drar = false
            }
        }
        return true
    }

    private fun slipp(mal: Float, handling: () -> Unit) {
        forgrunn.animate().translationX(mal).setDuration(WaddleTokens.MOTION_FAST).withEndAction { handling() }.start()
    }

    fun tilbake() {
        forgrunn.animate().translationX(0f).setDuration(WaddleTokens.MOTION_FAST).start()
    }
}
