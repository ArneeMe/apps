package me.arnee.tall

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class MainActivity : Activity() {
    private val ui = WaddleUi
    private val haandtak = Handler(Looper.getMainLooper())
    private var store = 1
    private var igjen = 0
    private lateinit var felter: List<EditText>
    private lateinit var malFelt: EditText
    private lateinit var klokke: TextView
    private lateinit var storeKnapper: List<Button>
    private lateinit var svar: LinearLayout

    private val tikk = object : Runnable {
        override fun run() {
            igjen--
            klokke.text = if (igjen > 0) "$igjen" else "Tid ute"
            if (igjen > 0) haandtak.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val k = this
        felter = List(6) { tallfelt("") }
        malFelt = tallfelt("Mål")
        klokke = ui.text(k, "30", WaddleTokens.TEXT_XL, bold = true).apply { gravity = Gravity.CENTER }
        storeKnapper = (0..4).map { n ->
            ui.button(k, "$n") {
                store = n
                merkStore()
            }.apply {
                minWidth = 0
                minimumWidth = 0
            }
        }
        svar = ui.column(k, WaddleTokens.SPACE_6)
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Tallspill"))
            addView(ui.box(k).apply {
                addView(ui.text(k, "Store tall", muted = true))
                addView(ui.row(k).apply {
                    storeKnapper.forEach { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
                })
                addView(ui.button(k, "Trekk", primary = true) { trekk() })
            })
            addView(ui.box(k).apply {
                addView(klokke)
                addView(malFelt)
                addView(ui.row(k, WaddleTokens.SPACE_1).apply {
                    felter.forEach { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
                })
                addView(ui.button(k, "Løs", primary = true) { los() })
            })
            addView(svar)
        }
        ui.screen(this, innhold)
        Deler.tastatur(this)
        merkStore()
    }

    override fun onDestroy() {
        haandtak.removeCallbacks(tikk)
        super.onDestroy()
    }

    private fun tallfelt(hint: String) = Deler.felt(this, hint).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        gravity = Gravity.CENTER
        setPadding(0, paddingTop, 0, paddingBottom)
    }

    private fun merkStore() {
        val p = ui.palette(this)
        storeKnapper.forEachIndexed { n, knapp ->
            val valgt = n == store
            knapp.setTextColor(if (valgt) p.onAccent else p.text)
            knapp.background = ui.outline(this, if (valgt) p.accent else p.bg, if (valgt) p.accent else p.border)
        }
    }

    private fun trekk() {
        val sma = (1..10).flatMap { listOf(it, it) }.shuffled().take(6 - store)
        val tall = listOf(25, 50, 75, 100).shuffled().take(store) + sma
        tall.forEachIndexed { i, t -> felter[i].setText("$t") }
        malFelt.setText("${(100..999).random()}")
        svar.removeAllViews()
        haandtak.removeCallbacks(tikk)
        igjen = 31
        tikk.run()
    }

    private fun los() {
        val tall = felter.mapNotNull { it.text.toString().toIntOrNull()?.takeIf { t -> t > 0 } }
        val mal = malFelt.text.toString().toIntOrNull()
        svar.removeAllViews()
        if (tall.isEmpty() || mal == null) {
            svar.addView(ui.text(this, "Fyll inn tall og mål.", muted = true))
            return
        }
        haandtak.removeCallbacks(tikk)
        svar.addView(ui.text(this, "Regner …", muted = true))
        Thread {
            val r = Losning.los(tall, mal)
            runOnUiThread { visSvar(r, mal) }
        }.start()
    }

    private fun visSvar(r: Losning.Resultat, mal: Int) {
        val k = this
        svar.removeAllViews()
        val topp = when {
            r.avstand > 0 -> "Ingen eksakt. Nærmest er ${r.svar.first().verdi}, ${r.avstand} unna."
            r.antall > r.svar.size -> "${r.antall} løsninger. Viser de ${r.svar.size} korteste."
            r.antall == 1 -> "1 løsning."
            else -> "${r.antall} løsninger."
        }
        svar.addView(ui.text(k, topp, muted = true))
        svar.addView(ui.box(k).apply {
            r.svar.forEachIndexed { i, s ->
                if (i > 0) addView(ui.divider(k))
                addView(ui.column(k, WaddleTokens.SPACE_1).apply {
                    if (s.steg.isEmpty()) addView(ui.text(k, "$mal er et av tallene"))
                    s.steg.forEach { addView(ui.text(k, it)) }
                })
            }
        })
    }
}
