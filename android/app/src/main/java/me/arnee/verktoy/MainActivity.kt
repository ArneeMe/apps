package me.arnee.verktoy

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.verktoy.nattlys.Nattlys
import me.arnee.verktoy.rodfilter.Rodfilter

class MainActivity : Activity() {

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

    private fun nattlys() = WaddleUi.box(this).apply {
        val ui = WaddleUi
        val kontekst = this@MainActivity
        if (!Nattlys.tilgang(kontekst)) {
            addView(ui.listRow(kontekst, "Nattmodus", "Trenger tilgang. Kjør fra PC:", iconName = "moon"))
            addView(ui.text(kontekst, Nattlys.KOMMANDO, WaddleTokens.TEXT_SM, muted = true).apply { setTextIsSelectable(true) })
            return@apply
        }
        if (!Settings.canDrawOverlays(kontekst)) {
            addView(ui.listRow(kontekst, "Nattmodus", "Trenger tilgang til å vise over andre apper.", iconName = "moon"))
            addView(ui.button(kontekst, "Gi tilgang", primary = true) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            })
            return@apply
        }
        val pa = Nattlys.pa(kontekst)
        addView(ui.listRow(kontekst, "Nattmodus", if (pa) "På" else "Av", iconName = "moon"))

        val lys = ui.text(kontekst, "Lysstyrke ${Nattlys.lysstyrke(kontekst)} %", muted = true)
        addView(lys)
        addView(glider(100, Nattlys.lysstyrke(kontekst)) { verdi ->
            Nattlys.settLysstyrke(kontekst, verdi)
            lys.text = "Lysstyrke $verdi %"
        })

        val temperatur = ui.text(kontekst, "Temperatur ${Nattlys.kelvin(kontekst)} K", muted = true)
        addView(temperatur)
        addView(glider(Nattlys.KELVIN_MAKS - Nattlys.KELVIN_MIN, Nattlys.KELVIN_MAKS - Nattlys.kelvin(kontekst)) { verdi ->
            Nattlys.settKelvin(kontekst, Nattlys.KELVIN_MAKS - verdi)
            temperatur.text = "Temperatur ${Nattlys.KELVIN_MAKS - verdi} K"
        })

        val intensitet = ui.text(kontekst, "Intensitet ${Rodfilter.styrke(kontekst)} %", muted = true)
        addView(intensitet)
        addView(glider(Rodfilter.MAKS, Rodfilter.styrke(kontekst)) { verdi ->
            Rodfilter.settStyrke(kontekst, verdi)
            intensitet.text = "Intensitet $verdi %"
        })

        addView(ui.button(kontekst, if (pa) "Slå av" else "Slå på", primary = true) {
            Nattlys.settPa(kontekst, !pa)
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
