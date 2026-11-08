package me.arnee.vaer

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import kotlin.math.roundToInt
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class StedActivity : Activity() {
    private lateinit var navn: String
    private var uke = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navn = intent.getStringExtra("navn") ?: return finish()
        uke = savedInstanceState?.getBoolean("uke") ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("uke", uke)
    }

    override fun onResume() {
        super.onResume()
        vis()
        val sted = Visning.steder(this).firstOrNull { it.navn == navn } ?: return
        val v = Visning.varsler[navn]
        if (v == null || v.utloper < System.currentTimeMillis()) Thread {
            runCatching { Met.varsel(sted) }.onSuccess {
                Visning.varsler[navn] = it
                runOnUiThread { vis() }
            }
        }.start()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val v = Visning.varsler[navn]
        val na = v?.timer?.firstOrNull()
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(ui.row(k, WaddleTokens.SPACE_3).apply {
                if (na != null) addView(ui.icon(k, Met.ikon(na.symbol), 48))
                addView(ui.column(k, WaddleTokens.SPACE_1).apply {
                    addView(ui.text(k, navn.substringBefore(","), WaddleTokens.TEXT_XL, bold = true))
                    if (na != null) addView(ui.text(k, "${Met.ord(na.symbol)}, ${na.vind.roundToInt()} m/s", muted = true))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                if (na != null) addView(ui.text(k, Visning.grader(na.temp), WaddleTokens.TEXT_XL, bold = true))
            })
            if (v == null) {
                addView(ui.text(k, "Henter", muted = true))
                return@apply
            }
            addView(ui.row(k).apply {
                addView(ui.button(k, "I dag", primary = !uke) { uke = false; vis() })
                addView(ui.button(k, "7 dager", primary = uke) { uke = true; vis() })
            })
            addView(ui.box(k).apply {
                if (!uke) {
                    val regn = Visning.regn(v)
                    addView(if (regn != null) ui.text(k, regn, bold = true).apply { setTextColor(ui.palette(k).accent) } else ui.text(k, "Ikke regn de neste 12 timene", muted = true))
                    addView(ui.divider(k))
                    v.timer.getOrNull(1)?.let { addView(Visning.timeRad(k, listOf(it), "Neste time")) }
                    Visning.grupper(v.timer.drop(2).take(24)).forEach { g ->
                        if (g.first().time == 0) addView(ui.text(k, "I morgen", WaddleTokens.TEXT_SM, muted = true))
                        addView(Visning.timeRad(k, g, null))
                    }
                } else {
                    if (v.dager.isEmpty()) addView(ui.text(k, "Ingen data for de neste dagene.", muted = true))
                    v.dager.take(7).forEachIndexed { i, d ->
                        if (i > 0) addView(ui.divider(k))
                        addView(Visning.dagRad(k, d))
                    }
                }
            })
            addView(ui.text(k, "Data fra MET Norge.", WaddleTokens.TEXT_SM, muted = true))
        }
        ui.screen(this, innhold)
    }
}
