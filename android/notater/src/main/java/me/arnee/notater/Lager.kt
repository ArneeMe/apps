package me.arnee.notater

import android.content.Context
import java.io.File

class Oppgave(val tekst: String, val ferdig: Boolean)

object Lager {

    private fun mappe(context: Context) = File(context.filesDir, "notater").apply { mkdirs() }

    private fun oppgaveFil(context: Context) = File(context.filesDir, "oppgaver.txt")

    fun notater(context: Context): List<File> =
        mappe(context).listFiles { f -> f.name.endsWith(".txt") }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun nyttNotat(context: Context, tekst: String = ""): File =
        File(mappe(context), "${System.currentTimeMillis()}.txt").apply { writeText(tekst) }

    fun notat(context: Context, navn: String) = File(mappe(context), navn)

    fun tittel(fil: File): String =
        fil.readText().lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "Tomt notat"

    fun oppgaver(context: Context): List<Oppgave> {
        val fil = oppgaveFil(context)
        if (!fil.exists()) return emptyList()
        return fil.readLines().filter { it.length > 2 }.map { Oppgave(it.substring(2), it.startsWith("x ")) }
    }

    private fun lagre(context: Context, oppgaver: List<Oppgave>) {
        oppgaveFil(context).writeText(oppgaver.joinToString("") { (if (it.ferdig) "x " else "- ") + it.tekst + "\n" })
    }

    fun leggTil(context: Context, tekst: String) = lagre(context, oppgaver(context) + Oppgave(tekst, false))

    fun veksle(context: Context, indeks: Int) =
        lagre(context, oppgaver(context).mapIndexed { i, o -> if (i == indeks) Oppgave(o.tekst, !o.ferdig) else o })

    fun fjernFerdige(context: Context) = lagre(context, oppgaver(context).filter { !it.ferdig })
}
