package me.arnee.sms

class Reaksjon(val ord: String, val sitat: String, val fjernet: Boolean)

object Reaksjoner {
    private val verb = listOf(
        "satte et hjerte ved" to "❤️",
        "elsket" to "❤️",
        "likte" to "👍",
        "mislikte" to "👎",
        "👎" to "👎",
        "lo av" to "😂",
        "fremhevet" to "‼️",
        "uthevet" to "‼️",
        "la vekt på" to "‼️",
        "stilte spørsmål ved" to "❓",
        "spurte om" to "❓",
        "loved" to "❤️",
        "liked" to "👍",
        "disliked" to "👎",
        "laughed at" to "😂",
        "emphasized" to "‼️",
        "questioned" to "❓",
    )

    private val fjern = listOf("fjernet et hjerte fra", "fjernet", "removed a heart from", "removed")

    private val sitat = Regex("^(.+?)\\s*[«“\"](.+)[»”\"]\\s*$", RegexOption.DOT_MATCHES_ALL)

    fun tolk(tekst: String): Reaksjon? {
        val treff = sitat.matchEntire(tekst.trim()) ?: return null
        val handling = treff.groupValues[1].trim().lowercase()
        val innhold = treff.groupValues[2].trim()
        fjern.firstOrNull { handling.startsWith(it) }?.let { start ->
            val resten = handling.removePrefix(start).trim()
            val ord = verb.firstOrNull { resten.contains(it.first) }?.second ?: "❤️"
            return Reaksjon(ord, innhold, true)
        }
        verb.firstOrNull { handling == it.first }?.let { return Reaksjon(it.second, innhold, false) }
        val reagerte = Regex("^(reagerte med|reacted) (.+?)( på| to)?$").matchEntire(handling)
        if (reagerte != null) return Reaksjon(reagerte.groupValues[2], innhold, false)
        return null
    }

    fun passer(sitat: String, tekst: String): Boolean {
        val s = sitat.trimEnd('…', '.').trim()
        if (s.isEmpty()) return false
        return tekst.trim() == s || tekst.trim().startsWith(s)
    }
}
