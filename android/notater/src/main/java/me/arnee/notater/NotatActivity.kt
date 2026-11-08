package me.arnee.notater

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import java.io.File
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class NotatActivity : Activity() {
    private lateinit var fil: File
    private lateinit var felt: EditText
    private var slettet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fil = Lager.notat(this, intent.getStringExtra("navn") ?: return finish())
        val ui = WaddleUi
        val k = this
        felt = Deler.felt(k, "Skriv", flerlinje = true).apply {
            setText(if (fil.exists()) fil.readText() else "")
            gravity = Gravity.TOP or Gravity.START
            minLines = 12
        }
        val innhold = ui.column(k, WaddleTokens.SPACE_4).apply {
            addView(felt)
            addView(ui.row(k).apply {
                addView(ui.button(k, "Ferdig", primary = true) { finish() })
                addView(ui.button(k, "Slett") {
                    slettet = true
                    fil.delete()
                    finish()
                })
            })
        }
        ui.screen(this, innhold)
        Deler.tastatur(this)
        if (felt.text.isEmpty()) felt.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        if (slettet) return
        val tekst = felt.text.toString()
        if (tekst.isBlank()) fil.delete() else if (!fil.exists() || fil.readText() != tekst) fil.writeText(tekst)
    }
}
