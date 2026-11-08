package me.arnee.sms

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import java.io.ByteArrayOutputStream
import java.util.UUID

object Mms {
    private const val FRA = 137
    private const val TIL = 151
    private const val KOPI = 130

    private fun sms(context: Context, abonnement: Int): SmsManager {
        val standard = context.getSystemService(SmsManager::class.java)
        return if (Build.VERSION.SDK_INT >= 31 && abonnement != SubscriptionManager.INVALID_SUBSCRIPTION_ID) standard.createForSubscriptionId(abonnement) else standard
    }

    private fun mittNummer(context: Context): String? = runCatching {
        @Suppress("DEPRECATION", "MissingPermission")
        context.getSystemService(TelephonyManager::class.java).line1Number
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun likt(a: String, b: String): Boolean {
        val x = a.filter { it.isDigit() }.takeLast(8)
        val y = b.filter { it.isDigit() }.takeLast(8)
        return x.isNotEmpty() && x == y
    }

    private fun finnes(context: Context, kolonne: String, verdi: String?): Boolean {
        if (verdi.isNullOrEmpty()) return false
        return context.contentResolver.query(Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms._ID), "$kolonne = ?", arrayOf(verdi), null)?.use { it.count > 0 } ?: false
    }

    fun varsel(context: Context, data: ByteArray, abonnement: Int) {
        val p = Pdu.les(data)
        if (p.type != Pdu.NOTIFICATION_IND) return
        val plassering = p.plassering ?: return
        if (finnes(context, Telephony.Mms.CONTENT_LOCATION, plassering)) return
        val navn = "inn-${UUID.randomUUID()}.pdu"
        val ferdig = PendingIntent.getBroadcast(
            context, navn.hashCode(),
            Intent(context, MmsHentet::class.java)
                .putExtra("fil", navn)
                .putExtra("plassering", plassering)
                .putExtra("transaksjon", p.transaksjon)
                .putExtra("fra", p.fra)
                .putExtra("abonnement", abonnement),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        sms(context, abonnement).downloadMultimediaMessage(context, plassering, Filer.uri(context, navn), null, ferdig)
    }

    fun hentet(context: Context, intent: Intent, ok: Boolean) {
        val navn = intent.getStringExtra("fil") ?: return
        val fil = Filer.fil(context, navn)
        val fraVarsel = intent.getStringExtra("fra")
        if (!ok || !fil.exists() || fil.length() == 0L) {
            fil.delete()
            Varsel.vis(context, 0, "MMS ble ikke hentet", "Fra ${fraVarsel?.let { Lager.navn(context, it) } ?: "ukjent"}. Sjekk mobildata og be om å få den sendt igjen.")
            return
        }
        val p = Pdu.les(fil.readBytes())
        fil.delete()
        if (finnes(context, Telephony.Mms.MESSAGE_ID, p.meldingId)) return
        val fra = p.fra ?: fraVarsel ?: "ukjent"
        val meg = mittNummer(context)
        val andre = (p.til + p.kopi).filter { a -> meg?.let { !likt(a, it) } ?: true }
        val deltakere = if (meg == null && p.til.size == 1 && p.kopi.isEmpty()) listOf(fra) else (listOf(fra) + andre).distinctBy { it.filter { c -> c.isDigit() }.takeLast(8) }
        val traad = Telephony.Threads.getOrCreateThreadId(context, deltakere.toSet())
        val id = lagre(context, traad, Telephony.Mms.MESSAGE_BOX_INBOX, 132, p.meldingId, p.transaksjon ?: intent.getStringExtra("transaksjon"),
            intent.getStringExtra("plassering"), p.emne, if (p.dato > 0) p.dato else System.currentTimeMillis() / 1000, p.deler)
        adresser(context, id, listOf(fra to FRA) + p.til.map { it to TIL } + p.kopi.map { it to KOPI })
        val tekst = p.deler.filter { it.type == "text/plain" }.joinToString("\n") { it.tekst() }.ifEmpty { if (p.deler.any { it.type.startsWith("image/") }) "Bilde" else "MMS" }
        Varsel.vis(context, traad, Lager.navn(context, fra), tekst, if (deltakere.size == 1) fra else null)
        p.transaksjon?.let { kvitter(context, it, intent.getIntExtra("abonnement", SubscriptionManager.INVALID_SUBSCRIPTION_ID)) }
    }

    private fun kvitter(context: Context, transaksjon: String, abonnement: Int) {
        runCatching {
            val navn = "kvittering-${UUID.randomUUID()}.pdu"
            Filer.fil(context, navn).writeBytes(Pdu.kvittering(transaksjon))
            val svar = PendingIntent.getBroadcast(
                context, navn.hashCode(),
                Intent(context, MmsSendt::class.java).putExtra("fil", navn),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            sms(context, abonnement).sendMultimediaMessage(context, Filer.uri(context, navn), null, null, svar)
        }
    }

    private fun lagre(context: Context, traad: Long, boks: Int, type: Int, meldingId: String?, transaksjon: String?, plassering: String?, emne: String?, dato: Long, deler: List<Del>): Long {
        val verdier = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, traad)
            put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
            put(Telephony.Mms.DATE_SENT, dato)
            put(Telephony.Mms.MESSAGE_BOX, boks)
            put(Telephony.Mms.READ, if (boks == Telephony.Mms.MESSAGE_BOX_INBOX) 0 else 1)
            put(Telephony.Mms.SEEN, if (boks == Telephony.Mms.MESSAGE_BOX_INBOX) 0 else 1)
            put(Telephony.Mms.MESSAGE_TYPE, type)
            put(Telephony.Mms.MMS_VERSION, 0x12)
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            meldingId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
            transaksjon?.let { put(Telephony.Mms.TRANSACTION_ID, it) }
            plassering?.let { put(Telephony.Mms.CONTENT_LOCATION, it) }
            emne?.let {
                put(Telephony.Mms.SUBJECT, it)
                put(Telephony.Mms.SUBJECT_CHARSET, 106)
            }
            put(Telephony.Mms.TEXT_ONLY, if (deler.all { it.type == "text/plain" || it.type == "application/smil" }) 1 else 0)
        }
        val uri = context.contentResolver.insert(Telephony.Mms.CONTENT_URI, verdier) ?: throw IllegalStateException("Fikk ikke lagret MMS")
        val id = uri.lastPathSegment!!.toLong()
        deler.forEachIndexed { i, d ->
            val del = ContentValues().apply {
                put(Telephony.Mms.Part.MSG_ID, id)
                put(Telephony.Mms.Part.SEQ, i)
                put(Telephony.Mms.Part.CONTENT_TYPE, d.type)
                d.id?.let { put(Telephony.Mms.Part.CONTENT_ID, "<$it>") }
                put(Telephony.Mms.Part.CONTENT_LOCATION, d.plassering ?: d.navn ?: "del$i")
                d.navn?.let { put(Telephony.Mms.Part.NAME, it) }
                if (d.type.startsWith("text/") || d.type == "application/smil") {
                    put(Telephony.Mms.Part.CHARSET, 106)
                    put(Telephony.Mms.Part.TEXT, d.tekst())
                }
            }
            val delUri = context.contentResolver.insert(Uri.parse("content://mms/$id/part"), del)
            if (delUri != null && !(d.type.startsWith("text/") || d.type == "application/smil")) {
                context.contentResolver.openOutputStream(delUri)?.use { it.write(d.data) }
            }
        }
        return id
    }

    private fun adresser(context: Context, id: Long, liste: List<Pair<String, Int>>) {
        liste.forEach { (adresse, type) ->
            context.contentResolver.insert(Uri.parse("content://mms/$id/addr"), ContentValues().apply {
                put(Telephony.Mms.Addr.ADDRESS, adresse)
                put(Telephony.Mms.Addr.TYPE, type)
                put(Telephony.Mms.Addr.CHARSET, 106)
                put(Telephony.Mms.Addr.MSG_ID, id)
            })
        }
    }

    fun bilde(context: Context, uri: Uri): ByteArray {
        val grenser = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, grenser) }
        var skala = 1
        while (maxOf(grenser.outWidth, grenser.outHeight) / (skala * 2) >= 1280) skala *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = skala }) }
            ?: throw IllegalArgumentException("Kunne ikke lese bildet")
        var kvalitet = 85
        var storrelse = 1.0
        while (true) {
            val b = if (storrelse < 1.0) Bitmap.createScaledBitmap(bitmap, (bitmap.width * storrelse).toInt(), (bitmap.height * storrelse).toInt(), true) else bitmap
            val ut = ByteArrayOutputStream()
            b.compress(Bitmap.CompressFormat.JPEG, kvalitet, ut)
            if (ut.size() <= 280_000 || (kvalitet <= 40 && storrelse <= 0.3)) return ut.toByteArray()
            if (kvalitet > 40) kvalitet -= 15 else storrelse *= 0.75
        }
    }

    fun send(context: Context, til: List<String>, tekst: String?, bilde: ByteArray?, bildeType: String = "image/jpeg") {
        val transaksjon = "T" + UUID.randomUUID().toString().replace("-", "").take(16)
        val pdu = Pdu.send(til, tekst, bilde, transaksjon, bildeType)
        val traad = Telephony.Threads.getOrCreateThreadId(context, til.toSet())
        val deler = Pdu.les(pdu).deler
        val id = lagre(context, traad, Telephony.Mms.MESSAGE_BOX_OUTBOX, 128, null, transaksjon, null, null, System.currentTimeMillis() / 1000, deler)
        adresser(context, id, listOf("insert-address-token" to FRA) + til.map { it to TIL })
        val navn = "ut-$id.pdu"
        Filer.fil(context, navn).writeBytes(pdu)
        val svar = PendingIntent.getBroadcast(
            context, navn.hashCode(),
            Intent(context, MmsSendt::class.java).putExtra("fil", navn).putExtra("id", id),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        context.getSystemService(SmsManager::class.java).sendMultimediaMessage(context, Filer.uri(context, navn), null, null, svar)
    }

    fun sendt(context: Context, intent: Intent, ok: Boolean) {
        intent.getStringExtra("fil")?.let { Filer.fil(context, it).delete() }
        val id = intent.getLongExtra("id", -1L)
        if (id < 0) return
        context.contentResolver.update(
            Uri.parse("content://mms/$id"),
            ContentValues().apply { put(Telephony.Mms.MESSAGE_BOX, if (ok) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_FAILED) },
            null, null,
        )
    }
}
