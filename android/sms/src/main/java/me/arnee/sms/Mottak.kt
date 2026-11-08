package me.arnee.sms

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SubscriptionManager

class SmsMottak : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deler = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (deler.isNullOrEmpty()) return
        val adresse = deler[0].displayOriginatingAddress ?: return
        val tekst = deler.joinToString("") { it.displayMessageBody ?: "" }
        val traad = Lager.mottatt(context, adresse, tekst, deler[0].timestampMillis)
        Varsel.vis(context, traad, Lager.navn(context, adresse), tekst, adresse)
    }
}

class MmsMottak : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val data = intent.getByteArrayExtra("data") ?: return
        val abonnement = intent.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        val svar = goAsync()
        Thread {
            runCatching { Mms.varsel(context, data, abonnement) }
                .onFailure { Varsel.vis(context, 0, "MMS ble ikke hentet", "${it.javaClass.simpleName}: ${it.message}") }
            svar.finish()
        }.start()
    }
}

class MmsHentet : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ok = resultCode == Activity.RESULT_OK
        val svar = goAsync()
        Thread {
            runCatching { Mms.hentet(context, intent, ok) }
                .onFailure { Varsel.vis(context, 0, "MMS ble ikke lagret", "${it.javaClass.simpleName}: ${it.message}") }
            svar.finish()
        }.start()
    }
}

class MmsSendt : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { Mms.sendt(context, intent, resultCode == Activity.RESULT_OK) }
    }
}

class SendtMottak : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val rad = intent.data ?: return
        val type = if (resultCode == Activity.RESULT_OK) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED
        context.contentResolver.update(rad, ContentValues().apply { put(Telephony.Sms.TYPE, type) }, null, null)
    }
}

class SvarTjeneste : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val adresse = intent?.data?.schemeSpecificPart?.substringBefore("?")
        val tekst = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!adresse.isNullOrBlank() && !tekst.isNullOrBlank()) runCatching { Lager.send(this, adresse, tekst) }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

class VarselMottak : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val traad = intent.getLongExtra("traad", -1L)
        val adresse = intent.getStringExtra("adresse")
        if (intent.action == "svar" && adresse != null) {
            Varsel.svar(intent)?.takeIf { it.isNotBlank() }?.let { runCatching { Lager.send(context, adresse, it) } }
        }
        if (traad >= 0) {
            Lager.lest(context, traad)
            Varsel.fjern(context, traad)
        }
    }
}

class LevertMottak : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val rad = intent.data ?: return
        val status = if (resultCode == Activity.RESULT_OK) Telephony.Sms.STATUS_COMPLETE else Telephony.Sms.STATUS_FAILED
        context.contentResolver.update(rad, ContentValues().apply { put(Telephony.Sms.STATUS, status) }, null, null)
    }
}
