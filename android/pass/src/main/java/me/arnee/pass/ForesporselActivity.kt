package me.arnee.pass

import android.app.Activity
import android.os.Bundle
import org.json.JSONObject
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class ForesporselActivity : Activity() {
    private var f: Foresporsel? = null
    private var bro: Bro? = null
    private var status = "Kobler til"
    private var valgt: String? = null
    private var ferdig = false
    private var feil: String? = null
    private var motor: BevisMotor? = null
    private var bevis: JSONObject? = null
    private var bevisFor: String? = null
    private var framdrift: String? = null
    private var skalSende = false
    private var start = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lenke = intent.dataString ?: intent.getStringExtra("lenke") ?: return finish()
        f = runCatching { Foresporsel.fra(lenke) }.getOrElse {
            feil = "Lenken kunne ikke leses: ${it.message}"
            null
        }
        f?.let { koble(it) }
        motor = BevisMotor(this, object : BevisMotor.Lytter {
            override fun klar(isolert: Boolean) {
                if (!isolert) framdrift = "Bevismotoren kjører uten tråder, så det går tregere."
                valgt?.let { lag(it) }
                vis()
            }

            override fun framdrift(tekst: String) {
                framdrift = tekst
                vis()
            }

            override fun ferdig(svar: JSONObject) {
                if (bevisFor != valgt) return
                bevis = svar
                framdrift = "Bevis klare på ${(System.currentTimeMillis() - start) / 1000} s"
                if (skalSende) sendBevis()
                vis()
            }

            override fun feil(melding: String) {
                framdrift = "Kunne ikke lage bevis: $melding"
                if (skalSende) send("error", JSONObject().put("error", "Cannot generate proof: $melding"), "Sendt beskjed om at beviset ikke kunne lages.")
                vis()
            }
        })
    }

    private fun lag(id: String) {
        val forespørsel = f ?: return
        val m = motor ?: return
        val l = Lommebok.hent(this, id) ?: return
        bevis = null
        bevisFor = id
        start = System.currentTimeMillis()
        if (!m.erKlar) {
            framdrift = "Starter bevismotoren"
            return
        }
        framdrift = "Starter"
        m.lag(l.dokument.dg1, l.dokument.sod, forespørsel.sporring, forespørsel.domene, forespørsel.test)
    }

    private fun sendBevis() {
        val b = bro ?: return
        val svar = bevis ?: return
        skalSende = false
        ferdig = true
        Thread {
            val r = runCatching {
                val liste = svar.getJSONArray("bevis")
                for (i in 0 until liste.length()) b.send("proof", liste.getJSONObject(i))
                b.send("done", svar.getJSONObject("resultat"))
            }
            runOnUiThread {
                status = r.exceptionOrNull()?.let { "Fikk ikke sendt: ${it.message}" } ?: "Sendt. Sjekk nettsiden."
                vis()
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        vis()
    }

    override fun onDestroy() {
        super.onDestroy()
        Thread { bro?.lukk() }.start()
        motor?.lukk()
    }

    private fun koble(f: Foresporsel) {
        val b = Bro(f) { metode, params ->
            runOnUiThread {
                when (metode) {
                    "hello" -> status = "Koblet til ${f.domene}"
                    "brudd" -> if (!ferdig) status = "Mistet forbindelsen: ${params?.optString("melding")}"
                    "lukket" -> if (!ferdig) status = "Forbindelsen er lukket"
                    "error" -> status = "Siden svarte med feil: ${params?.toString()}"
                }
                vis()
            }
        }
        bro = b
        Thread {
            runCatching { b.koble() }.onFailure { e -> runOnUiThread { status = "Fikk ikke koblet til: ${e.message}"; vis() } }
        }.start()
    }

    private fun send(metode: String, params: JSONObject = JSONObject(), etter: String) {
        val b = bro ?: return
        ferdig = true
        Thread {
            val r = runCatching { b.send(metode, params) }
            runOnUiThread {
                status = r.exceptionOrNull()?.let { "Fikk ikke sendt: ${it.message}" } ?: etter
                vis()
            }
        }.start()
    }

    private fun linjer(s: JSONObject): List<String> {
        val ut = mutableListOf<String>()
        val navn = mapOf(
            "age" to "Alder", "birthdate" to "Fødselsdato", "expiry_date" to "Utløpsdato", "nationality" to "Nasjonalitet",
            "firstname" to "Fornavn", "lastname" to "Etternavn", "fullname" to "Fullt navn", "document_number" to "Passnummer",
            "document_type" to "Dokumenttype", "issuing_country" to "Utstederland", "gender" to "Kjønn",
        )
        s.keys().forEach { felt ->
            val krav = s.optJSONObject(felt) ?: return@forEach
            val n = navn[felt] ?: felt
            krav.keys().forEach { op ->
                val v = krav.opt(op)
                ut += when (op) {
                    "disclose" -> "$n vises"
                    "gte" -> "$n minst $v"
                    "gt" -> "$n over $v"
                    "lte" -> "$n høyst $v"
                    "lt" -> "$n under $v"
                    "eq" -> "$n er $v (vises)"
                    "in" -> "$n er en av $v"
                    "out" -> "$n er ikke en av $v"
                    "range" -> "$n mellom $v"
                    else -> "$n: $op $v"
                }
            }
        }
        return ut
    }

    private fun svar(f: Foresporsel, p: Person): List<String> {
        val ut = mutableListOf<String>()
        val s = f.sporring
        s.optJSONObject("age")?.let { a ->
            val alder = Dato.alder(p.fodt)
            a.optInt("gte", -1).takeIf { it >= 0 }?.let { ut += "Alder minst $it: ${if (alder >= it) "ja" else "nei"}" }
        }
        if (s.optJSONObject("nationality") != null) ut += "Nasjonalitet: ${p.nasjonalitet}"
        if (s.optJSONObject("firstname") != null) ut += "Fornavn: ${p.fornavn}"
        if (s.optJSONObject("lastname") != null) ut += "Etternavn: ${p.etternavn}"
        if (s.optJSONObject("fullname") != null) ut += "Navn: ${p.fornavn} ${p.etternavn}"
        return ut
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val forespørsel = f
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Forespørsel"))
            if (forespørsel == null) {
                addView(ui.text(k, feil ?: "Ingen forespørsel.", muted = true))
                return@apply
            }
            addView(ui.box(k).apply {
                val navn = forespørsel.tjeneste.optString("name").ifEmpty { forespørsel.domene }
                addView(ui.listRow(k, navn, "${forespørsel.domene}${if (forespørsel.test) " · testmodus" else ""}", iconName = "shield"))
                forespørsel.tjeneste.optString("purpose").takeIf { it.isNotEmpty() }?.let { addView(ui.text(k, it, muted = true)) }
                addView(ui.divider(k))
                addView(ui.text(k, "Ber om", WaddleTokens.TEXT_SM, muted = true))
                linjer(forespørsel.sporring).forEach { addView(ui.text(k, it)) }
                addView(ui.text(k, status, WaddleTokens.TEXT_SM, muted = true))
            })
            val pass = Lommebok.alle(k).filter { it.dokument.test == forespørsel.test }
            if (valgt == null && pass.size == 1 && !ferdig) {
                valgt = pass[0].id
                lag(pass[0].id)
            }
            addView(ui.box(k).apply {
                addView(ui.text(k, if (pass.size == 1) "Pass" else if (forespørsel.test) "Velg testpass" else "Velg pass", WaddleTokens.TEXT_LG, bold = true))
                if (pass.isEmpty()) addView(ui.text(k, if (forespørsel.test) "Du har ingen testpass. Lag et i Pass-appen." else "Du har ikke lest inn passet ditt ennå.", muted = true))
                pass.forEach { l ->
                    val p = l.dokument.person
                    addView(Deler.klikkbar(ui.listRow(k, "${p.fornavn} ${p.etternavn}", "${p.nasjonalitet} · født ${Dato.vis(p.fodt, true)}", iconName = if (valgt == l.id) "check" else "membercard")) {
                        if (valgt != l.id && !ferdig) {
                            valgt = l.id
                            lag(l.id)
                        }
                        vis()
                    })
                }
                pass.firstOrNull { it.id == valgt }?.let { l ->
                    addView(ui.divider(k))
                    addView(ui.text(k, "Dette får siden vite", WaddleTokens.TEXT_SM, muted = true))
                    svar(forespørsel, l.dokument.person).forEach { addView(ui.text(k, it)) }
                    addView(ui.text(k, "Ingenting annet fra passet sendes.", WaddleTokens.TEXT_SM, muted = true))
                }
            })
            framdrift?.let { addView(ui.text(k, it, WaddleTokens.TEXT_SM, muted = true)) }
            if (!ferdig) {
                addView(ui.row(k).apply {
                    addView(ui.button(k, if (skalSende) "Sender når klart" else "Del", primary = valgt != null) {
                        if (valgt == null || bro?.klar != true || skalSende) return@button
                        skalSende = true
                        Thread { runCatching { bro?.send("accept") } }.start()
                        if (bevis != null) sendBevis()
                        vis()
                    })
                    addView(ui.button(k, "Avslå") { send("reject", etter = "Avslått") })
                })
            }
            motor?.visning?.let { w ->
                (w.parent as? android.view.ViewGroup)?.removeView(w)
                addView(w, android.widget.LinearLayout.LayoutParams(1, 1))
            }
        }
        ui.screen(this, innhold)
    }
}
