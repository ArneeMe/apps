package me.arnee.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Varsel {
    private const val KANAL = "meldinger"

    fun vis(context: Context, traad: Long, tittel: String, tekst: String) {
        val varsler = context.getSystemService(NotificationManager::class.java)
        varsler.createNotificationChannel(NotificationChannel(KANAL, "Nye meldinger", NotificationManager.IMPORTANCE_HIGH))
        val apne = PendingIntent.getActivity(
            context, traad.toInt(),
            Intent(context, SamtaleActivity::class.java).putExtra("traad", traad),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val varsel = Notification.Builder(context, KANAL)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(tittel)
            .setContentText(tekst)
            .setStyle(Notification.BigTextStyle().bigText(tekst))
            .setContentIntent(apne)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .build()
        varsler.notify(traad.toInt(), varsel)
    }

    fun fjern(context: Context, traad: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(traad.toInt())
    }
}
