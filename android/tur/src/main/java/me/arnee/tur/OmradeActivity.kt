package me.arnee.tur

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class OmradeActivity : Activity() {
    private lateinit var kart: Kart
    private lateinit var info: TextView
    private lateinit var knapper: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val regn = Runnable { oppdater() }
    private var biter = 0
    private var lagret = 0
    private var lagretMb = 0L

    companion object {
        private const val MAKS = 4000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = WaddleUi
        val k = this
        kart = Kart(k).apply {
            endret = {
                handler.removeCallbacks(regn)
                handler.postDelayed(regn, 300)
            }
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val lm = getSystemService(LocationManager::class.java)
            runCatching { lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time } }.getOrNull()?.let { kart.posisjon(it) }
            kart.settZoom(11.5)
        }
        info = ui.text(k, "", muted = true)
        knapper = ui.column(k, WaddleTokens.SPACE_2)
        ui.screen(k, ui.column(k, WaddleTokens.SPACE_4).apply {
            addView(Deler.tittel(k, "Last ned område"))
            addView(ui.text(k, "Flytt og zoom kartet til området du vil ha veier og stier for. Da lages ruter der uten nett, og nesten med en gang.", muted = true))
            addView(Kart.ramme(k, kart), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, resources.displayMetrics.heightPixels / 2))
            addView(info)
            addView(knapper)
        })
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        Nedlasting.lytter = { vis() }
        oppdater()
    }

    override fun onPause() {
        super.onPause()
        Nedlasting.lytter = null
    }

    private fun oppdater() {
        if (kart.width == 0) return
        val b = kart.synlig()
        val alle = Graf.biter(b[0], b[1], b[2], b[3])
        biter = alle.size
        val mappe = Nedlasting.mappe(this)
        lagret = if (biter > MAKS) 0 else alle.count { (y, x) -> Graf.harBit(mappe, y, x) }
        Thread {
            val mb = mappe.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1048576
            runOnUiThread {
                lagretMb = mb
                vis()
            }
        }.start()
        vis()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val b = kart.synlig()
        val hoyde = (b[2] - b[0]) * 110.54
        val bredde = (b[3] - b[1]) * 111.32 * cos((b[0] + b[2]) / 2 * PI / 180)
        val storrelse = "Området er omtrent ${bredde.roundToInt()} × ${hoyde.roundToInt()} km."
        info.text = when {
            Nedlasting.aktiv -> "Henter ${Nedlasting.ferdig.get() + Nedlasting.feilet.get()} av ${Nedlasting.totalt} kartbiter" + if (Nedlasting.feilet.get() > 0) ", ${Nedlasting.feilet.get()} feilet." else "."
            biter > MAKS -> "$storrelse Det er for stort. Zoom inn."
            else -> {
                val mangler = biter - lagret
                val minutter = (mangler * 15 / 2 / 60.0).roundToInt()
                "$storrelse $lagret av $biter kartbiter er lagret." + if (mangler > 0) " Resten tar omtrent ${if (minutter < 1) "under ett minutt" else "$minutter min"}." else ""
            }
        } + " Lagret på telefonen: $lagretMb MB."
        knapper.removeAllViews()
        if (Nedlasting.aktiv) {
            knapper.addView(ui.button(k, "Stopp") {
                Nedlasting.stopp = true
                vis()
            })
            return
        }
        if (biter in 1..MAKS) {
            knapper.addView(ui.button(k, "Last ned", primary = lagret < biter) {
                Nedlasting.start(k, b[0], b[1], b[2], b[3], false)
                handler.postDelayed({ vis() }, 300)
            })
            if (lagret > 0) knapper.addView(ui.button(k, "Oppdater alt i området") {
                AlertDialog.Builder(k)
                    .setMessage("Hente alle $biter kartbiter på nytt, for å få med nye veier og stier?")
                    .setPositiveButton("Oppdater") { _, _ ->
                        Nedlasting.start(k, b[0], b[1], b[2], b[3], true)
                        handler.postDelayed({ vis() }, 300)
                    }
                    .setNegativeButton("Avbryt", null)
                    .show()
            })
        }
        knapper.addView(ui.text(k, "Nedlastingen fortsetter med skjermen av. Kartdata fra OpenStreetMap.", WaddleTokens.TEXT_SM, muted = true))
    }
}
