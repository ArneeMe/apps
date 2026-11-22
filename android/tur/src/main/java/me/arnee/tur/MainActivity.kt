package me.arnee.tur

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class MainActivity : Activity(), LocationListener {
    private lateinit var kart: Kart
    private lateinit var sentrer: View
    private lateinit var verdier: List<TextView>
    private lateinit var etiketter: List<TextView>
    private lateinit var info: TextView
    private lateinit var knapper: LinearLayout
    private var posisjon: Location? = null
    private var igjen: Pair<Double, Double>? = null
    private var type = "running"
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private var gjeldende: MainActivity? = null
    }
    private val tikk = object : Runnable {
        override fun run() {
            oppdater()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Okt.last(this)
        Rute.last(this)
        type = if (Okt.pagar) Okt.type else Innst.type(this)
        bygg()
        if (savedInstanceState != null) {
            kart.sett(savedInstanceState.getDoubleArray("kart"))
            if (!kart.folg) sentrer.visibility = View.VISIBLE
            return
        }
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) haandter(intent)
        val prefs = getSharedPreferences("innst", MODE_PRIVATE)
        if (!harPosisjon() && !prefs.getBoolean("spurt", false)) {
            prefs.edit().putBoolean("spurt", true).apply()
            spor()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putDoubleArray("kart", kart.tilstand())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        haandter(intent)
    }

    override fun onResume() {
        super.onResume()
        gjeldende = this
        kart.kilde = Innst.kart(this)
        kart.rute = Rute.spor
        igjen = posisjon?.let { Rute.igjen(Punkt(it.latitude, it.longitude)) }
        Okt.lytter = {
            kart.invalidate()
            oppdater()
        }
        lytt()
        visKnapper()
        handler.post(tikk)
    }

    override fun onPause() {
        super.onPause()
        if (gjeldende === this) gjeldende = null
        Okt.lytter = null
        handler.removeCallbacks(tikk)
        getSystemService(LocationManager::class.java).removeUpdates(this)
    }

    private fun harPosisjon() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun lytt() {
        if (!harPosisjon()) return
        val lm = getSystemService(LocationManager::class.java)
        try {
            if (posisjon == null) lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { onLocationChanged(it) }
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0f, this, Looper.getMainLooper())
        } catch (_: Exception) {
        }
    }

    override fun onLocationChanged(l: Location) {
        posisjon = l
        igjen = Rute.igjen(Punkt(l.latitude, l.longitude))
        kart.posisjon(l)
        oppdater()
    }

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun bygg() {
        val ui = WaddleUi
        val k = this
        val p = ui.palette(k)
        val rot = ui.column(k, WaddleTokens.SPACE_3).apply { setBackgroundColor(p.bg) }
        rot.addView(Deler.tittel(k, "Tur", "gear", "Innstillinger") { startActivity(Intent(k, InnstillingerActivity::class.java)) })
        kart = Kart(k).apply {
            spor = Okt.punkter
            flyttet = { sentrer.visibility = View.VISIBLE }
        }
        val pad = ui.dp(k, WaddleTokens.SPACE_3)
        sentrer = ui.icon(k, "compass").apply {
            background = ui.outline(k, p.surface, p.border)
            setPadding(pad, pad, pad, pad)
            contentDescription = "Vis meg"
            visibility = View.GONE
            Deler.klikkbar(this) {
                if (!harPosisjon()) return@klikkbar spor()
                kart.sentrer()
                visibility = View.GONE
            }
        }
        rot.addView(Kart.ramme(k, kart, sentrer), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val tall = ui.row(k, WaddleTokens.SPACE_3)
        val v = ArrayList<TextView>()
        val e = ArrayList<TextView>()
        repeat(3) {
            tall.addView(ui.column(k, WaddleTokens.SPACE_1).apply {
                addView(ui.text(k, "", WaddleTokens.TEXT_LG, bold = true).also { v.add(it) })
                addView(ui.text(k, "", WaddleTokens.TEXT_SM, muted = true).also { e.add(it) })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        verdier = v
        etiketter = e
        rot.addView(tall)
        info = ui.text(k, "", WaddleTokens.TEXT_SM, muted = true)
        rot.addView(info)
        knapper = ui.column(k, WaddleTokens.SPACE_2)
        rot.addView(knapper)
        setContentView(rot)
        window.decorView.setBackgroundColor(p.bg)
        if (Build.VERSION.SDK_INT >= 30) {
            val lys = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (ui.isDark(k)) 0 else lys, lys)
        }
        Deler.tastatur(this)
    }

    private fun oppdater() {
        val lop = type == "running"
        verdier[0].text = Vis.km(Okt.lengde)
        etiketter[0].text = "Distanse"
        verdier[1].text = Vis.tid(Okt.tid())
        etiketter[1].text = "Tid"
        val igjen = igjen
        when {
            lop -> {
                verdier[2].text = if (Okt.aktiv) Okt.tempoNa() else Vis.tempo(Okt.tid(), Okt.lengde)
                etiketter[2].text = "Tempo /km"
            }
            igjen != null -> {
                verdier[2].text = Vis.km(igjen.first)
                etiketter[2].text = "Igjen av ruta"
            }
            else -> {
                verdier[2].text = Vis.fart(Okt.tid(), Okt.lengde)
                etiketter[2].text = "km/t"
            }
        }
        val linjer = ArrayList<String>()
        if (Okt.avbrutt) linjer.add("Opptaket ble stoppet av telefonen. Trykk Fortsett.")
        else if (Okt.pagar && !Okt.aktiv) linjer.add("Pause")
        val alder = posisjon?.let { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1000000 }
        if (Okt.aktiv && (alder == null || alder > 30000)) linjer.add("Venter på GPS")
        Rute.spor?.let { r -> linjer.add("Rute: " + r.navn.ifEmpty { "uten navn" } + " · " + Vis.km(r.lengde)) }
        if (igjen != null && igjen.second > 50) linjer.add("${igjen.second.toInt()} m fra ruta")
        info.text = linjer.joinToString(" · ")
        info.visibility = if (linjer.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun visKnapper() {
        val ui = WaddleUi
        val k = this
        knapper.removeAllViews()
        val fyll = { LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        if (!Okt.pagar) {
            knapper.addView(ui.row(k).apply {
                addView(ui.button(k, "Løp", primary = type == "running") { velg("running") }, fyll())
                addView(ui.button(k, "Tur", primary = type != "running") { velg("hiking") }, fyll())
            })
            knapper.addView(ui.button(k, "Start", primary = true) { start() })
            knapper.addView(ui.row(k).apply {
                if (Rute.spor == null) addView(ui.button(k, "Åpne rute") { velgFil() }, fyll())
                else addView(ui.button(k, "Fjern rute") {
                    Rute.fjern(k)
                    igjen = null
                    kart.rute = null
                    visKnapper()
                }, fyll())
                addView(ui.button(k, "Turer") { startActivity(Intent(k, TurerActivity::class.java)) }, fyll())
            })
        } else {
            knapper.addView(ui.row(k).apply {
                if (Okt.aktiv) addView(ui.button(k, "Pause") {
                    Okt.pause(k)
                    stopService(Intent(k, Opptak::class.java))
                    visKnapper()
                }, fyll())
                else addView(ui.button(k, "Fortsett", primary = true) {
                    if (!harPosisjon()) return@button spor()
                    Okt.fortsett(k)
                    startForegroundService(Intent(k, Opptak::class.java))
                    visKnapper()
                }, fyll())
                addView(ui.button(k, "Stopp") { stopp() }, fyll())
            })
        }
        if (Okt.aktiv && Innst.skjerm(k)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        oppdater()
    }

    private fun velg(t: String) {
        type = t
        Innst.sett(this, "type", t)
        visKnapper()
    }

    private fun spor() {
        val tilganger = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) tilganger.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(tilganger.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!harPosisjon()) {
            Toast.makeText(this, "Tur trenger posisjon for å vise deg på kartet.", Toast.LENGTH_LONG).show()
            return
        }
        lytt()
        kart.sentrer()
        sentrer.visibility = View.GONE
    }

    private fun start() {
        if (!harPosisjon()) return spor()
        if (!getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Slå på posisjon først.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        Okt.start(this, type)
        startForegroundService(Intent(this, Opptak::class.java))
        kart.sentrer()
        sentrer.visibility = View.GONE
        visKnapper()
    }

    private fun stopp() {
        if (Okt.punkter.size < 2) {
            AlertDialog.Builder(this)
                .setMessage("Ingen GPS-punkter ennå. Forkaste turen?")
                .setPositiveButton("Forkast") { _, _ ->
                    stopService(Intent(this, Opptak::class.java))
                    Okt.forkast(this)
                    visKnapper()
                    kart.invalidate()
                }
                .setNegativeButton("Fortsett", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Lagre turen?")
            .setMessage("${Vis.km(Okt.lengde)} på ${Vis.tid(Okt.tid())}")
            .setPositiveButton("Lagre") { _, _ ->
                stopService(Intent(this, Opptak::class.java))
                if (Okt.aktiv) Okt.pause(this)
                val fil = Okt.lagre(this)
                visKnapper()
                kart.invalidate()
                if (fil == null) Toast.makeText(this, "Fikk ikke lagret turen.", Toast.LENGTH_LONG).show()
                else startActivity(Intent(this, TurActivity::class.java).putExtra("fil", fil.path))
            }
            .setNeutralButton("Forkast") { _, _ ->
                AlertDialog.Builder(this)
                    .setMessage("Forkaste turen?")
                    .setPositiveButton("Forkast") { _, _ ->
                        stopService(Intent(this, Opptak::class.java))
                        Okt.forkast(this)
                        visKnapper()
                        kart.invalidate()
                    }
                    .setNegativeButton("Avbryt", null)
                    .show()
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun velgFil() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 2)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2 && resultCode == RESULT_OK) data?.data?.let { importer(it) }
    }

    private fun haandter(i: Intent) {
        if (i.getBooleanExtra("visRute", false)) {
            i.removeExtra("visRute")
            Rute.spor?.let { visRute(it) }
        }
        val uri = when (i.action) {
            Intent.ACTION_VIEW -> i.data
            @Suppress("DEPRECATION")
            Intent.ACTION_SEND -> i.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        } ?: return
        importer(uri)
    }

    private fun importer(uri: Uri) {
        Thread {
            val data = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            val s = data?.let { Rute.les(it) }
            handler.post {
                if (data == null || s == null) {
                    Toast.makeText(applicationContext, "Fant ingen rute i filen.", Toast.LENGTH_LONG).show()
                    return@post
                }
                Rute.bruk(applicationContext, data, s)
                gjeldende?.visRute(s)
            }
        }.start()
    }

    private fun visRute(s: Spor) {
        igjen = posisjon?.let { Rute.igjen(Punkt(it.latitude, it.longitude)) }
        kart.rute = s
        kart.vis(s.punkter + s.merker.map { Punkt(it.lat, it.lon) })
        sentrer.visibility = View.VISIBLE
        visKnapper()
    }
}
