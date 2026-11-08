package me.arnee.pass

import java.math.BigInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BroTest {
    private val skaper = "0256b328b30c8bf5839e24058747879408bdb36241dc9c2e7c619faa12b2920967"

    @Test
    fun secp256k1() {
        val k = Kurve.SECP256K1
        assertTrue(k.paKurven(k.g))
        assertTrue(k.gange(k.n, k.g).uendelig)
        val q = k.gange(BigInteger.valueOf(1234567), k.g)
        assertEquals(q, k.punkt(k.komprimert(q)))
    }

    @Test
    fun rustVektor() {
        val min = BigInteger(1, ByteArray(32) { 7 })
        val nokkel = BroKrypto.nokkel(min, skaper)
        assertEquals("03f989420656df216794c7c591c40c2f9b7fb249e9ead3452217d9ec93217f94", BroKrypto.hex(nokkel))
        val nonce = BroKrypto.nonce(skaper)
        assertEquals("3fcbd1225cefafef96e2821d", BroKrypto.hex(nonce))
        assertEquals("72db6df8651ea177554399a55f9294837d6680b1de", BroKrypto.hex(BroKrypto.krypter(nokkel, nonce, "hello".toByteArray())))
    }

    @Test
    fun zlibRundtur() {
        val d = """{"proof":"abc"}""".repeat(50).toByteArray()
        val z = BroKrypto.pakk(d)
        assertEquals(0x78, z[0].toInt() and 0xFF)
        assertArrayEquals(d, BroKrypto.pakkUt(z))
    }
}
