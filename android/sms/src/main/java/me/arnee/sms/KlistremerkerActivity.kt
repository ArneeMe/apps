package me.arnee.sms

import android.app.Activity
import android.content.ClipboardManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class KlistremerkerActivity : Activity() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(Innstillinger.skaler(base))
    }

    private var lenke = ""
    private var status: String? = null

    override fun onResume() {
        super.onResume()
        if (lenke.isEmpty()) {
            val utklipp = getSystemService(ClipboardManager::class.java).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            if (utklipp != null && Klistremerker.lenke(utklipp) != null) lenke = utklipp
        }
        vis()
    }

    private fun importer() {
        val (id, nokkel) = Klistremerker.lenke(lenke) ?: run {
            Toast.makeText(this, "Lenken må være fra signal.art og ha pack_id og pack_key.", Toast.LENGTH_LONG).show()
            return
        }
        status = "Henter pakken"
        vis()
        Thread {
            val resultat = runCatching {
                Klistremerker.importer(this, id, nokkel) { n, av -> runOnUiThread { status = "Henter ${n + 1} av $av"; vis() } }
            }
            runOnUiThread {
                status = resultat.exceptionOrNull()?.let { "Fikk ikke hentet pakken: ${it.message}" }
                if (resultat.isSuccess) lenke = ""
                vis()
            }
        }.start()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val side = (resources.displayMetrics.widthPixels - ui.dp(k, WaddleTokens.SPACE_8 * 2)) / 4
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(ui.text(k, "Klistremerker", WaddleTokens.TEXT_XL, bold = true))
            Klistremerker.pakker(k).forEach { pakke ->
                addView(ui.box(k).apply {
                    addView(ui.text(k, pakke.tittel, WaddleTokens.TEXT_LG, bold = true))
                    pakke.filer.chunked(4).forEach { rad ->
                        addView(LinearLayout(k).apply {
                            orientation = LinearLayout.HORIZONTAL
                            rad.forEach { fil ->
                                addView(ImageView(k).apply {
                                    Klistremerker.forhand(fil, side)?.let { setImageBitmap(it) }
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    contentDescription = "Klistremerke"
                                    isClickable = true
                                    setOnClickListener {
                                        setResult(RESULT_OK, intent.setData(android.net.Uri.fromFile(fil)))
                                        finish()
                                    }
                                }, LinearLayout.LayoutParams(side, side))
                            }
                        })
                    }
                    addView(ui.button(k, "Fjern pakken") {
                        Klistremerker.slett(k, pakke.id)
                        vis()
                    })
                })
            }
            addView(ui.box(k).apply {
                addView(ui.text(k, "Legg til pakke", WaddleTokens.TEXT_LG, bold = true))
                addView(ui.text(k, "Finn en pakke på signal.art og kopier lenken. Den ser ut som signal.art/addstickers/#pack_id=...&pack_key=...", WaddleTokens.TEXT_SM, muted = true))
                addView(Deler.felt(k, "Lenke").apply {
                    setText(lenke)
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) {
                            lenke = s.toString()
                        }
                    })
                })
                status?.let { addView(ui.text(k, it, muted = true)) }
                addView(ui.button(k, "Hent", primary = true) { importer() })
            })
        }
        ui.screen(this, innhold)
        Deler.tastatur(this)
    }
}
