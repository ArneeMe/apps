package me.arnee.pass

import android.app.Activity
import android.app.AlertDialog
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class DetaljActivity : Activity() {

    override fun onResume() {
        super.onResume()
        vis()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val l = Lommebok.hent(k, intent.getStringExtra("id") ?: "") ?: return finish()
        val d = l.dokument
        val p = d.person
        val sjekk = d.sjekk()
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, if (d.test) "Testpass" else "Pass"))
            addView(ui.box(k).apply {
                listOf(
                    "Navn" to "${p.fornavn} ${p.etternavn}",
                    "Født" to "${Dato.vis(p.fodt, true)}, ${Dato.alder(p.fodt)} år",
                    "Nasjonalitet" to p.nasjonalitet,
                    "Utstedt av" to p.utsteder,
                    "Kjønn" to p.kjonn,
                    "Passnummer" to p.nummer,
                    "Utløper" to Dato.vis(p.utloper, false),
                ).forEachIndexed { i, (a, b) ->
                    if (i > 0) addView(ui.divider(k))
                    addView(Deler.verdi(k, a, b))
                }
            })
            addView(ui.box(k).apply {
                addView(ui.text(k, "Ekthet", WaddleTokens.TEXT_LG, bold = true))
                if (sjekk.feil != null) {
                    addView(ui.text(k, "Kunne ikke sjekke: ${sjekk.feil}", muted = true))
                } else {
                    addView(ui.listRow(k, if (sjekk.dataUendret) "Data er uendret" else "Data stemmer ikke med signaturen", "Hash av persondata mot SOD", iconName = if (sjekk.dataUendret) "check" else "close"))
                    addView(ui.divider(k))
                    addView(ui.listRow(k, if (sjekk.signaturGyldig) "Signaturen er gyldig" else "Signaturen er ugyldig", "${sjekk.algoritme}, ${sjekk.utsteder}", iconName = if (sjekk.signaturGyldig) "check" else "close"))
                    addView(ui.divider(k))
                    addView(ui.listRow(k, "Landets rotsertifikat er ikke sjekket ennå", if (d.test) "Testpass er signert av ZKPassport sitt test-CSCA for ZKR" else "Kommer med ICAO sin masterliste", iconName = "info-box"))
                }
            })
            addView(ui.button(k, "Slett") {
                AlertDialog.Builder(k)
                    .setMessage("Slette ${if (d.test) "testpasset" else "passet"} fra telefonen?")
                    .setPositiveButton("Slett") { _, _ ->
                        Lommebok.slett(k, l.id)
                        finish()
                    }
                    .setNegativeButton("Avbryt", null)
                    .show()
            })
        }
        ui.screen(this, innhold)
    }
}
