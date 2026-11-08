package me.arnee.pass

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class LesActivity : Activity() {
    private var nummer = ""
    private var fodt = ""
    private var utloper = ""
    private var status: String? = null
    private var venter = false

    override fun onResume() {
        super.onResume()
        if (venter) start()
        vis()
    }

    override fun onPause() {
        super.onPause()
        NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this)
    }

    private fun felt(hint: String, verdi: String, tall: Boolean, endret: (String) -> Unit): EditText = Deler.felt(this, hint).apply {
        setText(verdi)
        if (tall) inputType = InputType.TYPE_CLASS_NUMBER
        else inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = endret(s.toString())
        })
    }

    private fun start() {
        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter == null) {
            status = "Telefonen har ikke NFC."
            vis()
            return
        }
        if (!adapter.isEnabled) {
            status = "NFC er slått av. Slå det på og prøv igjen."
            vis()
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            return
        }
        val dok = nummer.trim().uppercase()
        val f = Dato.fraVisning(fodt)
        val u = Dato.fraVisning(utloper)
        if (dok.length < 6 || f == null || u == null) {
            status = "Fyll inn passnummer og datoer som DDMMÅÅÅÅ."
            vis()
            return
        }
        venter = true
        status = "Hold passet flatt mot baksiden av telefonen. Rør det ikke før det er ferdig."
        vis()
        adapter.enableReaderMode(this, { tag ->
            val kort = IsoDep.get(tag) ?: return@enableReaderMode
            val resultat = runCatching {
                Leser(kort) { steg -> runOnUiThread { status = steg; vis() } }.les(dok, f, u)
            }
            runOnUiThread {
                resultat.onSuccess { d ->
                    venter = false
                    adapter.disableReaderMode(this)
                    val id = Lommebok.lagre(this, d)
                    startActivity(Intent(this, DetaljActivity::class.java).putExtra("id", id))
                    finish()
                }.onFailure {
                    status = "${it.message ?: it.javaClass.simpleName}. Hold passet stille og prøv igjen."
                    vis()
                }
            }
        }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS, null)
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(ui.text(k, "Les pass", WaddleTokens.TEXT_XL, bold = true))
            addView(ui.box(k).apply {
                addView(ui.text(k, "Fra siden med bildet ditt. Brukes bare til å låse opp brikken.", WaddleTokens.TEXT_SM, muted = true))
                addView(ui.text(k, "Passnummer", WaddleTokens.TEXT_SM, muted = true))
                addView(felt("AB1234567", nummer, false) { nummer = it })
                addView(ui.text(k, "Fødselsdato", WaddleTokens.TEXT_SM, muted = true))
                addView(felt("DDMMÅÅÅÅ", fodt, true) { fodt = it })
                addView(ui.text(k, "Utløpsdato", WaddleTokens.TEXT_SM, muted = true))
                addView(felt("DDMMÅÅÅÅ", utloper, true) { utloper = it })
                addView(ui.button(k, if (venter) "Venter på passet" else "Start", primary = true) { start() })
            })
            status?.let { addView(ui.text(k, it)) }
        }
        ui.screen(this, innhold)
        Deler.tastatur(this)
    }
}
