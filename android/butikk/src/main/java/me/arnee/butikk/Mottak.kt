package me.arnee.butikk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

class Mottak : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val bekreft = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                bekreft?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Toast.makeText(context, "Installert", Toast.LENGTH_SHORT).show()
            else -> {
                val melding = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "kode $status"
                Toast.makeText(context, "Installasjonen feilet: $melding", Toast.LENGTH_LONG).show()
            }
        }
    }
}
