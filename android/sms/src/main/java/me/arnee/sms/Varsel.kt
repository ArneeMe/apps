package me.arnee.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.app.RemoteInput
import android.content.Intent

object Varsel {
    const val KANAL = "meldinger"
    private const val SVAR = "svar"

    fun kanal(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(KANAL, "Nye meldinger", NotificationManager.IMPORTANCE_HIGH))
    }

    fun svar(intent: Intent): String? = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(SVAR)?.toString()

    fun vis(context: Context, traad: Long, tittel: String, tekst: String, adresse: String? = null) {
        val varsler = context.getSystemService(NotificationManager::class.java)
        kanal(context)
        val innhold = if (Innstillinger.visInnhold(context)) tekst else "Ny melding"
        val apne = PendingIntent.getActivity(
            context, traad.toInt(),
            Intent(context, SamtaleActivity::class.java).putExtra("traad", traad),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val varsel = Notification.Builder(context, KANAL)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(tittel)
            .setContentText(innhold)
            .setStyle(Notification.BigTextStyle().bigText(innhold))
            .setContentIntent(apne)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
        if (adresse != null && Innstillinger.svarFraVarsel(context)) {
            val handling = { type: String, kode: Int ->
                PendingIntent.getBroadcast(
                    context, traad.toInt() * 10 + kode,
                    Intent(context, VarselMottak::class.java).setAction(type).putExtra("traad", traad).putExtra("adresse", adresse),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            varsel.addAction(
                Notification.Action.Builder(null, "Svar", handling(SVAR, 1))
                    .addRemoteInput(RemoteInput.Builder(SVAR).setLabel("Svar").build())
                    .build(),
            )
            varsel.addAction(Notification.Action.Builder(null, "Merk som lest", handling("lest", 2)).build())
        }
        varsler.notify(traad.toInt(), varsel.build())
    }

    fun fjern(context: Context, traad: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(traad.toInt())
    }
}
