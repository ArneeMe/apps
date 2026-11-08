package me.arnee.vaer

import android.content.Context
import android.widget.LinearLayout
import kotlin.math.roundToInt
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi

object Visning {
    val varsler = java.util.concurrent.ConcurrentHashMap<String, Varsel>()
    val feil: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun steder(context: Context): List<Sted> {
        val lagret = context.getSharedPreferences("vaer", Context.MODE_PRIVATE).getString("steder", null) ?: "Oslo|59.9139|10.7522\nBergen|60.3913|5.3221"
        return lagret.lines().mapNotNull { linje ->
            val d = linje.split("|")
            if (d.size == 3) Sted(d[0], d[1].toDouble(), d[2].toDouble()) else null
        }
    }

    fun lagre(context: Context, liste: List<Sted>) {
        context.getSharedPreferences("vaer", Context.MODE_PRIVATE).edit()
            .putString("steder", liste.joinToString("\n") { "${it.navn}|${it.lat}|${it.lon}" }).apply()
    }

    fun grader(t: Double) = "${t.roundToInt()}°"

    fun regn(v: Varsel): String? = v.timer.drop(1).take(12).firstOrNull { it.nedbor > 0 }?.let { "Regn fra kl. %02d".format(it.time) }

    private fun bredde(k: Context) = WaddleUi.dp(k, WaddleTokens.SPACE_12 + WaddleTokens.SPACE_8)

    private fun regnTekst(k: Context, mm: Double) = WaddleUi.text(k, "Regn %.1f mm".format(mm), WaddleTokens.TEXT_SM, bold = true).apply { setTextColor(WaddleUi.palette(k).accent) }

    fun grupper(timer: List<Time>): List<List<Time>> {
        val ut = mutableListOf<MutableList<Time>>()
        for (t in timer) {
            val siste = ut.lastOrNull()
            if (siste != null && t.time != 0 && siste.size < 6 && Met.ord(siste[0].symbol) == Met.ord(t.symbol) && (siste[0].nedbor > 0) == (t.nedbor > 0)) siste += t else ut += mutableListOf(t)
        }
        return ut
    }

    fun timeRad(k: Context, g: List<Time>, etikett: String?): LinearLayout {
        val ui = WaddleUi
        val forste = g.first()
        val tid = etikett ?: if (g.size == 1) "%02d".format(forste.time) else "%02d–%02d".format(forste.time, (g.last().time + 1) % 24)
        val min = g.minOf { it.temp }
        val maks = g.maxOf { it.temp }
        val nedbor = g.sumOf { it.nedbor }
        return ui.row(k, WaddleTokens.SPACE_3).apply {
            addView(ui.text(k, tid, muted = true), LinearLayout.LayoutParams(bredde(k), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(ui.icon(k, Met.ikon(forste.symbol)))
            addView(ui.column(k, 0).apply {
                addView(ui.text(k, Met.ord(forste.symbol)))
                if (nedbor > 0) addView(regnTekst(k, nedbor))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(ui.text(k, if (min.roundToInt() == maks.roundToInt()) grader(min) else "${min.roundToInt()}–${grader(maks)}", bold = true))
        }
    }

    fun dagRad(k: Context, d: Dag): LinearLayout {
        val ui = WaddleUi
        val navn = d.dato.format(java.time.format.DateTimeFormatter.ofPattern("EEE d.", java.util.Locale.getDefault()))
        return ui.row(k, WaddleTokens.SPACE_3).apply {
            addView(ui.text(k, navn, muted = true), LinearLayout.LayoutParams(bredde(k), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(ui.icon(k, Met.ikon(d.symbol)))
            addView(ui.column(k, 0).apply {
                addView(ui.text(k, Met.ord(d.symbol)))
                if (d.nedbor >= 0.5) addView(regnTekst(k, d.nedbor))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(ui.text(k, "${d.min.roundToInt()}–${grader(d.maks)}", bold = true))
        }
    }
}
