package me.arnee.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager

object Sim {
    const val INGEN = SubscriptionManager.INVALID_SUBSCRIPTION_ID

    private fun lager(context: Context) = context.getSharedPreferences("sim", Context.MODE_PRIVATE)

    fun liste(context: Context): List<SubscriptionInfo> {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return emptyList()
        return runCatching {
            @Suppress("MissingPermission")
            context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty().sortedBy { it.simSlotIndex }
        }.getOrDefault(emptyList())
    }

    fun flere(context: Context) = liste(context).size > 1

    fun kort(context: Context, sub: Int): String? {
        val info = liste(context).firstOrNull { it.subscriptionId == sub } ?: return null
        return "SIM ${info.simSlotIndex + 1}"
    }

    fun navn(context: Context, sub: Int): String {
        val info = liste(context).firstOrNull { it.subscriptionId == sub } ?: return "Standard SIM"
        val operator = (info.displayName ?: info.carrierName)?.toString()?.takeIf { it.isNotBlank() }
        return listOfNotNull("SIM ${info.simSlotIndex + 1}", operator).joinToString(" ")
    }

    fun standard() = SubscriptionManager.getDefaultSmsSubscriptionId()

    fun valgt(context: Context, traad: Long, meldinger: List<Melding>): Int {
        val aktive = liste(context).map { it.subscriptionId }
        val lagret = lager(context).getInt(traad.toString(), INGEN)
        if (lagret != INGEN && lagret in aktive) return lagret
        val sist = meldinger.lastOrNull { it.sim != INGEN && it.sim in aktive }?.sim
        return sist ?: standard()
    }

    fun velg(context: Context, traad: Long, sub: Int) {
        lager(context).edit().putInt(traad.toString(), sub).apply()
    }

    fun neste(context: Context, sub: Int): Int {
        val aktive = liste(context).map { it.subscriptionId }
        if (aktive.isEmpty()) return sub
        val i = aktive.indexOf(sub)
        return aktive[(i + 1) % aktive.size]
    }

    fun sms(context: Context, sub: Int): SmsManager {
        val standard = context.getSystemService(SmsManager::class.java)
        if (sub == INGEN) return standard
        return if (Build.VERSION.SDK_INT >= 31) standard.createForSubscriptionId(sub) else @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(sub)
    }

    fun fraIntent(intent: android.content.Intent): Int =
        intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", intent.getIntExtra("subscription", INGEN))
}
