package me.arnee.vaer

import kotlin.math.roundToInt

class Plagg(val tittel: String, val min: Double, val maks: Double, val regn: Double, val vind: Double, val symbol: String, val klaer: List<String>)

object Klaer {
    fun periode(v: Varsel, naa: java.time.ZonedDateTime = java.time.ZonedDateTime.now()): Pair<String, List<Time>> {
        val iMorgen = naa.hour >= 20
        val fra = if (iMorgen) naa.toLocalDate().plusDays(1).atTime(7, 0).atZone(naa.zone) else naa.minusHours(1)
        val til = (if (iMorgen) naa.toLocalDate().plusDays(1) else naa.toLocalDate()).atTime(20, 0).atZone(naa.zone)
        val timer = v.timer.filter { t ->
            val tid = java.time.Instant.ofEpochMilli(t.tid).atZone(naa.zone)
            !tid.isBefore(fra) && tid.isBefore(til)
        }
        return (if (iMorgen) "I morgen" else "I dag") to timer
    }

    fun velg(min: Double, regn: Double, vind: Double): List<String> {
        val ut = mutableListOf<String>()
        when {
            min <= -5 -> ut += listOf("Vinterjakke", "Ullundertøy", "Lue, votter og skjerf")
            min <= 2 -> ut += listOf("Vinterjakke", "Lue og votter")
            min <= 8 -> ut += listOf("Jakke", "Genser", "Lue")
            min <= 14 -> ut += listOf("Lett jakke", "Genser")
            min <= 20 -> ut += listOf("Genser eller tynn jakke")
            else -> ut += listOf("T-skjorte", "Shorts")
        }
        if (regn >= 0.2) ut += if (min <= 2) "Vanntette sko" else "Regnjakke og vanntette sko"
        if (vind >= 8) ut += "Vindtett ytterlag"
        return ut
    }

    fun beregn(v: Varsel): Plagg? {
        val (tittel, timer) = periode(v)
        if (timer.isEmpty()) return null
        val min = timer.minOf { it.temp }
        val maks = timer.maxOf { it.temp }
        val regn = timer.sumOf { it.nedbor }
        val vind = timer.maxOf { it.vind }
        val symbol = timer.firstOrNull { it.nedbor > 0 }?.symbol ?: timer[timer.size / 2].symbol
        return Plagg(tittel, min, maks, regn, vind, symbol, velg(min, regn, vind))
    }

    fun temp(p: Plagg) = if (p.min.roundToInt() == p.maks.roundToInt()) "${p.min.roundToInt()}°" else "${p.min.roundToInt()}–${p.maks.roundToInt()}°"
}
