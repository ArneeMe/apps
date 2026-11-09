package me.arnee.digipost

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class Kunde(
    val id: String = UUID.randomUUID().toString(),
    var navn: String = "",
    var nummer: String = "",
    var digipost: String = "",
    var telefon: String = "",
    var epost: String = "",
    var linje1: String = "",
    var linje2: String = "",
    var postnr: String = "",
    var sted: String = "",
    var gruppe: String = "",
    var ekstra: String = "",
    var notat: String = "",
    var status: String = "",
    var sjekket: Long = 0,
) {
    fun adresse() = nummer.filter { it.isDigit() }.takeIf { it.length == 11 || it.length == 9 } ?: digipost.trim()

    fun post() = Papir(navn, linje1, linje2, postnr, sted).takeIf { !it.tom() }

    fun felt(): Map<String, String> {
        val ekstraFelt = ekstra.lines().filter { it.contains(":") }.associate { it.substringBefore(":").trim().lowercase() to it.substringAfter(":").trim() }
        return mapOf(
            "navn" to navn, "fornavn" to navn.trim().substringBefore(" "), "nummer" to nummer, "telefon" to telefon, "epost" to epost,
            "adresse" to listOf(linje1, linje2).filter { it.isNotBlank() }.joinToString(", "), "postnr" to postnr, "sted" to sted,
            "gruppe" to gruppe, "dato" to LocalDate.now().format(DateTimeFormatter.ofPattern("d.M.yyyy")),
        ) + ekstraFelt
    }

    fun mottaker() = Mottaker(
        navn, adresse(), listOf(adresse(), statusTekst()).filter { it.isNotEmpty() }.joinToString(" · "), post(),
        harDigipost = status != "IKKE" && status != "UGYLDIG" && adresse().isNotEmpty(), flett = felt(),
    )

    fun statusTekst() = when (status) {
        "DIGIPOST" -> "Har Digipost"
        "IKKE" -> "Ikke Digipost, får papir"
        "UGYLDIG" -> "Ugyldig nummer"
        else -> if (adresse().isEmpty()) "Bare papir" else "Ikke sjekket"
    }

    fun json(): JSONObject = JSONObject().put("id", id).put("navn", navn).put("nummer", nummer).put("digipost", digipost).put("telefon", telefon)
        .put("epost", epost).put("linje1", linje1).put("linje2", linje2).put("postnr", postnr).put("sted", sted).put("gruppe", gruppe)
        .put("ekstra", ekstra).put("notat", notat).put("status", status).put("sjekket", sjekket)

    companion object {
        fun fra(j: JSONObject) = Kunde(
            j.getString("id"), j.optString("navn"), j.optString("nummer"), j.optString("digipost"), j.optString("telefon"), j.optString("epost"),
            j.optString("linje1"), j.optString("linje2"), j.optString("postnr"), j.optString("sted"), j.optString("gruppe"), j.optString("ekstra"),
            j.optString("notat"), j.optString("status"), j.optLong("sjekket"),
        )
    }
}

object Kunder {
    private val kolonner = listOf("navn", "nummer", "digipost", "telefon", "epost", "adresse", "adresse2", "postnr", "sted", "gruppe", "notat")

    private fun fil(k: Context) = File(k.filesDir, "kunder.json")

    fun alle(k: Context): List<Kunde> {
        val f = fil(k)
        if (!f.exists()) return emptyList()
        val liste = JSONArray(f.readText())
        return (0 until liste.length()).map { Kunde.fra(liste.getJSONObject(it)) }
    }

    fun lagre(k: Context, liste: List<Kunde>) {
        fil(k).writeText(JSONArray(liste.map { it.json() }).toString())
        if (Kopi.mappe(k) != null) Thread { Kopi.skriv(k) }.start()
    }

    fun en(k: Context, id: String) = alle(k).firstOrNull { it.id == id }

    fun sett(k: Context, kunde: Kunde) = lagre(k, alle(k).filter { it.id != kunde.id } + kunde)

    fun slett(k: Context, id: String) = lagre(k, alle(k).filter { it.id != id })

    fun grupper(k: Context) = alle(k).map { it.gruppe.trim() }.filter { it.isNotEmpty() }.distinct().sorted()

    fun flett(mal: String, felt: Map<String, String>) =
        Regex("\\{([^{}]+)\\}").replace(mal) { felt[it.groupValues[1].trim().lowercase()] ?: it.value }

    fun sjekk(o: Oppsett, kunde: Kunde) {
        val nummer = kunde.adresse()
        if (nummer.isEmpty()) return
        kunde.status = if (!nummer.all { it.isDigit() }) "DIGIPOST" else when (Digipost.identifiser(o, nummer)) {
            "DIGIPOST" -> "DIGIPOST"
            "INVALID" -> "UGYLDIG"
            else -> "IKKE"
        }
        kunde.sjekket = System.currentTimeMillis()
    }

    fun historikk(k: Context, kunde: Kunde): List<Sending> {
        val adresse = kunde.adresse()
        val navn = Samtaler.nokkel(kunde.navn)
        return Lager.historikk(k).filter { (adresse.isNotEmpty() && it.adresse == adresse) || Samtaler.nokkel(it.mottaker) == navn }.sortedByDescending { it.sendt }
    }

    fun eksport(liste: List<Kunde>): String {
        val celle = { v: String -> if (v.contains(';') || v.contains('"') || v.contains('\n')) "\"" + v.replace("\"", "\"\"") + "\"" else v }
        return (listOf(kolonner.joinToString(";")) + liste.map { c ->
            listOf(c.navn, c.nummer, c.digipost, c.telefon, c.epost, c.linje1, c.linje2, c.postnr, c.sted, c.gruppe, c.notat).joinToString(";") { celle(it) }
        }).joinToString("\n")
    }

    private fun rader(tekst: String, skille: Char): List<List<String>> {
        val rader = mutableListOf<List<String>>()
        var rad = mutableListOf<String>()
        val celle = StringBuilder()
        var sitat = false
        var i = 0
        while (i < tekst.length) {
            val c = tekst[i]
            when {
                sitat && c == '"' && tekst.getOrNull(i + 1) == '"' -> { celle.append('"'); i++ }
                c == '"' -> sitat = !sitat
                !sitat && c == skille -> { rad.add(celle.toString()); celle.clear() }
                !sitat && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && tekst.getOrNull(i + 1) == '\n') i++
                    rad.add(celle.toString()); celle.clear()
                    if (rad.any { it.isNotBlank() }) rader.add(rad)
                    rad = mutableListOf()
                }
                else -> celle.append(c)
            }
            i++
        }
        rad.add(celle.toString())
        if (rad.any { it.isNotBlank() }) rader.add(rad)
        return rader
    }

    fun importer(tekst: String): List<Kunde> {
        val ren = tekst.removePrefix("﻿")
        val forste = ren.lineSequence().firstOrNull() ?: return emptyList()
        val skille = listOf(';', ',', '\t').maxBy { s -> forste.count { it == s } }
        val alle = rader(ren, skille)
        if (alle.isEmpty()) return emptyList()
        val hode = alle[0].map { it.trim().lowercase() }
        val navn = mapOf(
            "navn" to listOf("navn", "name", "kunde", "fullt navn"),
            "nummer" to listOf("nummer", "fødselsnummer", "fodselsnummer", "fnr", "personnummer", "orgnr", "org.nr.", "organisasjonsnummer"),
            "digipost" to listOf("digipost", "digipost-adresse", "digipostadresse"),
            "telefon" to listOf("telefon", "mobil", "tlf", "phone"),
            "epost" to listOf("epost", "e-post", "email", "mail"),
            "adresse" to listOf("adresse", "gateadresse", "address"),
            "adresse2" to listOf("adresse2", "adresse 2", "c/o"),
            "postnr" to listOf("postnr", "postnummer", "zip"),
            "sted" to listOf("sted", "poststed", "by", "city"),
            "gruppe" to listOf("gruppe", "kategori", "group"),
            "notat" to listOf("notat", "kommentar", "note"),
        )
        val kjent = navn.values.flatten().toSet()
        val plass = navn.mapValues { (_, alias) -> hode.indexOfFirst { it in alias } }
        val ekstra = hode.withIndex().filter { it.value.isNotEmpty() && it.value !in kjent }
        return alle.drop(1).map { r ->
            val v = { felt: String -> plass[felt]?.takeIf { it >= 0 }?.let { r.getOrNull(it)?.trim() } ?: "" }
            Kunde(
                navn = v("navn"), nummer = v("nummer"), digipost = v("digipost"), telefon = v("telefon"), epost = v("epost"),
                linje1 = v("adresse"), linje2 = v("adresse2"), postnr = v("postnr"), sted = v("sted"), gruppe = v("gruppe"), notat = v("notat"),
                ekstra = ekstra.mapNotNull { (i, h) -> r.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }?.let { "$h: $it" } }.joinToString("\n"),
            )
        }.filter { it.navn.isNotBlank() }
    }
}
