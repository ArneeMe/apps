package me.arnee.tall

import kotlin.math.abs

object Losning {
    class Svar(val verdi: Int, val steg: List<String>)

    class Resultat(val avstand: Int, val antall: Int, val svar: List<Svar>)

    fun los(tall: List<Int>, mal: Int, maks: Int = 10): Resultat {
        val funnet = HashMap<String, Svar>()
        var best = Int.MAX_VALUE

        fun se(verdi: Int, steg: List<String>) {
            val avstand = abs(verdi - mal)
            if (avstand > best) return
            if (avstand < best) {
                best = avstand
                funnet.clear()
            }
            funnet.getOrPut(steg.sorted().joinToString("|")) { Svar(verdi, steg) }
        }

        fun sok(verdier: List<Int>, steg: List<List<String>>) {
            for (i in verdier.indices) for (j in i + 1 until verdier.size) {
                val a = maxOf(verdier[i], verdier[j])
                val b = minOf(verdier[i], verdier[j])
                val sa = if (verdier[i] >= verdier[j]) steg[i] else steg[j]
                val sb = if (verdier[i] >= verdier[j]) steg[j] else steg[i]
                val rest = verdier.filterIndexed { k, _ -> k != i && k != j }
                val restSteg = steg.filterIndexed { k, _ -> k != i && k != j }
                fun prov(r: Int, tegn: String) {
                    val s = sa + sb + "$a $tegn $b = $r"
                    se(r, s)
                    if (rest.isNotEmpty()) sok(rest + r, restSteg + listOf(s))
                }
                prov(a + b, "+")
                if (a != b && a - b != b) prov(a - b, "−")
                if (b != 1) prov(a * b, "×")
                if (b != 1 && a % b == 0 && a / b != b) prov(a / b, "÷")
            }
        }

        tall.forEach { se(it, emptyList()) }
        sok(tall, tall.map { emptyList<String>() })
        val svar = funnet.values.sortedWith(compareBy({ it.steg.size }, { it.steg.joinToString() }))
        return Resultat(best, svar.size, svar.take(maks))
    }
}
