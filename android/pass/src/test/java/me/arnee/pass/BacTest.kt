package me.arnee.pass

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BacTest {
    private val bac = Bac("L898902C", "690806", "940623")

    @Test
    fun nokler() {
        assertEquals("L898902C<369080619406236", Mrz.nokkelInfo("L898902C", "690806", "940623"))
        assertEquals("AB94FDECF2674FDFB9B391F85D7F76F2", bac.kEnc.hex())
        assertEquals("7962D9ECE03D1ACD4C76089DCE131543", bac.kMac.hex())
    }

    @Test
    fun utfordringOgOkt() {
        val rndIc = "4608F91988702212".unhex()
        val rndIfd = "781723860C06C226".unhex()
        val kIfd = "0B795240CB7049B01C19B33E32804F0B".unhex()
        val (kommando, _, _) = bac.utfordring(rndIc, rndIfd, kIfd)
        assertEquals("72C29C2371CC9BDB65B779B8E8D37B29ECC154AA56A8799FAE2F498F76ED92F25F1448EEA8AD90A7", kommando.hex())
        val sm = bac.okt("46B9342A41396CD7386BF5803104D7CEDC122B9132139BAF2EEDC94EE178534F2F2D235D074D7449".unhex(), rndIc, rndIfd, kIfd)
        assertEquals("979EC13B1CBFE9DCD01AB0FED307EAE5", sm.ksEnc.hex())
        assertEquals("F1CB1F1FB5ADF208806B89DC579DC1F8", sm.ksMac.hex())
        assertEquals("887022120C06C226", sm.ssc.hex())

        assertEquals("0CA4020C158709016375432908C044F68E08BF8B92D635FF24F800", sm.beskytt(0x00, 0xA4, 0x02, 0x0C, "011E".unhex(), null).hex())
        assertArrayEquals(ByteArray(0), sm.pakkUt("990290008E08FA855A5D4C50A8ED9000".unhex()))
        assertEquals("0CB0000000000D9701048E08ED6705417E96BA5500", sm.beskytt(0x00, 0xB0, 0x00, 0x00, null, 4).hex())
        assertEquals("60145F01", sm.pakkUt("8709019FF0EC34F9922651990290008E08AD55CC17140B2DED9000".unhex()).hex())
    }
}
