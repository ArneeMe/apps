package me.arnee.verktoy

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.LinearLayout
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.verktoy.nattlys.Nattlys
import me.arnee.verktoy.rodfilter.Rodfilter

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        vis()
    }

    private fun vis() {
        val ui = WaddleUi
        val innhold = ui.column(this, WaddleTokens.SPACE_6).apply {
            addView(ui.row(this@MainActivity, WaddleTokens.SPACE_3).apply {
                addView(pingvin())
                addView(ui.text(this@MainActivity, "Verktøy", WaddleTokens.TEXT_XL, bold = true))
            })
            addView(rodfilter())
            addView(nattlys())
        }
        ui.screen(this, innhold)
    }

    private fun pingvin(): ImageView {
        val bilde = resources.getDrawable(R.drawable.pingvin, theme) as BitmapDrawable
        bilde.setFilterBitmap(false)
        val skala = maxOf(1, (WaddleUi.dp(this, WaddleTokens.SPACE_12) / bilde.bitmap.width))
        val side = bilde.bitmap.width * skala
        return ImageView(this).apply {
            setImageDrawable(bilde)
            scaleType = ImageView.ScaleType.FIT_XY
            layoutParams = LinearLayout.LayoutParams(side, side)
            contentDescription = null
        }
    }

    private fun rodfilter() = WaddleUi.box(this).apply {
        val ui = WaddleUi
        val tilgang = Settings.canDrawOverlays(this@MainActivity)
        val pa = Rodfilter.pa(this@MainActivity)
        val styrke = Rodfilter.styrke(this@MainActivity)
        val detalj = when {
            !tilgang -> "Trenger tilgang til å vise over andre apper."
            pa -> "På, styrke $styrke %"
            else -> "Av, styrke $styrke %"
        }
        addView(ui.listRow(this@MainActivity, "Rødfilter", detalj, iconName = "moon"))
        addView(ui.divider(this@MainActivity))
        addView(ui.row(this@MainActivity).apply {
            if (!tilgang) {
                addView(ui.button(this@MainActivity, "Gi tilgang", primary = true) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                })
            } else {
                addView(ui.button(this@MainActivity, if (pa) "Slå av" else "Slå på", primary = true) {
                    Rodfilter.sett(this@MainActivity, !pa)
                    vis()
                })
            }
        })
        if (tilgang) addView(ui.row(this@MainActivity).apply {
            addView(ui.button(this@MainActivity, "Svakere") {
                Rodfilter.endreStyrke(this@MainActivity, -Rodfilter.STEG)
                vis()
            })
            addView(ui.button(this@MainActivity, "Sterkere") {
                Rodfilter.endreStyrke(this@MainActivity, Rodfilter.STEG)
                vis()
            })
        })
    }

    private fun nattlys() = WaddleUi.box(this).apply {
        val ui = WaddleUi
        if (!Nattlys.tilgang(this@MainActivity)) {
            addView(ui.listRow(this@MainActivity, "Nattlys", "Trenger tilgang. Kjør fra PC:", iconName = "cloud-moon"))
            addView(ui.text(this@MainActivity, Nattlys.KOMMANDO, WaddleTokens.TEXT_SM, muted = true).apply { setTextIsSelectable(true) })
            return@apply
        }
        val pa = Nattlys.pa(this@MainActivity)
        val dimmingPa = Nattlys.dimmingPa(this@MainActivity)
        addView(ui.listRow(this@MainActivity, "Nattlys", "${if (pa) "På" else "Av"}, ${Nattlys.kelvin(this@MainActivity)} K", iconName = "cloud-moon"))
        addView(ui.row(this@MainActivity).apply {
            addView(ui.button(this@MainActivity, if (pa) "Slå av" else "Slå på") {
                Nattlys.settPa(this@MainActivity, !pa)
                vis()
            })
        })
        addView(ui.row(this@MainActivity).apply {
            addView(ui.button(this@MainActivity, "Varmere") {
                Nattlys.endreKelvin(this@MainActivity, -Nattlys.KELVIN_STEG)
                vis()
            })
            addView(ui.button(this@MainActivity, "Kaldere") {
                Nattlys.endreKelvin(this@MainActivity, Nattlys.KELVIN_STEG)
                vis()
            })
        })
        addView(ui.divider(this@MainActivity))
        addView(ui.listRow(this@MainActivity, "Ekstra dimmet", "${if (dimmingPa) "På" else "Av"}, ${Nattlys.dimming(this@MainActivity)} %", iconName = "sun"))
        addView(ui.row(this@MainActivity).apply {
            addView(ui.button(this@MainActivity, if (dimmingPa) "Slå av" else "Slå på") {
                Nattlys.settDimmingPa(this@MainActivity, !dimmingPa)
                vis()
            })
        })
        addView(ui.row(this@MainActivity).apply {
            addView(ui.button(this@MainActivity, "Mørkere") {
                Nattlys.endreDimming(this@MainActivity, Nattlys.DIMMING_STEG)
                vis()
            })
            addView(ui.button(this@MainActivity, "Lysere") {
                Nattlys.endreDimming(this@MainActivity, -Nattlys.DIMMING_STEG)
                vis()
            })
        })
    }
}
