package me.arnee.tur

import android.app.Activity
import android.content.Intent
import java.io.File
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class TurerActivity : Activity() {
    override fun onResume() {
        super.onResume()
        vis(null)
        Thread {
            val turer = Turer.alle(this).mapNotNull { f -> Turer.les(f)?.let { f to it } }
            runOnUiThread { vis(turer) }
        }.start()
    }

    private fun vis(turer: List<Pair<File, Spor>>?) {
        val ui = WaddleUi
        val k = this
        ui.screen(k, ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Turer"))
            when {
                turer == null -> addView(ui.text(k, "Henter", muted = true))
                turer.isEmpty() -> addView(ui.text(k, "Ingen turer ennå.", muted = true))
                else -> addView(ui.box(k).apply {
                    turer.forEachIndexed { i, (f, s) ->
                        if (i > 0) addView(ui.divider(k))
                        val maal = if (s.type == "running") Vis.tempo(s.tid, s.lengde) + " /km" else Vis.fart(s.tid, s.lengde) + " km/t"
                        addView(Deler.klikkbar(ui.listRow(k, "${Vis.type(s.type)} · ${Vis.km(s.lengde)}", "${Vis.dato(s.start)} · ${Vis.tid(s.tid)} · $maal", if (s.type == "running") "directions" else "tree")) {
                            startActivity(Intent(k, TurActivity::class.java).putExtra("fil", f.path))
                        })
                    }
                })
            }
        })
    }
}
