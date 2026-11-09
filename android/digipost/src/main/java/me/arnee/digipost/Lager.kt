package me.arnee.digipost

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class Sending(
    val dokument: String,
    val test: Boolean,
    val sendt: Long,
    val mottaker: String,
    val adresse: String,
    val emne: String,
    val antall: Int,
    var status: String,
    var kanal: String,
    var levert: String,
    var lest: String,
    val leveres: String,
    var hendelser: String = "",
)

object Lager {
    const val PROD = "https://api.digipost.no"
    const val TEST = "https://api.test.digipost.no"

    private fun lager(k: Context) = k.getSharedPreferences("digipost", Context.MODE_PRIVATE)

    fun test(k: Context) = lager(k).getBoolean("test", false)

    fun settTest(k: Context, verdi: Boolean) = lager(k).edit().putBoolean("test", verdi).apply()

    private fun nokkel(k: Context, navn: String) = if (test(k)) navn + "_test" else navn

    fun sertifikat(k: Context) = File(k.filesDir, nokkel(k, "sertifikat") + ".p12")

    fun tekst(k: Context, navn: String, standard: String = "") = lager(k).getString(nokkel(k, navn), standard)!!

    fun settTekst(k: Context, navn: String, verdi: String) = lager(k).edit().putString(nokkel(k, navn), verdi).apply()

    fun bryter(k: Context, navn: String, standard: Boolean = false) = lager(k).getBoolean(navn, standard)

    fun settBryter(k: Context, navn: String, verdi: Boolean) = lager(k).edit().putBoolean(navn, verdi).apply()

    fun tall(k: Context, navn: String, standard: Int = 0) = lager(k).getInt(navn, standard)

    fun settTall(k: Context, navn: String, verdi: Int) = lager(k).edit().putInt(navn, verdi).apply()

    fun streng(k: Context, navn: String, standard: String) = lager(k).getString(navn, standard)!!

    fun settStreng(k: Context, navn: String, verdi: String) = lager(k).edit().putString(navn, verdi).apply()

    fun valg(k: Context) = Valg(
        sensitiv = bryter(k, "sensitiv"),
        niva = streng(k, "niva", "PASSWORD"),
        smsTimer = tall(k, "sms_timer"),
        smsAlltid = bryter(k, "sms_alltid"),
        retur = retur(k),
        farger = bryter(k, "farger"),
        makuler = bryter(k, "makuler"),
        ulestDager = tall(k, "ulest_dager"),
        registrerDager = tall(k, "registrer_dager", 14),
    )

    fun retur(k: Context): Papir? =
        Papir(tekst(k, "retur_navn"), tekst(k, "retur_linje1"), tekst(k, "retur_linje2"), tekst(k, "retur_postnr"), tekst(k, "retur_sted")).takeIf { !it.tom() }

    private fun innboksFil(k: Context) = File(k.filesDir, nokkel(k, "innboks") + ".json")

    fun innboks(k: Context): List<Innkommende> {
        val fil = innboksFil(k)
        if (!fil.exists()) return emptyList()
        val liste = JSONArray(fil.readText())
        return (0 until liste.length()).map {
            val d = liste.getJSONObject(it)
            Innkommende(d.getString("id"), d.optString("emne"), d.optString("avsender"), d.optString("tid"), d.optString("type"), d.optString("innhold"), d.optString("slett"), d.optInt("vedlegg"))
        }
    }

    fun settInnboks(k: Context, liste: List<Innkommende>) {
        val json = JSONArray(liste.map {
            JSONObject().put("id", it.id).put("emne", it.emne).put("avsender", it.avsender).put("tid", it.tid).put("type", it.type)
                .put("innhold", it.innhold).put("slett", it.slett).put("vedlegg", it.vedlegg)
        })
        innboksFil(k).writeText(json.toString())
    }

    fun lest(k: Context): Set<String> = lager(k).getStringSet(nokkel(k, "lest"), emptySet())!!

    fun settLest(k: Context, id: String) = lager(k).edit().putStringSet(nokkel(k, "lest"), lest(k) + id).apply()

    fun oppsett(k: Context): Oppsett? {
        val id = tekst(k, "avsender")
        if (id.isEmpty() || !sertifikat(k).exists()) return null
        return Oppsett(id, sertifikat(k).readBytes(), tekst(k, "passord"), if (test(k)) TEST else PROD, tekst(k, "pa_vegne_av"))
    }

    fun favoritter(k: Context): List<Mottaker> {
        val liste = JSONArray(lager(k).getString(nokkel(k, "favoritter"), "[]"))
        return (0 until liste.length()).map {
            val f = liste.getJSONObject(it)
            Mottaker(f.getString("navn"), f.getString("adresse"), f.optString("detalj"), post(f.optJSONObject("post")))
        }
    }

    fun settFavoritter(k: Context, liste: List<Mottaker>) {
        val json = JSONArray(liste.map { JSONObject().put("navn", it.navn).put("adresse", it.adresse).put("detalj", it.detalj).put("post", it.post?.let { p -> postJson(p) }) })
        lager(k).edit().putString(nokkel(k, "favoritter"), json.toString()).apply()
    }

    fun postJson(p: Papir): JSONObject = JSONObject().put("navn", p.navn).put("linje1", p.linje1).put("linje2", p.linje2).put("postnr", p.postnr).put("sted", p.sted)

    fun post(j: JSONObject?) = j?.let { Papir(it.optString("navn"), it.optString("linje1"), it.optString("linje2"), it.optString("postnr"), it.optString("sted")) }

    private fun historikkFil(k: Context) = File(k.filesDir, "historikk.json")

    fun historikk(k: Context): List<Sending> {
        val fil = historikkFil(k)
        if (!fil.exists()) return emptyList()
        val liste = JSONArray(fil.readText())
        return (0 until liste.length()).map {
            val s = liste.getJSONObject(it)
            Sending(
                s.getString("dokument"), s.optBoolean("test"), s.getLong("sendt"), s.getString("mottaker"), s.getString("adresse"),
                s.getString("emne"), s.optInt("antall", 1), s.optString("status"), s.optString("kanal"), s.optString("levert"),
                s.optString("lest"), s.optString("leveres"), s.optString("hendelser"),
            )
        }
    }

    fun settHistorikk(k: Context, liste: List<Sending>) {
        val json = JSONArray(liste.map {
            JSONObject().put("dokument", it.dokument).put("test", it.test).put("sendt", it.sendt).put("mottaker", it.mottaker)
                .put("adresse", it.adresse).put("emne", it.emne).put("antall", it.antall).put("status", it.status).put("kanal", it.kanal)
                .put("levert", it.levert).put("lest", it.lest).put("leveres", it.leveres).put("hendelser", it.hendelser)
        })
        historikkFil(k).writeText(json.toString())
    }
}
