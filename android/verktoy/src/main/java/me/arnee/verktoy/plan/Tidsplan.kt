package me.arnee.verktoy.plan

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
import me.arnee.verktoy.nattlys.Nattlys

object Tidsplan {
    private fun lager(context: Context) = context.getSharedPreferences("tidsplan", Context.MODE_PRIVATE)

    fun pa(context: Context) = lager(context).getBoolean("pa", false)

    fun start(context: Context) = lager(context).getInt("start", 22 * 60 + 30)

    fun slutt(context: Context) = lager(context).getInt("slutt", 7 * 60)

    fun tekst(minutter: Int) = "%02d:%02d".format(minutter / 60, minutter % 60)

    fun sett(context: Context, aktiv: Boolean = pa(context), fra: Int = start(context), til: Int = slutt(context)) {
        lager(context).edit().putBoolean("pa", aktiv).putInt("start", fra).putInt("slutt", til).apply()
        planlegg(context)
    }

    private fun varsel(context: Context, slaPa: Boolean) = PendingIntent.getBroadcast(
        context,
        if (slaPa) 1 else 2,
        Intent(context, TidsplanMottak::class.java).putExtra("pa", slaPa),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun neste(minutter: Int): Long {
        val nar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutter / 60)
            set(Calendar.MINUTE, minutter % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (nar.timeInMillis <= System.currentTimeMillis()) nar.add(Calendar.DAY_OF_YEAR, 1)
        return nar.timeInMillis
    }

    fun planlegg(context: Context) {
        val alarmer = context.getSystemService(AlarmManager::class.java)
        for (slaPa in listOf(true, false)) {
            val v = varsel(context, slaPa)
            alarmer.cancel(v)
            if (!pa(context)) continue
            val nar = neste(if (slaPa) start(context) else slutt(context))
            if (Build.VERSION.SDK_INT < 31 || alarmer.canScheduleExactAlarms()) {
                alarmer.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nar, v)
            } else {
                alarmer.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nar, v)
            }
        }
    }
}

class TidsplanMottak : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.hasExtra("pa") && Nattlys.tilgang(context)) Nattlys.settPa(context, intent.getBooleanExtra("pa", false))
        Tidsplan.planlegg(context)
    }
}
