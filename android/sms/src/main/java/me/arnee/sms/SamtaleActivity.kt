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
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(Innstillinger.skaler(base))
    }

    private var traad = -1L
    private var adresser: List<String> = emptyList()
    private var mottaker = ""
    private var utkast = ""
    private var vedlegg: Uri? = null
    private var sim = Sim.INGEN
    private var antall = 60

    private val handler = Handler(Looper.getMainLooper())
    private val hent = Runnable { last() }

    private val endringer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(hent)
            handler.postDelayed(hent, 150)
        }
    }

    private fun bredde() = resources.displayMetrics.widthPixels - WaddleUi.dp(this, WaddleTokens.SPACE_12)

    private fun last() {
        if (traad < 0) return
        val app = applicationContext
        val id = traad
        val gruppe = adresser.size > 1
        val bredde = bredde()
        Lager.last({
            Lager.meldinger(app, id, gruppe).also { liste ->
                liste.forEach { m -> m.bilder.forEach { Lager.bilde(app, it, bredde) } }
                liste.mapNotNull { it.fra }.forEach { Lager.navn(app, it) }
            }
        }) { ny ->
            if (id != traad || ny == Lager.sisteMeldinger[id]) return@last
            Lager.sisteMeldinger[id] = ny
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
            adresser = (Lager.sisteSamtaler ?: Lager.samtaler(this)).firstOrNull { it.id == traad }?.adresser ?: emptyList()
        }
        utkast = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra("sms_body") ?: ""
    }

    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(Uri.parse("content://mms-sms/"), true, endringer)
        if (traad >= 0) {
            val app = applicationContext
            val id = traad
            Lager.last({ Lager.lest(app, id) }) {}
            Varsel.fjern(this, traad)
        }
        vis()
        last()
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(endringer)
    }

    private fun send() {
        if (adresser.isEmpty()) return
        val tekst = utkast.trim()
        val bilde = vedlegg
        if (tekst.isEmpty() && bilde == null) return
        if (!Lager.standard(this) && checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 1)
            return
        }
        val mms = bilde != null || adresser.size > 1
        if (mms && !Lager.standard(this)) {
            Toast.makeText(this, "Bilder og gruppemeldinger krever at Meldinger er standard SMS-app.", Toast.LENGTH_LONG).show()
            return
        }
        val til = adresser.toList()
        utkast = ""
        vedlegg = null
        vis()
        Thread {
            val resultat = runCatching {
                if (mms) {
                    Mms.send(this, til, tekst.ifEmpty { null }, bilde?.let { Mms.bilde(this, it) }, sim = sim)
                } else {
                    Lager.send(this, til.first(), tekst, sim)
                }
            }
            runOnUiThread {
                resultat.exceptionOrNull()?.let { Toast.makeText(this, "Fikk ikke sendt: ${it.javaClass.simpleName}: ${it.message}", Toast.LENGTH_LONG).show() }
                vis()
            }
        }.start()
    }

    private fun sendMerke(fil: java.io.File) {
        if (adresser.isEmpty()) return
        if (!Lager.standard(this)) {
            Toast.makeText(this, "Klistremerker krever at Meldinger er standard SMS-app.", Toast.LENGTH_LONG).show()
            return
        }
        val til = adresser.toList()
        Thread {
            val resultat = runCatching {
                val (data, type) = Klistremerker.tilMms(fil)
                Mms.send(this, til, null, data, type, sim)
            }
            runOnUiThread {
                resultat.exceptionOrNull()?.let { Toast.makeText(this, "Fikk ikke sendt: ${it.message}", Toast.LENGTH_LONG).show() }
                vis()
            }
        }.start()
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 5 && resultCode == RESULT_OK) {
            vedlegg = data?.data
            vis()
        }
        if (requestCode == 6 && resultCode == RESULT_OK) {
            data?.data?.path?.let { sendMerke(java.io.File(it)) }
        }
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val navn = adresser.joinToString(", ") { Lager.navn(k, it) }
        val innhold = ui.column(k, WaddleTokens.SPACE_4).apply {
            addView(ui.text(k, navn.ifEmpty { "Ny melding" }, WaddleTokens.TEXT_LG, bold = true))
            if (traad >= 0) {
                val alleMeldinger = Lager.sisteMeldinger[traad] ?: emptyList()
                val meldinger = alleMeldinger.takeLast(antall)
                if (alleMeldinger.size > antall) {
                    addView(ui.button(k, "Vis eldre") {
                        antall += 100
                        vis()
                    })
                }
                sim = Sim.valgt(k, traad, meldinger)
                val flere = Sim.flere(k)
                var forrigeSim = Sim.INGEN
                val bredde = bredde()
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
                        if (adresser.size > 1 && !m.fraMeg && m.fra != null) addView(ui.text(k, Lager.navn(k, m.fra), WaddleTokens.TEXT_SM, bold = true))
                        if (m.tekst.isNotEmpty()) addView(ui.text(k, m.tekst).apply {
                            setTextIsSelectable(true)
                            if (m.fraMeg) gravity = android.view.Gravity.END
                        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                        m.bilder.forEach { uri ->
                            Lager.bildeFraMinne(uri, bredde)?.let { bilde ->
                                addView(ImageView(k).apply {
                                    setImageBitmap(bilde)
                                    adjustViewBounds = true
                                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                    contentDescription = "Bilde. Trykk for å åpne."
                                    isClickable = true
                                    setOnClickListener { startActivity(Intent(k, BildeActivity::class.java).setData(uri)) }
                                })
                            }
                        }
                        val simMerke = if (flere && m.sim != Sim.INGEN && m.sim != forrigeSim) Sim.kort(k, m.sim) else null
                        forrigeSim = m.sim
                        val under = listOfNotNull(
                            simMerke,
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
                Deler.tastatur(this@SamtaleActivity)
                return
            }
            if (adresser.isNotEmpty() && adresser.none { Lager.kanSvare(it) }) {
                addView(ui.text(k, "Du kan ikke svare til denne avsenderen.", WaddleTokens.TEXT_SM, muted = true).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                ui.screen(this@SamtaleActivity, this)
                Deler.tastatur(this@SamtaleActivity, nederst = true)
                return
            }
            addView(ui.box(k).apply {
                vedlegg?.let { uri ->
                    Lager.bilde(k, uri, WaddleUi.dp(k, 160))?.let { forhand ->
                        addView(ImageView(k).apply {
                            setImageBitmap(forhand)
                            adjustViewBounds = true
                            layoutParams = LinearLayout.LayoutParams(WaddleUi.dp(k, 160), ViewGroup.LayoutParams.WRAP_CONTENT)
                        })
                    }
                    addView(ui.button(k, "Fjern bilde") {
                        vedlegg = null
                        vis()
                    })
                }
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
                addView(ui.row(k, WaddleTokens.SPACE_1).apply {
                    addView(ui.button(k, "Send", primary = true) { send() })
                    addView(ui.button(k, "Bilde") {
                        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), 5)
                    })
                    addView(ui.button(k, "Merke") {
                        startActivityForResult(Intent(k, KlistremerkerActivity::class.java), 6)
                    })
                    if (Sim.flere(k)) addView(ui.button(k, Sim.kort(k, sim) ?: "SIM") {
                        sim = Sim.neste(k, sim)
                        Sim.velg(k, traad, sim)
                        vis()
                    }.apply { textSize = WaddleTokens.TEXT_SM })
                    for (i in 0 until childCount) (getChildAt(i) as? android.widget.Button)?.apply {
                        minWidth = 0
                        minimumWidth = 0
                        val pad = WaddleUi.dp(k, WaddleTokens.SPACE_3)
                        setPadding(pad, 0, pad, 0)
                    }
                })
            })
        }
        ui.screen(this, innhold)
        Deler.tastatur(this, nederst = true)
    }

    private fun velg(nummer: String) {
        adresser = listOf(nummer)
        traad = Lager.traad(this, nummer)
        vis()
        last()
    }

    private fun lytter(endret: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            endret(s.toString())
        }
    }
}
