package me.arnee.sovn

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.service.quicksettings.TileService
import android.view.KeyEvent

object Timer {
    const val STANDARD = 40
    val VALG = listOf(15, 30, 40, 60, 90)

    private fun lager(context: Context) = context.getSharedPreferences("sovn", Context.MODE_PRIVATE)

    fun slutt(context: Context): Long = lager(context).getLong("slutt", 0L)

    fun gaar(context: Context) = slutt(context) > System.currentTimeMillis()

    fun minutterIgjen(context: Context) = ((slutt(context) - System.currentTimeMillis() + 59_999) / 60_000).toInt()

    private fun varsel(context: Context) =
        PendingIntent.getBroadcast(context, 0, Intent(context, Slutt::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun start(context: Context, minutter: Int) {
        val ms = minutter * 60_000L
        lager(context).edit().putLong("slutt", System.currentTimeMillis() + ms).apply()
        val alarmer = context.getSystemService(AlarmManager::class.java)
        val nar = SystemClock.elapsedRealtime() + ms
        if (Build.VERSION.SDK_INT < 31 || alarmer.canScheduleExactAlarms()) {
            alarmer.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, nar, varsel(context))
        } else {
            alarmer.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, nar, varsel(context))
        }
        oppdaterTile(context)
    }

    fun stopp(context: Context) {
        lager(context).edit().putLong("slutt", 0L).apply()
        context.getSystemService(AlarmManager::class.java).cancel(varsel(context))
        oppdaterTile(context)
    }

    fun admin(context: Context) = ComponentName(context, Admin::class.java)

    fun kanLase(context: Context) = context.getSystemService(DevicePolicyManager::class.java).isAdminActive(admin(context))

    fun fullfor(context: Context) {
        lager(context).edit().putLong("slutt", 0L).apply()
        val lyd = context.getSystemService(AudioManager::class.java)
        lyd.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
        lyd.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        val fokus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .build()
        lyd.requestAudioFocus(fokus)
        lyd.abandonAudioFocusRequest(fokus)
        if (kanLase(context)) context.getSystemService(DevicePolicyManager::class.java).lockNow()
        oppdaterTile(context)
    }

    private fun oppdaterTile(context: Context) {
        TileService.requestListeningState(context, ComponentName(context, SovnTile::class.java))
    }
}
