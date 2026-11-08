package me.arnee.sms

import android.content.Context

object Innstillinger {
    private fun lager(context: Context) = context.getSharedPreferences("innstillinger", Context.MODE_PRIVATE)

    fun byttSveip(context: Context) = lager(context).getBoolean("bytt_sveip", false)
    fun leveringsrapport(context: Context) = lager(context).getBoolean("leveringsrapport", false)
    fun enterSender(context: Context) = lager(context).getBoolean("enter_sender", false)
    fun svarFraVarsel(context: Context) = lager(context).getBoolean("svar_fra_varsel", true)
    fun visInnhold(context: Context) = lager(context).getBoolean("vis_innhold", true)
    fun mindreTekst(context: Context) = lager(context).getBoolean("mindre_tekst", true)

    fun skaler(base: Context): Context {
        if (!mindreTekst(base)) return base
        val oppsett = android.content.res.Configuration(base.resources.configuration)
        oppsett.fontScale *= 0.87f
        return base.createConfigurationContext(oppsett)
    }

    fun sett(context: Context, navn: String, verdi: Boolean) {
        lager(context).edit().putBoolean(navn, verdi).apply()
    }
}
