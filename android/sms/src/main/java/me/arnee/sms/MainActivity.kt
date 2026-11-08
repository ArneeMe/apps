package me.arnee.sms

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.widget.LinearLayout
import android.widget.Toast
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

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 2 && !Lager.standard(this)) {
            Toast.makeText(this, "Ble ikke standard. Velg Meldinger under Standardapper, SMS.", Toast.LENGTH_LONG).show()
        }
        vis()
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

    private var visArkiv = false

    private fun rad(navn: String, utdrag: String, tid: String, ny: Boolean, gruppe: Boolean) = WaddleUi.row(this, WaddleTokens.SPACE_3).apply {
        val pad = WaddleUi.dp(this@MainActivity, WaddleTokens.SPACE_3)
        setPadding(0, pad, 0, pad)
        gravity = android.view.Gravity.TOP
        addView(WaddleUi.icon(this@MainActivity, if (gruppe) "users" else "user"))
        addView(WaddleUi.column(this@MainActivity, WaddleTokens.SPACE_1).apply {
            addView(WaddleUi.text(this@MainActivity, navn, bold = ny).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            addView(WaddleUi.text(this@MainActivity, utdrag, WaddleTokens.TEXT_SM, muted = !ny).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(WaddleUi.text(this@MainActivity, tid, WaddleTokens.TEXT_SM, bold = ny, muted = !ny))
    }

    private fun slett(s: Samtale, navn: String) {
        if (!Lager.standard(this)) {
            Toast.makeText(this, "Bare standard SMS-app kan slette. Gjør Meldinger til standard først.", Toast.LENGTH_LONG).show()
            vis()
            return
        }
        AlertDialog.Builder(this)
            .setMessage("Slette samtalen med $navn?")
            .setPositiveButton("Slett") { _, _ ->
                if (!Lager.slett(this, s.id)) Toast.makeText(this, "Fikk ikke slettet samtalen.", Toast.LENGTH_LONG).show()
                vis()
            }
            .setNegativeButton("Avbryt") { _, _ -> vis() }
            .setOnCancelListener { vis() }
            .show()
    }

    private fun vis() {
        val ui = WaddleUi
        val k = this
        val innhold = ui.column(k, WaddleTokens.SPACE_6).apply {
            addView(Deler.tittel(k, if (visArkiv) "Arkiv" else "Meldinger", "settings-cog", "Innstillinger") {
                startActivity(Intent(k, InnstillingerActivity::class.java))
            })
            if (!Lager.standard(k) && !visArkiv) {
                addView(ui.box(k).apply {
                    addView(ui.listRow(k, "Standardapp", "Meldinger må være standard SMS-app for å ta imot og sende.", iconName = "message"))
                    addView(ui.text(k, "Bildemeldinger (MMS) kan bare leses her ennå. Nye MMS blir ikke hentet.", WaddleTokens.TEXT_SM, muted = true))
                    addView(ui.button(k, "Gjør til standard", primary = true) { gjorStandard() })
                    addView(ui.button(k, "Åpne standardapper") {
                        startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                    })
                })
            }
            addView(ui.row(k).apply {
                if (visArkiv) {
                    addView(ui.button(k, "Tilbake") { visArkiv = false; vis() })
                } else {
                    addView(ui.button(k, "Ny melding", primary = Lager.standard(k)) {
                        startActivity(Intent(k, SamtaleActivity::class.java))
                    })
                }
            })
            if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                addView(ui.text(k, "Trenger tilgang til SMS for å vise meldingene.", muted = true))
            } else {
                val alle = runCatching { Lager.samtaler(k) }.getOrDefault(emptyList())
                val (arkiv, aktive) = alle.partition { Lager.arkivert(k, it) }
                val samtaler = if (visArkiv) arkiv else aktive
                if (samtaler.isEmpty()) addView(ui.text(k, if (visArkiv) "Arkivet er tomt." else "Ingen samtaler.", muted = true))
                val byttet = Innstillinger.byttSveip(k)
                if (samtaler.isNotEmpty()) addView(ui.box(k).apply {
                    samtaler.forEachIndexed { i, s ->
                        if (i > 0) addView(ui.divider(k))
                        val navn = s.adresser.joinToString(", ") { Lager.navn(k, it) }.ifEmpty { "Ukjent" }
                        val utdrag = (Reaksjoner.tolk(s.utdrag)?.let { "Reagerte: ${it.ord}" } ?: s.utdrag).lineSequence().firstOrNull().orEmpty()
                        val arkiver = {
                            Lager.arkiver(k, s, !visArkiv)
                            vis()
                        }
                        val slettHandling = { slett(s, navn) }
                        val arkivTekst = if (visArkiv) "Hent tilbake" else "Arkiver"
                        addView(Sveip(
                            k, rad(navn, utdrag, Tid.kort(s.tid), !s.lest, s.adresser.size > 1),
                            if (byttet) "Slett" else arkivTekst,
                            if (byttet) arkivTekst else "Slett",
                            if (byttet) slettHandling else arkiver,
                            if (byttet) arkiver else slettHandling,
                        ) { startActivity(Intent(k, SamtaleActivity::class.java).putExtra("traad", s.id)) })
                    }
                })
                if (!visArkiv && arkiv.isNotEmpty()) {
                    addView(ui.button(k, "Arkiv (${arkiv.size})") { visArkiv = true; vis() })
                }
                if (!visArkiv && samtaler.isNotEmpty()) {
                    addView(ui.text(k, if (byttet) "Dra til venstre for å arkivere, til høyre for å slette." else "Dra til høyre for å arkivere, til venstre for å slette.", WaddleTokens.TEXT_SM, muted = true))
                }
            }
        }
        ui.screen(this, innhold)
    }

    override fun onBackPressed() {
        if (visArkiv) {
            visArkiv = false
            vis()
        } else {
            @Suppress("DEPRECATION") super.onBackPressed()
        }
    }
}
