package me.arnee.sms

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Tid {
    private fun dag(tid: Long) = Calendar.getInstance().apply {
        timeInMillis = tid
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun dagerSiden(tid: Long) = ((dag(System.currentTimeMillis()) - dag(tid)) / 86_400_000L).toInt()

    private fun format(monster: String, tid: Long) = SimpleDateFormat(monster, Locale("nb", "NO")).format(Date(tid))

    fun kort(tid: Long): String = when (val d = dagerSiden(tid)) {
        0 -> format("HH:mm", tid)
        1 -> "i går"
        in 2..6 -> format("EEE", tid)
        else -> if (format("yyyy", tid) == format("yyyy", System.currentTimeMillis())) format("d. MMM", tid) else format("dd.MM.yy", tid)
    }

    fun skille(tid: Long): String = when (dagerSiden(tid)) {
        0 -> "I dag " + format("HH:mm", tid)
        1 -> "I går " + format("HH:mm", tid)
        in 2..6 -> format("EEEE HH:mm", tid).replaceFirstChar { it.uppercase() }
        else -> format("d. MMMM yyyy HH:mm", tid)
    }
}
