package me.arnee.attester

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {
    private var orger: List<Org>? = null
    private var innsendinger: List<Innsending>? = null
    private val maler = mutableMapOf<String, Mal>()
    private var valgt: Innsending? = null
    private var velgOrg = false
    private var melding: String? = null
    private var jobber = false

    private val tid = DateTimeFormatter.ofPattern("d. MMM HH:mm", Locale.forLanguageTag("nb"))

    private fun lager() = getSharedPreferences("attester", Context.MODE_PRIVATE)

    private fun org(): Org? {
        val slug = lager().getString("org", null)
        val liste = orger ?: return null
        return liste.firstOrNull { it.slug == slug } ?: liste.singleOrNull()
    }

    override fun onResume() {
        super.onResume()
        if (Api.innlogget(this)) hentAlt() else vis()
    }

    private fun bakgrunn(arbeid: () -> Unit, ferdig: () -> Unit = {}) {
        jobber = true
        melding = null
        vis()
        Thread {
            val resultat = runCatching(arbeid)
            runOnUiThread {
                jobber = false
                resultat.onFailure {
                    when (it) {
                        is Utlogget -> {
                            Api.loggUt(this)
                            nullstill()
                            melding = "Du er logget ut. Logg inn igjen."
                        }
                        is Feil -> melding = it.message
                        else -> melding = "Fikk ikke kontakt med attester.no. Sjekk nettet."
                    }
                }.onSuccess { ferdig() }
                vis()
            }
        }.start()
    }

    private fun nullstill() {
        orger = null
        innsendinger = null
        valgt = null
        velgOrg = false
    }

    private fun hentAlt() {
        bakgrunn({
            val liste = Api.organisasjoner(this)
            runOnUiThread { orger = liste }
            val o = liste.firstOrNull { it.slug == lager().getString("org", null) } ?: liste.singleOrNull()
            if (o != null) hentInnsendinger(o)
        })
    }

    private fun hentInnsendinger(o: Org) {
        val liste = Api.innsendinger(this, o.slug)
        liste.map { it.mal }.distinct().filter { it !in maler }.forEach { id ->
            runCatching { Api.mal(o.slug, id) }.onSuccess { m -> runOnUiThread { maler[id] = m } }
        }
        runOnUiThread {
            innsendinger = liste
            valgt = valgt?.let { v -> liste.firstOrNull { it.id == v.id } }
        }
    }

    private fun navn(s: Innsending) =
        s.data["name"]?.takeIf { it.isNotBlank() } ?: s.data.values.firstOrNull { it.isNotBlank() } ?: "Uten navn"

    private fun dato(t: OffsetDateTime) = t.atZoneSameInstant(ZoneId.systemDefault()).format(tid)

    private fun timerIgjen(utstedt: OffsetDateTime): Long {
        val slettes = utstedt.plusHours(24)
        return maxOf(0, (Duration.between(OffsetDateTime.now(), slettes).toMinutes() + 59) / 60)
    }

    private fun apne(sti: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Api.NETTSTED + sti)))
    }

    private fun vis() {
        val k = this
        val innhold = Attest.kolonne(k, AttestTokens.AVSTAND_6)
        val o = org()
        when {
            !Api.innlogget(k) -> innlogging(innhold)
            orger == null -> {
                innhold.addView(Attest.merke(k, "attester.no"))
                innhold.addView(Attest.dempet(k, if (jobber) "Henter" else "Fikk ikke hentet organisasjonene."))
            }
            o == null || velgOrg -> organisasjoner(innhold)
            valgt != null -> detalj(innhold, o, valgt!!)
            else -> ko(innhold, o)
        }
        melding?.let { innhold.addView(Attest.tekst(k, it, farge = AttestTokens.DEMPET)) }
        Attest.skjerm(this, innhold)
    }

    private fun innlogging(innhold: LinearLayout) {
        val k = this
        innhold.addView(Attest.kolonne(k, AttestTokens.AVSTAND_2).apply {
            addView(Attest.merke(k, "attester.no"))
            addView(Attest.overskrift(k, "Logg inn", AttestTokens.TEKST_TITTEL))
            addView(Attest.tekst(k, "Bruk samme konto som på nettsiden.", AttestTokens.TEKST_INGRESS, AttestTokens.DEMPET))
        })
        val epost = Attest.epost(k).apply { setText(Api.epost(k)) }
        val passord = Attest.passord(k)
        innhold.addView(Attest.kolonne(k).apply {
            addView(epost)
            addView(passord)
            addView(Attest.knapp(k, if (jobber) "Logger inn" else "Logg inn", primaer = true) {
                if (jobber) return@knapp
                val e = epost.text.toString().trim()
                val p = passord.text.toString()
                if (e.isEmpty() || p.isEmpty()) {
                    melding = "Fyll inn e-post og passord."
                    vis()
                    return@knapp
                }
                bakgrunn({ Api.loggInn(k, e, p) }) { hentAlt() }
            })
            addView(Attest.knapp(k, "Glemt passord") { apne("/login/glemt") })
        })
    }

    private fun organisasjoner(innhold: LinearLayout) {
        val k = this
        innhold.addView(Attest.kolonne(k, AttestTokens.AVSTAND_2).apply {
            addView(Attest.merke(k, Api.epost(k)))
            addView(Attest.overskrift(k, "Velg organisasjon", AttestTokens.TEKST_TITTEL))
        })
        val liste = orger.orEmpty()
        if (liste.isEmpty()) {
            innhold.addView(Attest.dempet(k, "Du er ikke med i noen organisasjon ennå."))
        } else {
            innhold.addView(Attest.boks(k).apply {
                liste.forEach { o ->
                    addView(Attest.linje(k, o.navn, "/org/${o.slug}") {
                        lager().edit().putString("org", o.slug).apply()
                        velgOrg = false
                        innsendinger = null
                        valgt = null
                        bakgrunn({ hentInnsendinger(o) })
                    })
                }
            })
        }
        innhold.addView(Attest.knapp(k, "Logg ut") { loggUt() })
    }

    private fun ko(innhold: LinearLayout, o: Org) {
        val k = this
        val liste = innsendinger
        val venter = liste.orEmpty().filter { it.utstedt == null }.sortedByDescending { it.sendt }
        val utstedt = liste.orEmpty().filter { it.utstedt != null }.sortedByDescending { it.utstedt }
        innhold.addView(Attest.kolonne(k, AttestTokens.AVSTAND_2).apply {
            addView(Attest.merke(k, "attester.no/org/${o.slug}"))
            addView(Attest.overskrift(k, o.navn, AttestTokens.TEKST_TITTEL))
            addView(Attest.tekst(k, when {
                liste == null -> if (jobber) "Henter innsendinger" else "Fikk ikke hentet innsendinger."
                venter.isEmpty() -> "Ingen venter på attest."
                venter.size == 1 -> "1 venter på attest."
                else -> "${venter.size} venter på attest."
            }, AttestTokens.TEKST_INGRESS, AttestTokens.DEMPET))
        })
        if (venter.isNotEmpty()) innhold.addView(seksjon("Venter", venter.map { s ->
            Attest.linje(k, navn(s), listOfNotNull(maler[s.mal]?.navn, "sendt ${dato(s.sendt)}").joinToString(" · ")) { velg(s) }
        }))
        if (utstedt.isNotEmpty()) innhold.addView(seksjon("Utstedt", utstedt.map { s ->
            Attest.linje(k, navn(s), "utstedt ${dato(s.utstedt!!)} · slettes om ${timerIgjen(s.utstedt)} t") { velg(s) }
        }))
        innhold.addView(Attest.kolonne(k).apply {
            addView(Attest.knapp(k, if (jobber) "Oppdaterer" else "Oppdater", primaer = true) {
                if (!jobber) bakgrunn({ hentInnsendinger(o) })
            })
            addView(Attest.knapp(k, "Åpne på nettet") { apne("/login/adminpage/${o.slug}") })
            if (orger.orEmpty().size > 1) addView(Attest.knapp(k, "Bytt organisasjon") {
                velgOrg = true
                vis()
            })
            addView(Attest.knapp(k, "Logg ut") { loggUt() })
        })
    }

    private fun seksjon(tittel: String, rader: List<View>) = Attest.kolonne(this, AttestTokens.AVSTAND_2).apply {
        addView(Attest.overskrift(this@MainActivity, tittel))
        addView(Attest.boks(this@MainActivity).apply { rader.forEach { addView(it) } })
    }

    private fun velg(s: Innsending) {
        valgt = s
        melding = null
        vis()
    }

    private fun detalj(innhold: LinearLayout, o: Org, s: Innsending) {
        val k = this
        val mal = maler[s.mal]
        innhold.addView(Attest.kolonne(k, AttestTokens.AVSTAND_2).apply {
            addView(Attest.merke(k, mal?.navn ?: "Innsending"))
            addView(Attest.overskrift(k, navn(s), AttestTokens.TEKST_TITTEL))
            addView(Attest.tekst(k, if (s.utstedt == null) "Sendt ${dato(s.sendt)}. Venter på attest."
            else "Utstedt ${dato(s.utstedt)}. Dataene slettes om ${timerIgjen(s.utstedt)} timer.", AttestTokens.TEKST_INGRESS, AttestTokens.DEMPET))
        })
        val felt = mal?.felt?.filter { !s.data[it.nokkel].isNullOrBlank() }?.map { it.navn to s.data[it.nokkel]!! }
            ?: s.data.filterValues { it.isNotBlank() }.map { it.key to it.value }
        val ekstra = s.data.filter { (n, v) -> v.isNotBlank() && mal != null && mal.felt.none { it.nokkel == n } }.map { it.key to it.value }
        innhold.addView(Attest.boks(k).apply {
            (felt + ekstra).forEach { (n, v) ->
                addView(Attest.kolonne(k, AttestTokens.AVSTAND_1).apply {
                    val pad = Attest.dp(k, AttestTokens.AVSTAND_3)
                    setPadding(0, pad, 0, pad)
                    addView(Attest.merke(k, n))
                    addView(Attest.tekst(k, v).apply { setTextIsSelectable(true) })
                })
            }
        })
        innhold.addView(Attest.kolonne(k).apply {
            addView(Attest.knapp(k, if (s.utstedt == null) "Utsted på nettet" else "Lag PDF på nettet", primaer = true) {
                apne("/login/adminpage/${o.slug}")
            })
            addView(Attest.knapp(k, "Slett data") { slett(o, s) })
            addView(Attest.knapp(k, "Tilbake") {
                valgt = null
                vis()
            })
        })
    }

    private fun slett(o: Org, s: Innsending) {
        AlertDialog.Builder(this)
            .setMessage("Slette dataene til ${navn(s)}? Det kan ikke angres.")
            .setPositiveButton("Slett") { _, _ ->
                bakgrunn({ Api.slett(this, o.slug, s.id) }) {
                    innsendinger = innsendinger?.filter { it.id != s.id }
                    valgt = null
                    melding = "Slettet."
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun loggUt() {
        Api.loggUt(this)
        nullstill()
        melding = null
        vis()
    }

    override fun onBackPressed() {
        when {
            valgt != null -> valgt = null
            velgOrg && org() != null -> velgOrg = false
            else -> {
                @Suppress("DEPRECATION") super.onBackPressed()
                return
            }
        }
        vis()
    }
}
