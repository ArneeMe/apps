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
        val til = adresser.firstOrNull() ?: return
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
        vis()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val navn = adresser.joinToString(", ") { Lager.navn(k, it) }
        val innhold = ui.column(k, WaddleTokens.SPACE_4).apply {
            addView(ui.text(k, navn.ifEmpty { "Ny melding" }, WaddleTokens.TEXT_LG, bold = true))
            if (traad >= 0) {
                val meldinger = runCatching { Lager.meldinger(k, traad) }.getOrDefault(emptyList())
                val bredde = resources.displayMetrics.widthPixels - WaddleUi.dp(k, WaddleTokens.SPACE_12)
                var forrige = 0L
                meldinger.forEach { m ->
                    if (m.tid - forrige > 30 * 60_000L) {
                        addView(ui.text(k, Tid.skille(m.tid), WaddleTokens.TEXT_SM, muted = true).apply {
                            gravity = android.view.Gravity.CENTER_HORIZONTAL
                            setPadding(0, WaddleUi.dp(k, WaddleTokens.SPACE_3), 0, 0)
                        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    }
                    forrige = m.tid
                    addView(ui.column(k, WaddleTokens.SPACE_1).apply {
                        if (m.tekst.isNotEmpty()) addView(ui.text(k, m.tekst).apply {
                            setTextIsSelectable(true)
                            if (m.fraMeg) gravity = android.view.Gravity.END
                        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                        m.bilder.forEach { uri ->
                            Lager.bilde(k, uri, bredde)?.let { bilde ->
                                addView(ImageView(k).apply {
                                    setImageBitmap(bilde)
                                    adjustViewBounds = true
                                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                })
                            }
                        }
                        val under = listOfNotNull(
                            m.reaksjoner.takeIf { it.isNotEmpty() }?.joinToString(", "),
                            if (m.feilet) "Ikke sendt" else null,
                            if (m.levert && m.fraMeg && m == meldinger.lastOrNull { it.fraMeg }) "Levert" else null,
                        )
                        if (under.isNotEmpty()) addView(ui.text(k, under.joinToString("  "), WaddleTokens.TEXT_SM, muted = !m.feilet).apply {
                            if (m.fraMeg) gravity = android.view.Gravity.END
                        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                        val innrykk = WaddleUi.dp(k, WaddleTokens.SPACE_12)
                        val pad = WaddleUi.dp(k, WaddleTokens.SPACE_1)
                        setPadding(if (m.fraMeg) innrykk else 0, pad, if (m.fraMeg) 0 else innrykk, pad)
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
            } else {
                val treffListe = ui.column(k, 0)
                val visTreff = { tekst: String ->
                    treffListe.removeAllViews()
                    val kontakter = Lager.kontakter(k, tekst)
                    kontakter.forEach { kontakt ->
                        treffListe.addView(Deler.klikkbar(ui.listRow(k, kontakt.navn, kontakt.nummer, iconName = "user")) { velg(kontakt.nummer) })
                    }
                    val nummer = tekst.filter { it.isDigit() || it == '+' }
                    if (nummer.length >= 3 && nummer.length >= tekst.trim().length - 2) {
                        treffListe.addView(Deler.klikkbar(ui.listRow(k, "Send til $nummer", null, iconName = "phone")) { velg(nummer) })
                    }
                }
                addView(Deler.felt(k, "Navn eller nummer").apply {
                    setText(mottaker)
                    addTextChangedListener(lytter {
                        mottaker = it
                        visTreff(it)
                    })
                    requestFocus()
                })
                addView(treffListe)
                visTreff(mottaker)
                addView(ui.text(k, "Velg en mottaker for å skrive.", WaddleTokens.TEXT_SM, muted = true))
                ui.screen(this@SamtaleActivity, this)
                return
            }
            if (adresser.size > 1) {
                addView(ui.text(k, "Gruppesamtaler krever MMS. Svar herfra går bare til ${Lager.navn(k, adresser.first())}.", WaddleTokens.TEXT_SM, muted = true))
            }
            addView(ui.box(k).apply {
                addView(Deler.felt(k, "Melding", flerlinje = !Innstillinger.enterSender(k)).apply {
                    setText(utkast)
                    addTextChangedListener(lytter { utkast = it })
                    if (Innstillinger.enterSender(k)) {
                        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
                        setOnEditorActionListener { _, _, _ ->
                            send()
                            true
                        }
                    }
                })
                addView(ui.button(k, "Send", primary = true) { send() })
            })
        }
        ui.screen(this, innhold)
        val rot = (findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)
        if (rot is ScrollView) rot.post { rot.fullScroll(View.FOCUS_DOWN) }
    }

    private fun velg(nummer: String) {
        adresser = listOf(nummer)
        traad = Lager.traad(this, nummer)
        vis()
    }

    private fun lytter(endret: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            endret(s.toString())
        }
    }
}
