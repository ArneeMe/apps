package me.arnee.sms

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.provider.BlockedNumberContract
import android.provider.Settings
import android.widget.Toast
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class InnstillingerActivity : Activity() {
    private var nyttNummer = ""

    override fun onResume() {
        super.onResume()
        vis()
    }

    private fun bryter(tittel: String, forklaring: String, navn: String, verdi: Boolean) =
        Deler.klikkbar(WaddleUi.listRow(this, tittel, "${if (verdi) "På" else "Av"}. $forklaring")) {
            Innstillinger.sett(this, navn, !verdi)
            vis()
        }

    private fun blokkerte(): List<Pair<Long, String>> = runCatching {
        val liste = mutableListOf<Pair<Long, String>>()
        contentResolver.query(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI,
            arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ID, BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
            null, null, null,
        )?.use { c -> while (c.moveToNext()) liste += c.getLong(0) to (c.getString(1) ?: "") }
        liste
    }.getOrNull() ?: emptyList()

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(ui.text(k, "Innstillinger", WaddleTokens.TEXT_XL, bold = true))

            addView(ui.box(k).apply {
                addView(ui.text(k, "Varsler", WaddleTokens.TEXT_LG, bold = true))
                addView(bryter("Vis innhold", "Viser teksten i varselet.", "vis_innhold", Innstillinger.visInnhold(k)))
                addView(ui.divider(k))
                addView(bryter("Svar fra varselet", "Svar og merk som lest uten å åpne appen.", "svar_fra_varsel", Innstillinger.svarFraVarsel(k)))
                addView(ui.divider(k))
                addView(ui.button(k, "Lyd og vibrasjon") {
                    Varsel.kanal(k)
                    startActivity(
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, Varsel.KANAL),
                    )
                })
            })

            addView(ui.box(k).apply {
                addView(ui.text(k, "Sending", WaddleTokens.TEXT_LG, bold = true))
                addView(bryter("Leveringsrapport", "Viser Levert når mottakeren har fått meldingen.", "leveringsrapport", Innstillinger.leveringsrapport(k)))
                addView(ui.divider(k))
                addView(bryter("Enter sender", "Enter sender i stedet for å lage ny linje.", "enter_sender", Innstillinger.enterSender(k)))
            })

            addView(ui.box(k).apply {
                addView(ui.text(k, "Liste", WaddleTokens.TEXT_LG, bold = true))
                addView(bryter("Bytt sveiperetning", "Til venstre arkiverer, til høyre sletter.", "bytt_sveip", Innstillinger.byttSveip(k)))
            })

            addView(ui.box(k).apply {
                addView(ui.text(k, "Blokkerte numre", WaddleTokens.TEXT_LG, bold = true))
                if (!Lager.standard(k)) {
                    addView(ui.text(k, "Meldinger må være standard SMS-app for å blokkere.", muted = true))
                    return@apply
                }
                val liste = blokkerte()
                if (liste.isEmpty()) addView(ui.text(k, "Ingen.", muted = true))
                liste.forEach { (id, nummer) ->
                    addView(Deler.klikkbar(ui.listRow(k, Lager.navn(k, nummer), "Trykk for å fjerne blokkeringen.", iconName = "close")) {
                        contentResolver.delete(android.content.ContentUris.withAppendedId(BlockedNumberContract.BlockedNumbers.CONTENT_URI, id), null, null)
                        vis()
                    })
                }
                val felt = Deler.felt(k, "Nummer").apply {
                    inputType = android.text.InputType.TYPE_CLASS_PHONE
                    setText(nyttNummer)
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) {
                            nyttNummer = s.toString()
                        }
                    })
                }
                addView(felt)
                addView(ui.button(k, "Blokker") {
                    val nummer = nyttNummer.trim()
                    if (nummer.isEmpty()) return@button
                    val ok = runCatching {
                        contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, ContentValues().apply {
                            put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, nummer)
                        })
                    }.isSuccess
                    if (!ok) Toast.makeText(k, "Fikk ikke blokkert nummeret.", Toast.LENGTH_LONG).show()
                    nyttNummer = ""
                    vis()
                })
            })
        }
        ui.screen(this, innhold)
        Deler.tastatur(this)
    }
}
