package me.arnee.tur

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import kotlin.math.roundToInt
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class RuteActivity : Activity() {
    private lateinit var ruteId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ruteId = intent.getStringExtra("id") ?: return finish()
        vis()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val r = Ruter.alle(k).firstOrNull { it.id == ruteId } ?: return finish()
        val data = Ruter.fil(k, ruteId).readBytes()
        val spor = Rute.les(data) ?: return finish()
        val kart = Kart(k).apply {
            rute = spor
            vis(spor.punkter + spor.merker.map { Punkt(it.lat, it.lon) })
        }
        ui.screen(k, ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, r.navn, "pencil", "Gi nytt navn") {
                Ruter.skjema(k, "Endre", r.navn, r.mappe) { n, m ->
                    Ruter.endre(k, ruteId, n, m)
                    vis()
                }
            })
            addView(Kart.ramme(k, kart), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, resources.displayMetrics.heightPixels * 9 / 20))
            addView(ui.box(k).apply {
                addView(Deler.verdi(k, "Mappe", r.mappe.ifEmpty { "Ingen" }))
                addView(Deler.verdi(k, "Lengde", Vis.km(spor.lengde)))
                if (spor.punkter.any { it.hoyde != null }) addView(Deler.verdi(k, "Stigning", "${spor.stigning.roundToInt()} m"))
                addView(Deler.verdi(k, "Løp, omtrent", Vis.varighet(Ruting.tid(spor, "running"))))
                addView(Deler.verdi(k, "Tur, omtrent", Vis.varighet(Ruting.tid(spor, "hiking"))))
                if (spor.merker.isNotEmpty()) addView(Deler.verdi(k, "Steder underveis", spor.merker.size.toString()))
            })
            addView(ui.button(k, "Følg ruta", primary = true) {
                Rute.bruk(k, data, spor)
                startActivity(Intent(k, MainActivity::class.java).putExtra("visRute", true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            })
            addView(ui.button(k, "Lagre som GPX") {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/gpx+xml").putExtra(Intent.EXTRA_TITLE, r.navn.replace(Regex("[^\\p{L}\\p{N} _-]"), "").ifEmpty { "rute" } + ".gpx"), 1)
            })
            addView(ui.button(k, "Slett") {
                AlertDialog.Builder(k)
                    .setMessage("Slette ${r.navn}?")
                    .setPositiveButton("Slett") { _, _ ->
                        Ruter.slett(k, ruteId)
                        finish()
                    }
                    .setNegativeButton("Avbryt", null)
                    .show()
            })
        })
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != 1 || resultCode != RESULT_OK) return
        val ok = runCatching { contentResolver.openOutputStream(uri)?.use { it.write(Ruter.fil(this, ruteId).readBytes()) } != null }.getOrDefault(false)
        Toast.makeText(this, if (ok) "Lagret" else "Fikk ikke lagret filen.", Toast.LENGTH_LONG).show()
    }
}
