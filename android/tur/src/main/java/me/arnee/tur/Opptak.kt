package me.arnee.tur

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper

class Opptak : Service(), LocationListener {
    private var sistVarslet = 0L

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("opptak", "Opptak", NotificationManager.IMPORTANCE_LOW))
        Okt.last(this)
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, varsel(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(1, varsel())
            val lm = getSystemService(LocationManager::class.java)
            lm.removeUpdates(this)
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, Okt.intervall(), 0f, this, Looper.getMainLooper())
            if (!Okt.aktiv) stopSelf()
        } catch (e: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        getSystemService(LocationManager::class.java).removeUpdates(this)
        super.onDestroy()
    }

    override fun onLocationChanged(l: Location) {
        Okt.legg(this, l)
        val na = System.currentTimeMillis()
        if (na - sistVarslet > 10000) {
            sistVarslet = na
            getSystemService(NotificationManager::class.java).notify(1, varsel())
        }
    }

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun varsel(): Notification = Notification.Builder(this, "opptak")
        .setSmallIcon(R.drawable.ic_map)
        .setContentTitle("${Vis.type(Okt.type)} · ${Vis.km(Okt.lengde)}")
        .setContentText("Tar opp")
        .setWhen(System.currentTimeMillis() - Okt.tid())
        .setUsesChronometer(true)
        .setShowWhen(true)
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .build()
}
