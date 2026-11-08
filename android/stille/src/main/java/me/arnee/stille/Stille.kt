package me.arnee.stille

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import java.util.Calendar

object Stille {
    val UNNTAK = listOf(
        "Ingen" to NotificationManager.Policy.PRIORITY_SENDERS_ANY,
        "Favoritter" to NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
        "Kontakter" to NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS,
    )

    private fun lager(context: Context) = context.getSharedPreferences("stille", Context.MODE_PRIVATE)
    private fun varsler(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun tilgang(context: Context) = varsler(context).isNotificationPolicyAccessGranted

    fun pa(context: Context) = varsler(context).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

    fun unntak(context: Context) = lager(context).getInt("unntak", 1)
    fun gjentatte(context: Context) = lager(context).getBoolean("gjentatte", true)
    fun alarmer(context: Context) = lager(context).getBoolean("alarmer", true)
    fun plan(context: Context) = lager(context).getBoolean("plan", false)
    fun start(context: Context) = lager(context).getInt("start", 23 * 60)
    fun slutt(context: Context) = lager(context).getInt("slutt", 7 * 60)
    fun dager(context: Context): Set<Int> = lager(context).getString("dager", "1,2,3,4,5,6,7")!!.split(",").mapNotNull { it.toIntOrNull() }.toSet()

    fun lagre(context: Context, endring: android.content.SharedPreferences.Editor.() -> Unit) {
        lager(context).edit().apply(endring).apply()
        planlegg(context)
        if (pa(context)) settPa(context, true)
    }

    fun settPa(context: Context, pa: Boolean) {
        if (!tilgang(context)) return
        val v = varsler(context)
        if (pa) {
            val (_, avsendere) = UNNTAK[unntak(context)]
            var kategorier = 0
            if (unntak(context) > 0) kategorier = kategorier or NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES
            if (gjentatte(context)) kategorier = kategorier or NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
            if (alarmer(context)) kategorier = kategorier or NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS
            v.notificationPolicy = NotificationManager.Policy(kategorier, avsendere, avsendere)
            v.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        } else {
            v.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        TileService.requestListeningState(context, ComponentName(context, StilleTile::class.java))
    }

    fun tekst(minutter: Int) = "%02d:%02d".format(minutter / 60, minutter % 60)

    private fun varsel(context: Context, pa: Boolean) = PendingIntent.getBroadcast(
        context, if (pa) 1 else 2,
        Intent(context, Mottak::class.java).putExtra("pa", pa),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun neste(context: Context, minutter: Int, pa: Boolean): Long {
        val dager = dager(context)
        val nar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutter / 60)
            set(Calendar.MINUTE, minutter % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (nar.timeInMillis <= System.currentTimeMillis()) nar.add(Calendar.DAY_OF_YEAR, 1)
        if (pa) {
            repeat(7) {
                if (dag(nar) in dager) return nar.timeInMillis
                nar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return nar.timeInMillis
    }

    fun dag(c: Calendar) = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1

    fun planlegg(context: Context) {
        val alarmer = context.getSystemService(AlarmManager::class.java)
        for (pa in listOf(true, false)) {
            val v = varsel(context, pa)
            alarmer.cancel(v)
            if (!plan(context)) continue
            val nar = neste(context, if (pa) start(context) else slutt(context), pa)
            if (Build.VERSION.SDK_INT < 31 || alarmer.canScheduleExactAlarms()) {
                alarmer.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nar, v)
            } else {
                alarmer.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nar, v)
            }
        }
    }
}

class Mottak : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.hasExtra("pa")) Stille.settPa(context, intent.getBooleanExtra("pa", false))
        Stille.planlegg(context)
    }
}

class StilleTile : TileService() {
    override fun onStartListening() = oppdater()

    override fun onClick() {
        Stille.settPa(this, !Stille.pa(this))
        oppdater()
    }

    private fun oppdater() {
        val tile = qsTile ?: return
        tile.state = when {
            !Stille.tilgang(this) -> android.service.quicksettings.Tile.STATE_UNAVAILABLE
            Stille.pa(this) -> android.service.quicksettings.Tile.STATE_ACTIVE
            else -> android.service.quicksettings.Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
