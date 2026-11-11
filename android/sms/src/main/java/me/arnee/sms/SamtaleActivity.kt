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
        val tekst = utkast.trim()
        val bilde = vedlegg
        if (tekst.isEmpty() && bilde == null) return
        if (sendInnhold(tekst, bilde)) {
            utkast = ""
            vedlegg = null
        }
    }

    private fun reager(m: Melding, hjerte: Boolean) {
        val sitat = m.tekst.replace(Regex("\\s+"), " ").trim().let { if (it.length > 120) it.take(119) + "…" else it }
        if (sitat.isEmpty()) return
        sendInnhold(if (hjerte) "Satte et hjerte ved «$sitat»" else "Likte «$sitat»", null)
    }

    private fun meny(m: Melding) {
        val valg = listOf("👍  Lik", "❤️  Hjerte", "Kopier")
        android.app.AlertDialog.Builder(this)
            .setItems(valg.toTypedArray()) { _, i ->
                when (i) {
                    0 -> reager(m, false)
                    1 -> reager(m, true)
                    2 -> {
                        getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("melding", m.tekst))
                        Toast.makeText(this, "Kopiert", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun sendInnhold(tekst: String, bilde: Uri?): Boolean {
        if (adresser.isEmpty()) return false
        if (!Lager.standard(this) && checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 1)
            return false
        }
        val mms = bilde != null || adresser.size > 1
        if (mms && !Lager.standard(this)) {
            Toast.makeText(this, "Bilder og gruppemeldinger krever at Meldinger er standard SMS-app.", Toast.LENGTH_LONG).show()
            return false
        }
        val til = adresser.toList()
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
        return true
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
                    val p = ui.palette(k)
                    val maks = (bredde * 0.82).toInt()
                    addView(ui.column(k, WaddleTokens.SPACE_1).apply {
                        gravity = if (m.fraMeg) android.view.Gravity.END else android.view.Gravity.START
                        if (adresser.size > 1 && !m.fraMeg && m.fra != null) addView(ui.text(k, Lager.navn(k, m.fra), WaddleTokens.TEXT_SM, bold = true))
                        addView(ui.column(k, WaddleTokens.SPACE_2).apply {
                            val inn = WaddleUi.dp(k, WaddleTokens.SPACE_3)
                            setPadding(inn, inn, inn, inn)
                            background = if (m.fraMeg) ui.outline(k, p.surface, p.border) else ui.outline(k, bland(p.bg, p.border, 0.45f), bland(p.bg, p.border, 0.45f))
                            if (m.tekst.isNotEmpty()) addView(ui.text(k, m.tekst).apply { maxWidth = maks - 2 * inn })
                            m.bilder.forEach { uri ->
                                Lager.bildeFraMinne(uri, bredde)?.let { bilde ->
                                    addView(ImageView(k).apply {
                                        setImageBitmap(bilde)
                                        adjustViewBounds = true
                                        layoutParams = LinearLayout.LayoutParams(maks - 2 * inn, ViewGroup.LayoutParams.WRAP_CONTENT)
                                        contentDescription = "Bilde. Trykk for å åpne."
                                        isClickable = true
                                        setOnClickListener { startActivity(Intent(k, BildeActivity::class.java).setData(uri)) }
                                    })
                                }
                            }
                            isLongClickable = true
                            setOnLongClickListener {
                                meny(m)
                                true
                            }
                        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                        val simMerke = if (flere && m.sim != Sim.INGEN && m.sim != forrigeSim) Sim.kort(k, m.sim) else null
                        forrigeSim = m.sim
                        val under = listOfNotNull(
                            simMerke,
                            m.reaksjoner.takeIf { it.isNotEmpty() }?.joinToString(" "),
                            if (m.feilet) "Ikke sendt" else null,
                            if (m.levert && m.fraMeg && m == meldinger.lastOrNull { it.fraMeg }) "Levert" else null,
                        )
                        if (under.isNotEmpty()) addView(ui.text(k, under.joinToString("  "), WaddleTokens.TEXT_SM, muted = !m.feilet))
                        val pad = WaddleUi.dp(k, WaddleTokens.SPACE_1)
                        setPadding(0, pad, 0, pad)
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

    private fun bland(a: Int, b: Int, t: Float): Int {
        val f = { skift: Int -> (((a shr skift) and 0xFF) * (1 - t) + ((b shr skift) and 0xFF) * t).toInt() and 0xFF }
        return (0xFF shl 24) or (f(16) shl 16) or (f(8) shl 8) or f(0)
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
