package me.arnee.skjermtid

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import java.util.Calendar

class App(val pakke: String, val navn: String, val minutter: Int)

object Bruk {
    const val KOMMANDO = "adb shell appops set me.arnee.skjermtid GET_USAGE_STATS allow"

    private fun lager(context: Context) = context.getSharedPreferences("grenser", Context.MODE_PRIVATE)

    fun tilgang(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    private fun midnatt() = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun idag(context: Context): List<App> {
        val bruk = context.getSystemService(UsageStatsManager::class.java)
            .queryAndAggregateUsageStats(midnatt(), System.currentTimeMillis())
        val pm = context.packageManager
        return bruk.values
            .filter { it.totalTimeInForeground >= 60_000 && it.packageName != context.packageName }
            .map {
                val navn = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, 0)).toString() }.getOrDefault(it.packageName)
                App(it.packageName, navn, (it.totalTimeInForeground / 60_000).toInt())
            }
            .sortedByDescending { it.minutter }
    }

    fun grense(context: Context, pakke: String) = lager(context).getInt(pakke, 0)

    fun settGrense(context: Context, pakke: String, minutter: Int) {
        lager(context).edit().apply { if (minutter > 0) putInt(pakke, minutter) else remove(pakke) }.apply()
        planlegg(context)
    }

    fun planlegg(context: Context) {
        val alarmer = context.getSystemService(AlarmManager::class.java)
        val v = PendingIntent.getBroadcast(context, 0, Intent(context, Sjekk::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        alarmer.cancel(v)
        if (lager(context).all.isEmpty()) return
        alarmer.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES, AlarmManager.INTERVAL_FIFTEEN_MINUTES, v)
    }

    fun sjekk(context: Context) {
        if (!tilgang(context)) return
        val varslet = context.getSharedPreferences("varslet", Context.MODE_PRIVATE)
        val dag = midnatt().toString()
        if (varslet.getString("dag", "") != dag) varslet.edit().clear().putString("dag", dag).apply()
        val varsler = context.getSystemService(NotificationManager::class.java)
        varsler.createNotificationChannel(NotificationChannel("grense", "Grenser", NotificationManager.IMPORTANCE_DEFAULT))
        idag(context).forEach { app ->
            val g = grense(context, app.pakke)
            if (g > 0 && app.minutter >= g && !varslet.getBoolean(app.pakke, false)) {
                varslet.edit().putBoolean(app.pakke, true).apply()
                varsler.notify(app.pakke.hashCode(), Notification.Builder(context, "grense")
                    .setSmallIcon(R.drawable.ic_hourglass)
                    .setContentTitle("${app.navn}: ${app.minutter} min i dag")
                    .setContentText("Grensen din er $g min.")
                    .setAutoCancel(true)
                    .build())
            }
        }
    }
}

class Sjekk : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != null) Bruk.planlegg(context) else Bruk.sjekk(context)
    }
}
