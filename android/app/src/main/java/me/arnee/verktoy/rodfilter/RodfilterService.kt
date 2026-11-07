package me.arnee.verktoy.rodfilter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import me.arnee.verktoy.MainActivity
import me.arnee.verktoy.R

class RodfilterService : Service() {
    private var lag: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == SLA_AV) {
            Rodfilter.sett(this, false)
            return START_NOT_STICKY
        }
        startForeground()
        visLag()
        return START_STICKY
    }

    override fun onDestroy() {
        lag?.let { getSystemService(WindowManager::class.java).removeView(it) }
        lag = null
        super.onDestroy()
    }

    private fun visLag() {
        val vinduer = getSystemService(WindowManager::class.java)
        val parametre = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            alpha = Rodfilter.styrke(this@RodfilterService) / 100f
        }
        val eksisterende = lag
        if (eksisterende != null) {
            vinduer.updateViewLayout(eksisterende, parametre)
        } else {
            val view = View(this).apply { setBackgroundColor(Color.RED) }
            vinduer.addView(view, parametre)
            lag = view
        }
    }

    private fun startForeground() {
        val varsler = getSystemService(NotificationManager::class.java)
        varsler.createNotificationChannel(NotificationChannel(KANAL, "Rødfilter", NotificationManager.IMPORTANCE_LOW))
        val apne = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val slaAv = PendingIntent.getService(this, 1, Intent(this, RodfilterService::class.java).setAction(SLA_AV), PendingIntent.FLAG_IMMUTABLE)
        val varsel = Notification.Builder(this, KANAL)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle("Rødfilter er på")
            .setContentIntent(apne)
            .addAction(Notification.Action.Builder(null, "Slå av", slaAv).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, varsel, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, varsel)
        }
    }

    companion object {
        private const val KANAL = "rodfilter"
        private const val SLA_AV = "me.arnee.verktoy.rodfilter.SLA_AV"
    }
}
