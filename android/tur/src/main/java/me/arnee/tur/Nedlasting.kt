package me.arnee.tur

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class Nedlasting : Service() {
    companion object {
        @Volatile
        var aktiv = false
        @Volatile
        var stopp = false
        @Volatile
        var totalt = 0
        val ferdig = AtomicInteger()
        val feilet = AtomicInteger()
        var lytter: (() -> Unit)? = null
        private val hoved = Handler(Looper.getMainLooper())

        fun mappe(k: Context) = File(k.filesDir, "veier")

        fun start(k: Context, sor: Double, vest: Double, nord: Double, ost: Double, oppdater: Boolean) {
            k.startForegroundService(Intent(k, Nedlasting::class.java).putExtra("boks", doubleArrayOf(sor, vest, nord, ost)).putExtra("oppdater", oppdater))
        }

        private fun meld() = hoved.post { lytter?.invoke() }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val boks = intent?.getDoubleArrayExtra("boks")
        if (boks == null || aktiv) return START_NOT_STICKY
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("nedlasting", "Nedlasting", NotificationManager.IMPORTANCE_LOW))
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(2, varsel(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(2, varsel())
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        val oppdater = intent.getBooleanExtra("oppdater", false)
        val mappe = mappe(this)
        aktiv = true
        stopp = false
        ferdig.set(0)
        feilet.set(0)
        Thread {
            val biter = Graf.biter(boks[0], boks[1], boks[2], boks[3]).filter { (y, x) -> oppdater || !Graf.harBit(mappe, y, x) }
            totalt = biter.size
            meld()
            val arbeid = Executors.newFixedThreadPool(2)
            biter.forEach { (y, x) ->
                arbeid.submit {
                    if (stopp) return@submit
                    if (runCatching { Graf.hentBit(mappe, y, x) }.isSuccess) ferdig.incrementAndGet() else feilet.incrementAndGet()
                    oppdaterVarsel()
                    meld()
                }
            }
            arbeid.shutdown()
            arbeid.awaitTermination(1, TimeUnit.DAYS)
            aktiv = false
            meld()
            hoved.post {
                stopForeground(STOP_FOREGROUND_REMOVE)
                if (!stopp) getSystemService(NotificationManager::class.java).notify(3, Notification.Builder(this, "nedlasting")
                    .setSmallIcon(R.drawable.ic_download)
                    .setContentTitle("Veier og stier er lastet ned")
                    .setContentText("${ferdig.get()} kartbiter" + if (feilet.get() > 0) ", ${feilet.get()} feilet. Prøv igjen for å hente resten." else "")
                    .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, OmradeActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                    .setAutoCancel(true)
                    .build())
                stopSelf()
            }
        }.start()
        return START_NOT_STICKY
    }

    private var sist = 0L

    @Synchronized
    private fun oppdaterVarsel() {
        val na = System.currentTimeMillis()
        if (na - sist < 2000) return
        sist = na
        getSystemService(NotificationManager::class.java).notify(2, varsel())
    }

    private fun varsel(): Notification = Notification.Builder(this, "nedlasting")
        .setSmallIcon(R.drawable.ic_download)
        .setContentTitle("Laster ned veier og stier")
        .setContentText(if (totalt == 0) "Starter" else "${ferdig.get() + feilet.get()} av $totalt kartbiter")
        .setProgress(maxOf(totalt, 1), ferdig.get() + feilet.get(), totalt == 0)
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, OmradeActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .build()
}
