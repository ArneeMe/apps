package me.arnee.sms

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony

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
        Varsel.vis(context, 0, "Ny MMS", "Bildemeldinger kan ikke hentes i denne appen ennå.")
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
