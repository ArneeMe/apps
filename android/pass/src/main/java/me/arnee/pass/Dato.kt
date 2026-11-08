package me.arnee.pass

import java.util.Calendar

object Dato {
    private fun aar(yy: Int, fodsel: Boolean): Int {
        val naa = Calendar.getInstance().get(Calendar.YEAR) % 100
        return if (fodsel) (if (yy > naa) 1900 + yy else 2000 + yy) else 2000 + yy
    }

    fun vis(yymmdd: String, fodsel: Boolean): String {
        if (yymmdd.length != 6 || !yymmdd.all { it.isDigit() }) return yymmdd
        return "${yymmdd.substring(4, 6)}.${yymmdd.substring(2, 4)}.${aar(yymmdd.substring(0, 2).toInt(), fodsel)}"
    }

    fun alder(fodt: String): Int {
        if (fodt.length != 6) return 0
        val c = Calendar.getInstance()
        val y = aar(fodt.substring(0, 2).toInt(), true)
        val m = fodt.substring(2, 4).toInt()
        val d = fodt.substring(4, 6).toInt()
        var a = c.get(Calendar.YEAR) - y
        if (c.get(Calendar.MONTH) + 1 < m || (c.get(Calendar.MONTH) + 1 == m && c.get(Calendar.DAY_OF_MONTH) < d)) a--
        return a
    }

    fun fraVisning(tekst: String): String? {
        val t = tekst.filter { it.isDigit() }
        return when (t.length) {
            6 -> t
            8 -> t.substring(6, 8) + t.substring(2, 4) + t.substring(0, 2)
            else -> null
        }
    }
}
