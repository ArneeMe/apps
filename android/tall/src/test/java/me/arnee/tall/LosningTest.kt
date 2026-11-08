package me.arnee.tall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LosningTest {

    @Test
    fun finnerEksakt() {
        val r = Losning.los(listOf(75, 50, 2, 3, 8, 7), 812)
        assertEquals(0, r.avstand)
        assertTrue(r.svar.all { it.verdi == 812 })
        assertTrue(r.svar.size <= 10)
    }

    @Test
    fun kortestFoerst() {
        val r = Losning.los(listOf(100, 1, 2, 3, 4, 5), 200)
        assertEquals(listOf("100 × 2 = 200"), r.svar.first().steg)
    }

    @Test
    fun tallErSvaret() {
        val r = Losning.los(listOf(100, 1, 2, 3, 4, 5), 100)
        assertEquals(emptyList<String>(), r.svar.first().steg)
    }

    @Test
    fun nermestNaarIngenEksakt() {
        val r = Losning.los(listOf(1, 1, 2, 2, 3, 3), 999)
        assertTrue(r.avstand > 0)
        assertTrue(r.svar.all { kotlin.math.abs(it.verdi - 999) == r.avstand })
    }

    @Test
    fun ikkeSammeLosningToGanger() {
        val r = Losning.los(listOf(25, 4, 1, 1, 1, 1), 100, maks = 1000)
        val nokler = r.svar.map { it.steg.sorted() }
        assertEquals(nokler.size, nokler.toSet().size)
    }
}
