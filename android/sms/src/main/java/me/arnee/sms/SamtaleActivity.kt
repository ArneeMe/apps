package me.arnee.sms

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class SamtaleActivity : Activity() {
    private var traad = -1L
    private var adresser: List<String> = emptyList()
    private var mottaker = ""
    private var utkast = ""

    private val endringer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            vis()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        traad = intent.getLongExtra("traad", -1L)
        val fraLenke = intent.data?.schemeSpecificPart?.substringBefore("?")?.split(",", ";")?.map { it.trim() }?.filter { it.isNotEmpty() }
        if (traad < 0 && !fraLenke.isNullOrEmpty()) {
            adresser = fraLenke
            traad = Lager.traad(this, fraLenke.first())
        }
        if (traad >= 0 && adresser.isEmpty()) {
            adresser = Lager.samtaler(this).firstOrNull { it.id == traad }?.adresser ?: emptyList()
        }
        utkast = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra("sms_body") ?: ""
    }

    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(Uri.parse("content://mms-sms/"), true, endringer)
        if (traad >= 0) {
            Lager.lest(this, traad)
            Varsel.fjern(this, traad)
        }
        vis()
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(endringer)
    }

    private fun send() {
        val til = adresser.firstOrNull() ?: mottaker.trim()
        val tekst = utkast.trim()
        if (til.isEmpty() || tekst.isEmpty()) return
        if (!Lager.standard(this) && checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 1)
            return
        }
        val resultat = runCatching { Lager.send(this, til, tekst) }
        if (resultat.isFailure) {
            Toast.makeText(this, "Fikk ikke sendt: ${resultat.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            return
        }
        utkast = ""
        if (traad < 0) {
            traad = Lager.traad(this, til)
            adresser = listOf(til)
        }
        vis()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val navn = adresser.joinToString(", ") { Lager.navn(k, it) }
        val innhold = ui.column(k, WaddleTokens.SPACE_4).apply {
            addView(ui.text(k, navn.ifEmpty { "Ny melding" }, WaddleTokens.TEXT_LG, bold = true))
            if (traad >= 0) {
                val meldinger = runCatching { Lager.meldinger(k, traad) }.getOrDefault(emptyList())
                val bredde = resources.displayMetrics.widthPixels - WaddleUi.dp(k, WaddleTokens.SPACE_12)
                meldinger.forEach { m ->
                    addView(ui.column(k, WaddleTokens.SPACE_1).apply {
                        val hvem = if (m.fraMeg) "Meg" else navn.substringBefore(",")
                        val status = if (m.feilet) ", ikke sendt" else ""
                        addView(ui.text(k, "$hvem, ${format.format(Date(m.tid))}$status", WaddleTokens.TEXT_SM, muted = true))
                        if (m.tekst.isNotEmpty()) addView(ui.text(k, m.tekst).apply { setTextIsSelectable(true) })
                        m.bilder.forEach { uri ->
                            Lager.bilde(k, uri, bredde)?.let { bilde ->
                                addView(ImageView(k).apply {
                                    setImageBitmap(bilde)
                                    adjustViewBounds = true
                                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                })
                            }
                        }
                        val pad = WaddleUi.dp(k, WaddleTokens.SPACE_2)
                        setPadding(if (m.fraMeg) WaddleUi.dp(k, WaddleTokens.SPACE_8) else 0, pad, 0, pad)
                    })
                }
            } else {
                addView(Deler.felt(k, "Telefonnummer").apply {
                    inputType = android.text.InputType.TYPE_CLASS_PHONE
                    setText(mottaker)
                    addTextChangedListener(lytter { mottaker = it })
                })
            }
            if (adresser.size > 1) {
                addView(ui.text(k, "Gruppesamtaler krever MMS. Svar herfra går bare til ${Lager.navn(k, adresser.first())}.", WaddleTokens.TEXT_SM, muted = true))
            }
            addView(ui.box(k).apply {
                addView(Deler.felt(k, "Melding", flerlinje = true).apply {
                    setText(utkast)
                    addTextChangedListener(lytter { utkast = it })
                })
                addView(ui.button(k, "Send", primary = true) { send() })
            })
        }
        ui.screen(this, innhold)
        val rot = (findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)
        if (rot is ScrollView) rot.post { rot.fullScroll(View.FOCUS_DOWN) }
    }

    private fun lytter(endret: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            endret(s.toString())
        }
    }
}
