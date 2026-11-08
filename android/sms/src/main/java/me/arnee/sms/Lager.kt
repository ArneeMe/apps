package me.arnee.sms

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager

class Samtale(val id: Long, val adresser: List<String>, val utdrag: String, val tid: Long, val lest: Boolean)

class Melding(val tid: Long, val fraMeg: Boolean, val tekst: String, val bilder: List<Uri>, val feilet: Boolean)

object Lager {
    private val navn = mutableMapOf<String, String>()

    fun standard(context: Context) = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    fun navn(context: Context, adresse: String): String = navn.getOrPut(adresse) {
        runCatching {
            context.contentResolver.query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(adresse)),
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null,
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: adresse
    }

    private fun adresser(context: Context): Map<String, String> {
        val kart = mutableMapOf<String, String>()
        context.contentResolver.query(Uri.parse("content://mms-sms/canonical-addresses"), null, null, null, null)?.use { c ->
            val id = c.getColumnIndex("_id")
            val adresse = c.getColumnIndex("address")
            while (c.moveToNext()) kart[c.getString(id)] = c.getString(adresse)
        }
        return kart
    }

    fun samtaler(context: Context): List<Samtale> {
        val kart = adresser(context)
        val liste = mutableListOf<Samtale>()
        context.contentResolver.query(
            Uri.parse("content://mms-sms/conversations?simple=true"),
            arrayOf("_id", "recipient_ids", "snippet", "date", "read"), null, null, "date DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val mottakere = (c.getString(1) ?: "").split(" ").mapNotNull { kart[it] }
                liste += Samtale(c.getLong(0), mottakere, c.getString(2) ?: "", c.getLong(3), c.getInt(4) == 1)
            }
        }
        return liste
    }

    fun traad(context: Context, adresse: String): Long = Telephony.Threads.getOrCreateThreadId(context, adresse)

    fun meldinger(context: Context, traad: Long): List<Melding> {
        val liste = mutableListOf<Melding>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.BODY),
            "${Telephony.Sms.THREAD_ID} = ?", arrayOf(traad.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val type = c.getInt(1)
                liste += Melding(c.getLong(0), type != Telephony.Sms.MESSAGE_TYPE_INBOX, c.getString(2) ?: "", emptyList(), type == Telephony.Sms.MESSAGE_TYPE_FAILED)
            }
        }
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX),
            "${Telephony.Mms.THREAD_ID} = ?", arrayOf(traad.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val tekster = mutableListOf<String>()
                val bilder = mutableListOf<Uri>()
                context.contentResolver.query(Uri.parse("content://mms/part"), arrayOf("_id", "ct", "text"), "mid = ?", arrayOf(id.toString()), null)?.use { d ->
                    while (d.moveToNext()) {
                        val type = d.getString(1) ?: ""
                        when {
                            type == "text/plain" -> d.getString(2)?.let { tekster += it }
                            type.startsWith("image/") -> bilder += Uri.parse("content://mms/part/${d.getLong(0)}")
                        }
                    }
                }
                liste += Melding(c.getLong(1) * 1000, c.getInt(2) != Telephony.Mms.MESSAGE_BOX_INBOX, tekster.joinToString("\n"), bilder, false)
            }
        }
        return liste.sortedBy { it.tid }.takeLast(300)
    }

    fun bilde(context: Context, uri: Uri, bredde: Int): Bitmap? = runCatching {
        val grenser = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, grenser) }
        var skala = 1
        while (grenser.outWidth / (skala * 2) >= bredde) skala *= 2
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = skala }) }
    }.getOrNull()

    fun lest(context: Context, traad: Long) {
        val verdier = ContentValues().apply { put(Telephony.Sms.READ, 1) }
        runCatching {
            context.contentResolver.update(Telephony.Sms.CONTENT_URI, verdier, "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(traad.toString()))
            context.contentResolver.update(Telephony.Mms.CONTENT_URI, ContentValues().apply { put(Telephony.Mms.READ, 1) }, "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0", arrayOf(traad.toString()))
        }
    }

    fun mottatt(context: Context, adresse: String, tekst: String, tid: Long): Long {
        val traad = traad(context, adresse)
        context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
            put(Telephony.Sms.ADDRESS, adresse)
            put(Telephony.Sms.BODY, tekst)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, tid)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.THREAD_ID, traad)
        })
        return traad
    }

    fun send(context: Context, adresse: String, tekst: String) {
        val rad = context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, ContentValues().apply {
            put(Telephony.Sms.ADDRESS, adresse)
            put(Telephony.Sms.BODY, tekst)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.THREAD_ID, traad(context, adresse))
        })
        val sms = context.getSystemService(SmsManager::class.java)
        val deler = sms.divideMessage(tekst)
        val svar = ArrayList(deler.indices.map {
            PendingIntent.getBroadcast(
                context, ((rad?.lastPathSegment?.toIntOrNull() ?: 0) * 100) + it,
                Intent(context, SendtMottak::class.java).setData(rad),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        })
        sms.sendMultipartTextMessage(adresse, null, deler, svar, null)
    }
}
