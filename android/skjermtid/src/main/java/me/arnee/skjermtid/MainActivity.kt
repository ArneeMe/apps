package me.arnee.skjermtid

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class MainActivity : Activity() {
    private val valg = listOf(0, 15, 30, 45, 60, 90, 120)

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Bruk.sjekk(this)
        vis()
    }

    private fun tid(minutter: Int) = if (minutter >= 60) "${minutter / 60} t ${minutter % 60} min" else "$minutter min"

    private fun velgGrense(app: App) {
        AlertDialog.Builder(this)
            .setTitle(app.navn)
            .setItems(valg.map { if (it == 0) "Ingen grense" else "${tid(it)} om dagen" }.toTypedArray()) { _, i ->
                Bruk.settGrense(this, app.pakke, valg[i])
                vis()
            }
            .show()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Skjermtid"))
            if (!Bruk.tilgang(k)) {
                addView(ui.box(k).apply {
                    addView(ui.listRow(k, "Tilgang", "Skjermtid trenger å se hvor lenge appene er i bruk.", iconName = "hourglass"))
                    addView(ui.button(k, "Gi tilgang", primary = true) { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
                    addView(ui.text(k, "Eller fra PC:\n${Bruk.KOMMANDO}", WaddleTokens.TEXT_SM, muted = true).apply { setTextIsSelectable(true) })
                })
                return@apply
            }
            val apper = Bruk.idag(k)
            addView(ui.box(k).apply {
                addView(ui.listRow(k, "I dag", tid(apper.sumOf { it.minutter }), iconName = "hourglass"))
                addView(ui.divider(k))
                if (apper.isEmpty()) addView(ui.text(k, "Ingen apper brukt mer enn ett minutt.", muted = true))
                apper.take(25).forEach { app ->
                    val g = Bruk.grense(k, app.pakke)
                    val detalj = if (g > 0) "${tid(app.minutter)} av ${tid(g)}" else tid(app.minutter)
                    addView(Deler.klikkbar(ui.listRow(k, app.navn, detalj)) { velgGrense(app) })
                }
            })
            addView(ui.text(k, "Trykk på en app for å sette en grense. Du får et varsel når den er nådd.", WaddleTokens.TEXT_SM, muted = true))
        }
        ui.screen(this, innhold)
    }
}
