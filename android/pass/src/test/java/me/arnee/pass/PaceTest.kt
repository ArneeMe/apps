package me.arnee.pass

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceTest {

    @Test
    fun cmacRfc4493() {
        val k = "2B7E151628AED2A6ABF7158809CF4F3C".unhex()
        assertEquals("BB1D6929E95937287FA37D129B756746", Aes.cmac(k, ByteArray(0)).hex())
        assertEquals("070A16B46B4D4144F79BDD9DD04A287C", Aes.cmac(k, "6BC1BEE22E409F96E93D7E117393172A".unhex()).hex())
        assertEquals("DFA66747DE9AE63030CA32611497C827", Aes.cmac(k, "6BC1BEE22E409F96E93D7E117393172AAE2D8A571E03AC9C9EB76FAC45AF8E5130C81C46A35CE411".unhex()).hex())
    }

    @Test
    fun kurveneErRiktige() {
        for (k in listOf(Kurve.P256, Kurve.BRAINPOOL256)) {
            assertTrue(k.navn, k.paKurven(k.g))
            assertTrue(k.navn, k.gange(k.n, k.g).uendelig)
        }
    }

    @Test
    fun ecdhGirSammeHemmelighet() {
        val k = Kurve.BRAINPOOL256
        val a = k.hemmelig()
        val b = k.hemmelig()
        assertEquals(k.gange(a, k.gange(b, k.g)), k.gange(b, k.gange(a, k.g)))
        val g2 = Pace.avbildning(k, BigInteger.valueOf(12345), k.gange(a, k.g))
        assertTrue(k.paKurven(g2))
    }

    @Test
    fun passordnokkelBrukerTeller3() {
        val info = Mrz.nokkelInfo("T22000129", "640812", "101031")
        assertEquals(16, Pace.passordnokkel(info, 16).size)
        assertEquals(32, Pace.passordnokkel(info, 32).size)
    }
}
