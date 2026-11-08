package me.arnee.vaer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class KlaerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ider: IntArray) {
        val svar = goAsync()
        Thread {
            runCatching { oppdater(context, manager, ider) }
            svar.finish()
        }.start()
    }

    companion object {
        fun oppdaterAlle(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ider = manager.getAppWidgetIds(ComponentName(context, KlaerWidget::class.java))
            if (ider.isNotEmpty()) Thread { runCatching { oppdater(context, manager, ider) } }.start()
        }

        private fun oppdater(context: Context, manager: AppWidgetManager, ider: IntArray) {
            val sted = Visning.steder(context).firstOrNull() ?: return
            val v = Visning.varsler[sted.navn]?.takeIf { it.utloper > System.currentTimeMillis() }
                ?: runCatching { Met.varsel(sted) }.getOrNull()?.also { Visning.varsler[sted.navn] = it }
            val p = v?.let { Klaer.beregn(it) }
            val visning = RemoteViews(context.packageName, R.layout.widget_klaer)
            val apne = PendingIntent.getActivity(
                context, 0,
                Intent(context, StedActivity::class.java).putExtra("navn", sted.navn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            visning.setOnClickPendingIntent(R.id.rot, apne)
            if (p == null) {
                visning.setTextViewText(R.id.klaer, "Fikk ikke hentet været.")
            } else {
                visning.setTextViewText(R.id.tittel, "${p.tittel}, ${sted.navn.substringBefore(",")}")
                visning.setTextViewText(R.id.temp, Klaer.temp(p))
                visning.setTextViewText(R.id.regn, if (p.regn >= 0.2) "Regn %.1f mm".format(p.regn) else "")
                visning.setTextViewText(R.id.klaer, p.klaer.joinToString("\n"))
                val id = context.resources.getIdentifier("ic_" + Met.ikon(p.symbol).replace('-', '_'), "drawable", context.packageName)
                if (id != 0) visning.setImageViewResource(R.id.ikon, id)
            }
            ider.forEach { manager.updateAppWidget(it, visning) }
        }
    }
}
