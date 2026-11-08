package me.arnee.butikk

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class MainActivity : Activity() {
    private var apper: List<App>? = null
    private var feil = false
    private val henter = mutableSetOf<String>()

    override fun onResume() {
        super.onResume()
        last()
    }

    private fun last() {
        vis()
        Thread {
            val resultat = runCatching { Katalog.hent() }
            runOnUiThread {
                resultat.onSuccess {
                    apper = it
                    feil = false
                }.onFailure { feil = true }
                vis()
            }
        }.start()
    }

    private fun installer(app: App) {
        henter.add(app.pakke)
        vis()
        Thread {
            val resultat = runCatching { Katalog.installer(this, app) }
            runOnUiThread {
                henter.remove(app.pakke)
                if (resultat.isFailure) android.widget.Toast.makeText(this, "Fikk ikke lastet ned ${app.navn}. Sjekk nettet og prøv igjen.", android.widget.Toast.LENGTH_LONG).show()
                vis()
            }
        }.start()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Butikk"))
            if (!packageManager.canRequestPackageInstalls()) {
                addView(ui.box(k).apply {
                    addView(ui.listRow(k, "Installering", "Butikk trenger lov til å installere apper.", iconName = "download"))
                    addView(ui.button(k, "Gi tilgang", primary = true) {
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                    })
                })
            }
            val liste = apper
            when {
                feil && liste == null -> addView(ui.text(k, "Fikk ikke hentet listen. Sjekk nettet og trykk Oppdater.", muted = true))
                liste == null -> addView(ui.text(k, "Henter", muted = true))
                else -> addView(ui.box(k).apply {
                    liste.forEachIndexed { i, app ->
                        if (i > 0) addView(ui.divider(k))
                        val har = Katalog.installert(k, app.pakke)
                        val status = when {
                            app.pakke in henter -> "Laster ned"
                            har == null -> "Ikke installert"
                            har < app.versjon -> "Ny versjon: $har til ${app.versjon}"
                            else -> "Oppdatert, versjon $har"
                        }
                        addView(ui.listRow(k, app.navn, "${app.om}\n$status"))
                        if (app.pakke !in henter && (har == null || har < app.versjon)) {
                            addView(ui.button(k, if (har == null) "Installer" else "Oppdater", primary = har != null) { installer(app) })
                        }
                        if (har != null && app.adb.isNotEmpty()) {
                            addView(ui.text(k, "Tilgang fra PC:\n" + app.adb.joinToString("\n"), WaddleTokens.TEXT_SM, muted = true).apply { setTextIsSelectable(true) })
                        }
                    }
                })
            }
            addView(ui.button(k, "Oppdater") { last() })
        }
        ui.screen(this, innhold)
    }
}
