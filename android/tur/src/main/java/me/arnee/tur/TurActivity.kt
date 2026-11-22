package me.arnee.tur

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import java.io.File
import kotlin.math.roundToInt
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class TurActivity : Activity() {
    private lateinit var fil: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fil = File(intent.getStringExtra("fil") ?: "")
        val s = Turer.les(fil)
        if (s == null) {
            finish()
            return
        }
        val ui = WaddleUi
        val k = this
        val kart = Kart(k).apply {
            spor = s.punkter
            vis(s.punkter)
        }
        ui.screen(k, ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, if (s.navn.isNotEmpty() && s.navn != standardNavn(s.type, s.start)) s.navn else Vis.type(s.type), "pencil", "Gi nytt navn") { giNavn(s) })
            addView(Kart.ramme(k, kart), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, resources.displayMetrics.heightPixels * 9 / 20))
            addView(ui.box(k).apply {
                addView(Deler.verdi(k, "Start", Vis.dato(s.start)))
                addView(Deler.verdi(k, "Distanse", Vis.km(s.lengde)))
                addView(Deler.verdi(k, "Tid", Vis.tid(s.tid)))
                addView(Deler.verdi(k, "Tempo", Vis.tempo(s.tid, s.lengde) + " /km"))
                addView(Deler.verdi(k, "Fart", Vis.fart(s.tid, s.lengde) + " km/t"))
                if (s.punkter.any { it.hoyde != null }) addView(Deler.verdi(k, "Stigning", "${s.stigning.roundToInt()} m"))
            })
            addView(ui.button(k, "Følg som rute") {
                val data = fil.readBytes()
                val rute = Rute.les(data)
                if (rute != null) {
                    Rute.bruk(k, data, rute)
                    startActivity(Intent(k, MainActivity::class.java).putExtra("visRute", true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }
            })
            addView(ui.button(k, "Lagre i Ruter") { Ruter.lagreMedSkjema(k, fil.readBytes(), if (s.navn == standardNavn(s.type, s.start)) "" else s.navn) })
            addView(ui.button(k, "Lagre som GPX") {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/gpx+xml").putExtra(Intent.EXTRA_TITLE, "tur-${fil.nameWithoutExtension}.gpx"), 1)
            })
            addView(ui.button(k, "Slett") {
                AlertDialog.Builder(k)
                    .setMessage("Slette turen?")
                    .setPositiveButton("Slett") { _, _ ->
                        Turer.slett(k, fil)
                        finish()
                    }
                    .setNegativeButton("Avbryt", null)
                    .show()
            })
        })
    }

    private fun giNavn(s: Spor) {
        val felt = Deler.felt(this, "Navn").apply { setText(if (s.navn == standardNavn(s.type, s.start)) "" else s.navn) }
        AlertDialog.Builder(this)
            .setTitle("Gi nytt navn")
            .setView(felt)
            .setPositiveButton("Lagre") { _, _ ->
                Turer.giNavn(this, fil, s, felt.text.toString().trim().ifEmpty { standardNavn(s.type, s.start) })
                recreate()
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != 1 || resultCode != RESULT_OK) return
        val ok = runCatching { contentResolver.openOutputStream(uri)?.use { it.write(fil.readBytes()) } != null }.getOrDefault(false)
        Toast.makeText(this, if (ok) "Lagret" else "Fikk ikke lagret filen.", Toast.LENGTH_LONG).show()
    }
}
