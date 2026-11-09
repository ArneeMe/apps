package me.arnee.digipost

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

object Ui {
    val nivaer = listOf("PASSWORD", "TWO_FACTOR", "IDPORTEN_3", "IDPORTEN_4")
    val smsTimer = listOf(0, 1, 2, 4, 8, 24, 48, 72, 168)

    fun niva(verdi: String) = when (verdi) {
        "TWO_FACTOR" -> "Tofaktor"
        "IDPORTEN_3" -> "ID-porten nivå 3"
        "IDPORTEN_4" -> "ID-porten nivå 4"
        else -> "Passord"
    }

    fun sms(timer: Int) = when {
        timer == 0 -> "Av"
        timer % 24 == 0 -> "Etter ${timer / 24} døgn"
        else -> "Etter $timer timer"
    }

    fun kanal(verdi: String) = when (verdi) {
        "DIGIPOST" -> "Digipost"
        "PRINT" -> "Papir"
        "EPOST" -> "E-post"
        "PEPPOL" -> "EHF"
        "PENDING" -> "Venter"
        else -> verdi
    }

    fun status(verdi: String) = when (verdi) {
        "DELIVERED" -> "Levert"
        "DELIVERED_TO_PRINT" -> "Sendt til print"
        "COMPLETE" -> "Venter på levering"
        "NOT_COMPLETE" -> "Ikke fullført"
        "NOT_DELIVERED" -> "Ikke levert"
        else -> verdi
    }

    fun storrelse(bytes: Int) = when {
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        else -> "${maxOf(1, bytes / 1000)} kB"
    }

    fun <T> neste(liste: List<T>, verdi: T) = liste[(liste.indexOf(verdi) + 1) % liste.size]

    fun felt(k: Context, hint: String, verdi: String, type: Int? = null, endret: (String) -> Unit) = Deler.felt(k, hint).apply {
        if (type != null) inputType = type
        setText(verdi)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                endret(s.toString())
            }
        })
    }

    fun bryter(k: Context, tittel: String, forklaring: String, verdi: Boolean, trykk: () -> Unit) =
        Deler.klikkbar(WaddleUi.listRow(k, tittel, "${if (verdi) "På" else "Av"}. $forklaring")) { trykk() }

    fun bytt(k: Context, tittel: String, verdi: String, trykk: () -> Unit) =
        Deler.klikkbar(WaddleUi.listRow(k, tittel, "$verdi. Trykk for å bytte.")) { trykk() }
}
