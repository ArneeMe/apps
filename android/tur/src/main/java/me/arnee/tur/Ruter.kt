package me.arnee.tur

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.widget.LinearLayout
import android.widget.Toast
import java.io.File
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler
import org.json.JSONArray
import org.json.JSONObject

class LagretRute(val id: String, val navn: String, val mappe: String, val lengde: Double, val stigning: Double, val lagt: Long)

object Ruter {
    fun mappe(k: Context) = File(k.filesDir, "ruter").apply { mkdirs() }

    fun fil(k: Context, id: String) = File(mappe(k), "$id.gpx")

    private fun indeks(k: Context) = File(k.filesDir, "ruter.json")

    @Synchronized
    fun alle(k: Context): List<LagretRute> = runCatching {
        val a = JSONArray(indeks(k).readText())
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            LagretRute(it.getString("id"), it.optString("navn"), it.optString("mappe"), it.optDouble("lengde"), it.optDouble("stigning"), it.optLong("lagt"))
        }.filter { fil(k, it.id).exists() }
    }.getOrDefault(emptyList())

    @Synchronized
    private fun skriv(k: Context, liste: List<LagretRute>) {
        val a = JSONArray()
        liste.forEach { a.put(JSONObject().put("id", it.id).put("navn", it.navn).put("mappe", it.mappe).put("lengde", it.lengde).put("stigning", it.stigning).put("lagt", it.lagt)) }
        val ny = File(indeks(k).path + ".ny")
        ny.writeText(a.toString())
        ny.renameTo(indeks(k))
    }

    fun mapper(k: Context) = alle(k).map { it.mappe }.filter { it.isNotEmpty() }.distinct().sorted()

    fun lagre(k: Context, data: ByteArray, navn: String, mappe: String): LagretRute? {
        val s = Rute.les(data) ?: return null
        val id = System.currentTimeMillis().toString()
        val ny = File(fil(k, id).path + ".ny")
        ny.writeBytes(data)
        if (!ny.renameTo(fil(k, id))) return null
        val r = LagretRute(id, navn, mappe, s.lengde, s.stigning, System.currentTimeMillis())
        skriv(k, alle(k) + r)
        return r
    }

    fun endre(k: Context, id: String, navn: String? = null, mappe: String? = null) {
        skriv(k, alle(k).map { if (it.id == id) LagretRute(it.id, navn ?: it.navn, mappe ?: it.mappe, it.lengde, it.stigning, it.lagt) else it })
    }

    fun slett(k: Context, id: String) {
        fil(k, id).delete()
        skriv(k, alle(k).filter { it.id != id })
    }

    fun skjema(a: Activity, tittel: String, navn: String, mappe: String, lagre: (String, String) -> Unit) {
        val ui = WaddleUi
        val pad = ui.dp(a, WaddleTokens.SPACE_4)
        val navnFelt = Deler.felt(a, "Navn").apply { setText(navn) }
        val mappeFelt = Deler.felt(a, "Mappe, kan stå tom").apply { setText(mappe) }
        val innhold = ui.column(a, WaddleTokens.SPACE_3).apply {
            setPadding(pad, pad, pad, 0)
            addView(navnFelt)
            addView(mappeFelt)
            val finnes = mapper(a)
            if (finnes.isNotEmpty()) {
                addView(ui.text(a, "Mapper du har", WaddleTokens.TEXT_SM, muted = true))
                finnes.chunked(2).forEach { par ->
                    addView(ui.row(a).apply {
                        par.forEach { m ->
                            addView(ui.button(a, m) { mappeFelt.setText(m) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                        }
                        if (par.size == 1) addView(android.view.View(a), LinearLayout.LayoutParams(0, 0, 1f))
                    })
                }
            }
        }
        AlertDialog.Builder(a)
            .setTitle(tittel)
            .setView(android.widget.ScrollView(a).apply { addView(innhold) })
            .setPositiveButton("Lagre") { _, _ ->
                lagre(navnFelt.text.toString().trim().ifEmpty { "Rute uten navn" }, mappeFelt.text.toString().trim())
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    fun lagreMedSkjema(a: Activity, data: ByteArray, navn: String) {
        skjema(a, "Lagre i Ruter", navn, "") { n, m ->
            val ok = lagre(a, data, n, m) != null
            Toast.makeText(a, if (ok) "Lagret i Ruter" else "Fikk ikke lagret ruta.", Toast.LENGTH_SHORT).show()
        }
    }
}
