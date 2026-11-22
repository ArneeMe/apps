package me.arnee.tur

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt
import kotlin.random.Random
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class PlanActivity : Activity(), LocationListener {
    private lateinit var kart: Kart
    private lateinit var status: TextView
    private lateinit var knapper: LinearLayout
    private var modus = "rundtur"
    private var km = 5
    private var her: Punkt? = null
    private val punkter = ArrayList<Punkt>()
    private var forslag: Forslag? = null
    private var jobb = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        km = getSharedPreferences("innst", MODE_PRIVATE).getInt("km", 5)
        kart = Kart(this).apply { trykk = { leggTil(it) } }
        bygg()
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = getSystemService(LocationManager::class.java)
        try {
            if (her == null) lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { onLocationChanged(it) }
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0f, this, Looper.getMainLooper())
        } catch (_: Exception) {
        }
    }

    override fun onPause() {
        super.onPause()
        getSystemService(LocationManager::class.java).removeUpdates(this)
    }

    override fun onLocationChanged(l: Location) {
        val forste = her == null
        her = Punkt(l.latitude, l.longitude)
        kart.posisjon(l)
        if (forste) vis()
    }

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun type() = Innst.type(this)

    private fun bygg() {
        val ui = WaddleUi
        val k = this
        status = ui.text(k, "", muted = true)
        knapper = ui.column(k, WaddleTokens.SPACE_3)
        ui.screen(k, ui.column(k, WaddleTokens.SPACE_4).apply {
            addView(Deler.tittel(k, "Planlegg"))
            addView(Kart.ramme(k, kart), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, resources.displayMetrics.heightPixels * 9 / 20))
            addView(status)
            addView(knapper)
        })
        vis()
    }

    private fun start() = if (modus == "rundtur") punkter.lastOrNull() ?: her else her

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val fyll = { LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        knapper.removeAllViews()
        val f = forslag
        val merker = (if (modus == "rundtur") listOfNotNull(start()) else punkter).mapIndexed { i, p -> Merke(p.lat, p.lon, if (modus == "rundtur") "Start" else "${i + 1}") }
        kart.rute = Spor(f?.spor?.navn ?: "", "", f?.spor?.punkter ?: emptyList(), (f?.spor?.merker ?: emptyList()) + merker)
        status.text = when {
            jobb > 0 -> "Lager ruta"
            f != null -> "${Vis.km(f.spor.lengde)} · ca ${Vis.tid(Ruting.tid(f.spor, type()))} · ${f.spor.stigning.roundToInt()} høydemeter"
            modus == "rundtur" && start() == null -> "Venter på posisjon. Eller trykk på kartet der turen skal starte."
            modus == "rundtur" -> "Trykk på kartet for å starte et annet sted."
            punkter.isEmpty() -> "Trykk på kartet der du vil gå. Flere trykk gir punkter underveis."
            else -> "${punkter.size} punkt. Ruta går ${if (her != null) "fra der du er" else "fra punkt 1"}."
        }
        knapper.addView(ui.row(k).apply {
            addView(ui.button(k, "Rundtur", primary = modus == "rundtur") { bytt("rundtur") }, fyll())
            addView(ui.button(k, "Til et sted", primary = modus == "sted") { bytt("sted") }, fyll())
        })
        if (modus == "rundtur") {
            knapper.addView(Deler.verdi(k, "Lengde", "$km km"))
            knapper.addView(Deler.glider(k, 41, km - 1) {
                km = it + 1
                getSharedPreferences("innst", MODE_PRIVATE).edit().putInt("km", km).apply()
                forslag = null
                vis()
            })
            knapper.addView(ui.row(k).apply {
                addView(ui.button(k, if (f == null) "Lag rundtur" else "Ny variant", primary = f == null) { rundtur() }, fyll())
                if (f != null) addView(ui.button(k, "Bruk", primary = true) { bruk(f) }, fyll())
            })
        } else {
            knapper.addView(ui.row(k).apply {
                addView(ui.button(k, "Lag rute", primary = f == null) { tilSted() }, fyll())
                if (f != null) addView(ui.button(k, "Bruk", primary = true) { bruk(f) }, fyll())
            })
            if (punkter.isNotEmpty()) knapper.addView(ui.row(k).apply {
                addView(ui.button(k, "Angre") {
                    punkter.removeAt(punkter.size - 1)
                    forslag = null
                    vis()
                }, fyll())
                addView(ui.button(k, "Tøm") {
                    punkter.clear()
                    forslag = null
                    vis()
                }, fyll())
            })
        }
        knapper.addView(ui.text(k, "Rutene lages av BRouter på brouter.de, med kartdata fra OpenStreetMap. Bare punktene sendes.", WaddleTokens.TEXT_SM, muted = true))
        knapper.addView(ui.divider(k))
        knapper.addView(ui.button(k, "Åpne GPX-fil") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 1)
        })
        knapper.addView(ui.button(k, "Finn tur på ut.no") {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ut.no/turforslag"))) }
        })
        knapper.addView(ui.text(k, "På ut.no: åpne en tur, trykk Last ned GPX og åpne filen med Tur.", WaddleTokens.TEXT_SM, muted = true))
        if (Rute.spor != null) knapper.addView(ui.button(k, "Fjern ruta du følger") {
            Rute.fjern(k)
            Toast.makeText(k, "Ruta er fjernet.", Toast.LENGTH_SHORT).show()
            vis()
        })
    }

    private fun bytt(m: String) {
        if (modus == m) return
        modus = m
        punkter.clear()
        forslag = null
        vis()
    }

    private fun leggTil(p: Punkt) {
        if (jobb > 0) return
        if (modus == "rundtur") punkter.clear()
        punkter.add(p)
        forslag = null
        vis()
    }

    private fun rundtur() {
        val s = start() ?: return Toast.makeText(this, "Trykk på kartet der turen skal starte.", Toast.LENGTH_LONG).show()
        val navn = "Rundtur $km km"
        lag { Ruting.rundtur(s, km, Random.nextInt(360), navn) }
    }

    private fun tilSted() {
        val alle = listOfNotNull(her) + punkter
        if (alle.size < 2) return Toast.makeText(this, "Trykk på kartet der du vil gå.", Toast.LENGTH_LONG).show()
        lag { Ruting.mellom(alle, "Til punkt ${punkter.size}") }
    }

    private fun lag(arbeid: () -> Forslag) {
        if (jobb > 0) return
        jobb++
        vis()
        Thread {
            val resultat = runCatching(arbeid)
            runOnUiThread {
                jobb--
                resultat.onSuccess {
                    forslag = it
                    kart.vis(it.spor.punkter)
                }.onFailure {
                    Toast.makeText(this, "Fikk ikke laget ruta: ${it.message ?: "ingen nett?"}", Toast.LENGTH_LONG).show()
                }
                if (isDestroyed) return@runOnUiThread
                vis()
            }
        }.start()
    }

    private fun bruk(f: Forslag) {
        Rute.bruk(this, f.data, f.spor)
        startActivity(Intent(this, MainActivity::class.java).putExtra("visRute", true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != 1 || resultCode != RESULT_OK) return
        lag {
            val innhold = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Kunne ikke lese filen.")
            val s = Rute.les(innhold) ?: throw IllegalStateException("Fant ingen rute i filen.")
            Forslag(innhold, s)
        }
    }
}
