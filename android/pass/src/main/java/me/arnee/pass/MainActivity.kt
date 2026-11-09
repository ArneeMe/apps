package me.arnee.pass

import android.app.Activity
import android.content.Intent
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class MainActivity : Activity() {

    override fun onResume() {
        super.onResume()
        vis()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val pass = Lommebok.alle(k)
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Pass"))
            val lenke = getSystemService(android.content.ClipboardManager::class.java).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(k)?.toString()?.trim()?.takeIf { it.contains("zkpassport") && it.contains("?") }
            if (lenke != null) {
                addView(ui.button(k, "Åpne forespørsel fra utklipp", primary = true) {
                    startActivity(Intent(k, ForesporselActivity::class.java).putExtra("lenke", lenke))
                })
            }
            if (pass.isEmpty()) {
                addView(ui.text(k, "Ingen pass ennå.", muted = true))
            } else {
                addView(ui.box(k).apply {
                    pass.forEachIndexed { i, l ->
                        if (i > 0) addView(ui.divider(k))
                        val p = l.dokument.person
                        val merke = if (l.dokument.test) "Testpass" else p.nasjonalitet
                        addView(Deler.klikkbar(ui.listRow(k, "${p.fornavn} ${p.etternavn}".lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }, "$merke · utløper ${Dato.vis(p.utloper, false)}", iconName = "membercard")) {
                            startActivity(Intent(k, DetaljActivity::class.java).putExtra("id", l.id))
                        })
                    }
                })
            }
            addView(ui.row(k).apply {
                addView(ui.button(k, "Les pass", primary = lenke == null) { startActivity(Intent(k, LesActivity::class.java)) })
                addView(ui.button(k, "Lag testpass") { startActivity(Intent(k, TestpassActivity::class.java)) })
            })
        }
        ui.screen(this, innhold)
    }
}
