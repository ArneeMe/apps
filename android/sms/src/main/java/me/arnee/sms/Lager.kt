package me.arnee.sms

import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager

class Samtale(val id: Long, val adresser: List<String>, val utdrag: String, val tid: Long, val lest: Boolean)

class Melding(val tid: Long, val fraMeg: Boolean, val tekst: String, val bilder: List<Uri>, val feilet: Boolean, val levert: Boolean = false) {
    val reaksjoner = mutableListOf<String>()
}

class Kontakt(val navn: String, val nummer: String)

object Lager {
    private val navn = mutableMapOf<String, String>()

    fun standard(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29 && context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)) return true
        return Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }

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
            arrayOf(Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.BODY, Telephony.Sms.STATUS),
            "${Telephony.Sms.THREAD_ID} = ?", arrayOf(traad.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val type = c.getInt(1)
                liste += Melding(c.getLong(0), type != Telephony.Sms.MESSAGE_TYPE_INBOX, c.getString(2) ?: "", emptyList(), type == Telephony.Sms.MESSAGE_TYPE_FAILED, c.getInt(3) == Telephony.Sms.STATUS_COMPLETE)
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
        return slaSammen(liste.sortedBy { it.tid }).takeLast(300)
    }

    private fun slaSammen(liste: List<Melding>): List<Melding> {
        val ut = mutableListOf<Melding>()
        for (m in liste) {
            val r = if (m.bilder.isEmpty()) Reaksjoner.tolk(m.tekst) else null
            val mal = r?.let { reaksjon -> ut.lastOrNull { Reaksjoner.passer(reaksjon.sitat, it.tekst) } }
            if (r != null && mal != null) {
                val hvem = if (m.fraMeg) "Meg" else ""
                val merke = listOf(hvem, r.ord).filter { it.isNotEmpty() }.joinToString(": ")
                if (r.fjernet) mal.reaksjoner.remove(merke) else mal.reaksjoner += merke
            } else {
                ut += m
            }
        }
        return ut
    }

    fun kontakter(context: Context, tekst: String): List<Kontakt> {
        if (tekst.isBlank()) return emptyList()
        val liste = mutableListOf<Kontakt>()
        runCatching {
            context.contentResolver.query(
                Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(tekst)),
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext() && liste.size < 12) liste += Kontakt(c.getString(0) ?: "", c.getString(1) ?: "")
            }
        }
        return liste.distinctBy { it.nummer.filter { ch -> ch.isDigit() || ch == '+' } }
    }

    private fun arkiv(context: Context) = context.getSharedPreferences("arkiv", Context.MODE_PRIVATE)

    fun arkivert(context: Context, samtale: Samtale): Boolean = arkiv(context).getLong(samtale.id.toString(), -1L) >= samtale.tid

    fun arkiver(context: Context, samtale: Samtale, arkivert: Boolean) {
        arkiv(context).edit().apply {
            if (arkivert) putLong(samtale.id.toString(), samtale.tid) else remove(samtale.id.toString())
        }.apply()
    }

    fun slett(context: Context, traad: Long): Boolean = runCatching {
        context.contentResolver.delete(Uri.parse("content://mms-sms/conversations/$traad"), null, null) > 0
    }.getOrDefault(false)

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
        val levert = if (Innstillinger.leveringsrapport(context)) ArrayList(deler.indices.map {
            PendingIntent.getBroadcast(
                context, ((rad?.lastPathSegment?.toIntOrNull() ?: 0) * 100) + 50 + it,
                Intent(context, LevertMottak::class.java).setData(rad),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }) else null
        sms.sendMultipartTextMessage(adresse, null, deler, svar, levert)
    }
}
