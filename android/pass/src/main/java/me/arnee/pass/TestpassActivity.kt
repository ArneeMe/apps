package me.arnee.pass

import android.app.Activity
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import java.util.Calendar
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class TestpassActivity : Activity() {
    private val felt = linkedMapOf(
        "Fornavn" to "Ola",
        "Etternavn" to "Nordmann",
        "Fødselsdato" to "01011990",
        "Nasjonalitet" to "NOR",
        "Kjønn" to "M",
        "Utløpsdato" to "01012034",
        "Passnummer" to "ZP1111111",
    )

    private fun fodt(aarSiden: Int): String {
        val c = Calendar.getInstance().apply { add(Calendar.YEAR, -aarSiden) }
        return "%02d%02d%04d".format(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))
    }

    private val maler = listOf(
        "Voksen" to mapOf("Fornavn" to "Ola", "Etternavn" to "Nordmann", "Fødselsdato" to fodt(34), "Nasjonalitet" to "NOR", "Kjønn" to "M"),
        "17 år" to mapOf("Fornavn" to "Kari", "Etternavn" to "Nordmann", "Fødselsdato" to fodt(17), "Nasjonalitet" to "NOR", "Kjønn" to "F"),
        "12 år" to mapOf("Fornavn" to "Per", "Etternavn" to "Hansen", "Fødselsdato" to fodt(12), "Nasjonalitet" to "NOR", "Kjønn" to "M"),
        "Svensk" to mapOf("Fornavn" to "Anna", "Etternavn" to "Svensson", "Fødselsdato" to fodt(25), "Nasjonalitet" to "SWE", "Kjønn" to "F"),
    )

    override fun onResume() {
        super.onResume()
        vis()
    }

    private fun lag() {
        val f = Dato.fraVisning(felt.getValue("Fødselsdato"))
        val u = Dato.fraVisning(felt.getValue("Utløpsdato"))
        if (f == null || u == null) {
            android.widget.Toast.makeText(this, "Skriv datoer som DDMMÅÅÅÅ.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val sert = resources.openRawResource(R.raw.zkr_dsc).readBytes()
        val nokkel = Testpass.nokkel(resources.openRawResource(R.raw.zkr_dsc_nokkel).readBytes())
        val d = Testpass.lag(
            felt.getValue("Etternavn"), felt.getValue("Fornavn"), felt.getValue("Passnummer"),
            felt.getValue("Nasjonalitet"), f, felt.getValue("Kjønn"), u, sert, nokkel,
        )
        val id = Lommebok.lagre(this, d)
        startActivity(Intent(this, DetaljActivity::class.java).putExtra("id", id))
        finish()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(ui.text(k, "Lag testpass", WaddleTokens.TEXT_XL, bold = true))
            addView(ui.text(k, "Utstedt av Zero Knowledge Republic (ZKR) og signert med ZKPassport sin offentlige testnøkkel. Godtas bare av nettsider i testmodus.", WaddleTokens.TEXT_SM, muted = true))
            addView(ui.row(k, WaddleTokens.SPACE_1).apply {
                maler.forEach { (navn, verdier) ->
                    addView(ui.button(k, navn) {
                        felt.putAll(verdier)
                        vis()
                    }.apply {
                        minWidth = 0
                        minimumWidth = 0
                        textSize = WaddleTokens.TEXT_SM
                        setPadding(WaddleUi.dp(k, WaddleTokens.SPACE_2), 0, WaddleUi.dp(k, WaddleTokens.SPACE_2), 0)
                    })
                }
            })
            addView(ui.box(k).apply {
                felt.forEach { (navn, verdi) ->
                    addView(ui.text(k, navn, WaddleTokens.TEXT_SM, muted = true))
                    addView(Deler.felt(k, navn).apply {
                        setText(verdi)
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: Editable?) {
                                felt[navn] = s.toString()
                            }
                        })
                    })
                }
                addView(ui.button(k, "Lag", primary = true) { lag() })
            })
        }
        ui.screen(this, innhold)
        Deler.tastatur(this)
    }
}
