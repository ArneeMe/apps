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
            addView(ui.row(k).apply {
                addView(ui.button(k, "Les pass", primary = true) { startActivity(Intent(k, LesActivity::class.java)) })
                addView(ui.button(k, "Lag testpass") { startActivity(Intent(k, TestpassActivity::class.java)) })
            })
            if (pass.isEmpty()) {
                addView(ui.text(k, "Ingen pass ennå. Les ditt eget, eller lag et testpass for å prøve.", muted = true))
            } else {
                addView(ui.box(k).apply {
                    pass.forEachIndexed { i, l ->
                        if (i > 0) addView(ui.divider(k))
                        val p = l.dokument.person
                        val merke = if (l.dokument.test) "Test" else "Ekte"
                        addView(Deler.klikkbar(ui.listRow(k, "${p.fornavn} ${p.etternavn}", "$merke · ${p.nasjonalitet} · utløper ${Dato.vis(p.utloper, false)}", iconName = "membercard")) {
                            startActivity(Intent(k, DetaljActivity::class.java).putExtra("id", l.id))
                        })
                    }
                })
            }
            addView(ui.button(k, "Lim inn lenke fra nettside") {
                val tekst = getSystemService(android.content.ClipboardManager::class.java).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(k)?.toString().orEmpty()
                if (tekst.contains("zkpassport")) {
                    startActivity(Intent(k, ForesporselActivity::class.java).putExtra("lenke", tekst.trim()))
                } else {
                    android.widget.Toast.makeText(k, "Kopier lenken fra nettsiden først.", android.widget.Toast.LENGTH_LONG).show()
                }
            })
            addView(ui.text(k, "Passdata lagres kryptert på telefonen og sendes aldri videre. Til nettsider går bare bevis på det du velger å dele.", WaddleTokens.TEXT_SM, muted = true))
        }
        ui.screen(this, innhold)
    }
}
