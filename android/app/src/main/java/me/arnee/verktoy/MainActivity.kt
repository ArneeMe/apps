package me.arnee.verktoy

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
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
        addView(ui.listRow(this@MainActivity, "Nattlys", if (pa) "På" else "Av", iconName = "cloud-moon"))
        val farge = ui.text(this@MainActivity, "Farge ${Nattlys.kelvin(this@MainActivity)} K", muted = true)
        addView(farge)
        addView(glider(Nattlys.KELVIN_MAKS - Nattlys.KELVIN_MIN, Nattlys.kelvin(this@MainActivity) - Nattlys.KELVIN_MIN) { verdi ->
            Nattlys.settKelvin(this@MainActivity, Nattlys.KELVIN_MIN + verdi)
            farge.text = "Farge ${Nattlys.KELVIN_MIN + verdi} K"
        })
        val lys = ui.text(this@MainActivity, "Lysstyrke ${Nattlys.lysstyrke(this@MainActivity)} %", muted = true)
        addView(lys)
        addView(glider(100, Nattlys.lysstyrke(this@MainActivity)) { verdi ->
            Nattlys.settLysstyrke(this@MainActivity, verdi)
            lys.text = "Lysstyrke $verdi %"
        })
        addView(ui.button(this@MainActivity, if (pa) "Slå av" else "Slå på") {
            Nattlys.settPa(this@MainActivity, !pa)
            vis()
        })
    }

    private fun glider(maks: Int, verdi: Int, endret: (Int) -> Unit) = SeekBar(this).apply {
        val p = WaddleUi.palette(this@MainActivity)
        max = maks
        progress = verdi
        minimumHeight = WaddleUi.dp(this@MainActivity, WaddleTokens.SPACE_12)
        progressTintList = ColorStateList.valueOf(p.text)
        progressBackgroundTintList = ColorStateList.valueOf(p.border)
        thumb = GradientDrawable().apply {
            setColor(p.accent)
            setSize(WaddleUi.dp(this@MainActivity, WaddleTokens.SPACE_3), WaddleUi.dp(this@MainActivity, WaddleTokens.SPACE_6))
        }
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, verdi: Int, fraBruker: Boolean) {
                if (fraBruker) endret(verdi)
            }

            override fun onStartTrackingTouch(bar: SeekBar) {}

            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
    }
}
