package me.arnee.digipost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object Varsel {
    private const val SVAR = "svar"
    private const val BREV = "brev"
    private const val JOBB = 1
    val minutter = listOf(15, 30, 60, 180, 720)

    fun planlegg(k: Context) {
        runCatching { planleggNa(k) }
    }

    private fun planleggNa(k: Context) {
        val planlegger = k.getSystemService(JobScheduler::class.java)
        if (!Lager.bryter(k, "varsel_svar", true) && !Lager.bryter(k, "varsel_brev", true)) {
            planlegger.cancel(JOBB)
            return
        }
        val hvert = Lager.tall(k, "varsel_minutter", 30) * 60_000L
        val jobb = planlegger.getPendingJob(JOBB)
        if (jobb?.intervalMillis == hvert && jobb.isPersisted) return
        planlegger.schedule(
            JobInfo.Builder(JOBB, ComponentName(k, Sjekk::class.java))
                .setPeriodic(hvert)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build(),
        )
    }

    private fun kanaler(k: Context) {
        val varsler = k.getSystemService(NotificationManager::class.java)
        varsler.createNotificationChannel(NotificationChannel(SVAR, "Nye svar", NotificationManager.IMPORTANCE_HIGH))
        varsler.createNotificationChannel(NotificationChannel(BREV, "Status for brev", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun vis(k: Context, kanal: String, id: Int, tittel: String, tekst: String, intent: Intent) {
        kanaler(k)
        val apne = PendingIntent.getActivity(k, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val varsel = Notification.Builder(k, kanal)
            .setSmallIcon(R.drawable.ic_mail)
            .setContentTitle(tittel)
            .setContentText(tekst)
            .setStyle(Notification.BigTextStyle().bigText(tekst))
            .setContentIntent(apne)
            .setAutoCancel(true)
            .build()
        k.getSystemService(NotificationManager::class.java).notify(id, varsel)
    }

    fun svar(k: Context, nye: List<Innkommende>) {
        nye.forEach { d ->
            vis(k, SVAR, d.id.hashCode(), "Svar fra ${d.avsender}", d.emne.ifEmpty { "Nytt brev i innboksen" },
                Intent(k, SamtaleActivity::class.java).putExtra("nokkel", Samtaler.nokkel(d.avsender)))
        }
    }

    fun brev(k: Context, s: Sending, hendelse: String) {
        vis(k, BREV, (s.dokument + hendelse).hashCode(), "${Ui.hendelse(hendelse)}: ${s.emne}", "Til ${s.mottaker}",
            Intent(k, SendtActivity::class.java))
    }

    fun sjekk(k: Context) {
        val o = Lager.oppsett(k) ?: return
        if (Lager.bryter(k, "varsel_svar", true)) runCatching { Samtaler.nye(k) }.onSuccess { svar(k, it) }
        if (!Lager.bryter(k, "varsel_brev", true)) return
        val test = Lager.test(k)
        val liste = Lager.historikk(k)
        val hendelser = runCatching { Digipost.hendelser(o, 7) }.getOrNull() ?: return
        val per = hendelser.groupBy { it.dokument }
        liste.filter { it.test == test }.forEach { s ->
            val nye = per[s.dokument]?.sortedBy { it.tid }?.map { it.type }?.distinct() ?: return@forEach
            val gamle = s.hendelser.split(",").filter { it.isNotEmpty() }
            nye.filter { it !in gamle }.forEach { brev(k, s, it) }
            s.hendelser = (gamle + nye).distinct().joinToString(",")
            if ("OPENED" in nye) s.lest = "Y"
        }
        Lager.settHistorikk(k, liste)
    }
}

class Sjekk : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            runCatching { Varsel.sjekk(this) }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
