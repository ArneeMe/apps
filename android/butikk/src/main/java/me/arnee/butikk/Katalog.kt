package me.arnee.butikk

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray

class App(val navn: String, val pakke: String, val om: String, val versjon: Long, val apk: String, val adb: List<String>)

object Katalog {
    private const val ADRESSE = "https://github.com/ArneeMe/apps/releases/download/siste/"

    private fun apne(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 60_000
        instanceFollowRedirects = true
    }

    fun hent(): List<App> {
        val liste = apne(ADRESSE + "apper.json").inputStream.bufferedReader().use { JSONArray(it.readText()) }
        return (0 until liste.length()).map {
            val a = liste.getJSONObject(it)
            val adb = a.optJSONArray("adb") ?: JSONArray()
            App(
                a.getString("navn"),
                a.getString("pakke"),
                a.optString("om", ""),
                a.getLong("versjon"),
                a.getString("apk"),
                (0 until adb.length()).map { i -> adb.getString(i) },
            )
        }
    }

    fun installert(context: Context, pakke: String): Long? = try {
        val info = context.packageManager.getPackageInfo(pakke, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun installer(context: Context, app: App) {
        val installer = context.packageManager.packageInstaller
        val parametre = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.pakke)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(parametre)
        installer.openSession(id).use { okt ->
            apne(ADRESSE + app.apk).inputStream.use { inn ->
                okt.openWrite(app.apk, 0, -1).use { ut ->
                    inn.copyTo(ut)
                    okt.fsync(ut)
                }
            }
            val flagg = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val svar = PendingIntent.getBroadcast(context, id, Intent(context, Mottak::class.java), flagg or PendingIntent.FLAG_UPDATE_CURRENT)
            okt.commit(svar.intentSender)
        }
    }
}
