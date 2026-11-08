package me.arnee.sms

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import java.text.DateFormat
import java.util.Date
import me.arnee.design.WaddleTokens
import me.arnee.design.WaddleUi
import me.arnee.felles.Deler

class MainActivity : Activity() {
    private val endringer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            vis()
        }
    }

    override fun onResume() {
        super.onResume()
        val mangler = listOfNotNull(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null,
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (mangler.isNotEmpty()) requestPermissions(mangler.toTypedArray(), 1)
        contentResolver.registerContentObserver(Uri.parse("content://mms-sms/"), true, endringer)
        vis()
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(endringer)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        vis()
    }

    private fun gjorStandard() {
        if (Build.VERSION.SDK_INT >= 29) {
            val roller = getSystemService(RoleManager::class.java)
            startActivityForResult(roller.createRequestRoleIntent(RoleManager.ROLE_SMS), 2)
        } else {
            startActivity(Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName))
        }
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, "Meldinger"))
            if (!Lager.standard(k)) {
                addView(ui.box(k).apply {
                    addView(ui.listRow(k, "Standardapp", "Meldinger må være standard SMS-app for å ta imot og sende.", iconName = "message"))
                    addView(ui.text(k, "Bildemeldinger (MMS) kan bare leses her ennå. Nye MMS blir ikke hentet.", WaddleTokens.TEXT_SM, muted = true))
                    addView(ui.button(k, "Gjør til standard", primary = true) { gjorStandard() })
                })
            }
            addView(ui.button(k, "Ny melding", primary = Lager.standard(k)) {
                startActivity(Intent(k, SamtaleActivity::class.java))
            })
            if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                addView(ui.text(k, "Trenger tilgang til SMS for å vise meldingene.", muted = true))
            } else {
                val samtaler = runCatching { Lager.samtaler(k) }.getOrDefault(emptyList())
                if (samtaler.isEmpty()) addView(ui.text(k, "Ingen samtaler.", muted = true))
                val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                if (samtaler.isNotEmpty()) addView(ui.box(k).apply {
                    samtaler.forEachIndexed { i, s ->
                        if (i > 0) addView(ui.divider(k))
                        val navn = s.adresser.joinToString(", ") { Lager.navn(k, it) }.ifEmpty { "Ukjent" }
                        val utdrag = s.utdrag.lineSequence().firstOrNull().orEmpty().take(80)
                        val rad = ui.listRow(k, if (s.lest) navn else "$navn  ny", "${format.format(Date(s.tid))}  $utdrag", iconName = if (s.adresser.size > 1) "users" else "user")
                        addView(Deler.klikkbar(rad) {
                            startActivity(Intent(k, SamtaleActivity::class.java).putExtra("traad", s.id))
                        })
                    }
                })
            }
        }
        ui.screen(this, innhold)
    }
}
