package me.arnee.digipost

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date
import me.arnee.design.WaddleTokens
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

    fun hendelse(verdi: String) = when (verdi) {
        "OPENED" -> "Åpnet"
        "POSTMARKED" -> "Postlagt"
        "PRINT_FAILED" -> "Utskrift feilet"
        "SHREDDED" -> "Makulert"
        "SMS_NOTIFICATION_FAILED" -> "SMS-varsel feilet"
        "EMAIL_NOTIFICATION_FAILED" -> "E-postvarsel feilet"
        "EMAIL_MESSAGE_SENT" -> "Sendt som e-post"
        "EMAIL_MESSAGE_FAILED" -> "E-post feilet"
        "PEPPOL_DELIVERED" -> "Levert som EHF"
        "PEPPOL_FAILED" -> "EHF feilet"
        "MOVE_FILES_FROM_PUBLIC_SECTOR" -> "Flyttet fra offentlig sektor"
        else -> verdi
    }

    fun tid(verdi: OffsetDateTime) =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(verdi.toInstant().toEpochMilli()))

    fun tid(verdi: String) = runCatching { tid(OffsetDateTime.parse(verdi)) }.getOrDefault(verdi)

    fun tid(millis: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

    fun velgTid(k: Context, start: OffsetDateTime?, ferdig: (OffsetDateTime) -> Unit) {
        val fra = start?.toLocalDateTime() ?: LocalDateTime.now().plusHours(1).withMinute(0)
        DatePickerDialog(k, { _, ar, maned, dag ->
            TimePickerDialog(k, { _, time, minutt ->
                ferdig(LocalDateTime.of(ar, maned + 1, dag, time, minutt).atZone(ZoneId.systemDefault()).toOffsetDateTime())
            }, fra.hour, fra.minute, true).show()
        }, fra.year, fra.monthValue - 1, fra.dayOfMonth).show()
    }

    fun boble(k: Context, tittel: String, detalj: String, egen: Boolean, ikon: String) = LinearLayout(k).apply {
        gravity = if (egen) Gravity.END else Gravity.START
        val boks = WaddleUi.box(k).apply { addView(WaddleUi.listRow(k, tittel, detalj, iconName = ikon)) }
        val bredde = (k.resources.displayMetrics.widthPixels * 0.8).toInt()
        addView(boks, LinearLayout.LayoutParams(bredde, LinearLayout.LayoutParams.WRAP_CONTENT))
        setPadding(0, 0, 0, WaddleUi.dp(k, WaddleTokens.SPACE_1))
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

    fun skjerm(a: Activity, innhold: View) {
        val rot = a.findViewById<ViewGroup>(android.R.id.content)
        val y = (rot.getChildAt(0) as? ScrollView)?.scrollY ?: 0
        WaddleUi.screen(a, innhold)
        val ny = rot.getChildAt(0) as? ScrollView ?: return
        if (y == 0) return
        ny.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                ny.viewTreeObserver.removeOnPreDrawListener(this)
                ny.scrollTo(0, y)
                return true
            }
        })
    }
}
