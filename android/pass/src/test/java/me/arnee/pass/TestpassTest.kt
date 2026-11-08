package me.arnee.pass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestpassTest {

    @Test
    fun mrzFraIcaoEksempel() {
        val m = Testpass.mrz("Eriksson", "Anna Maria", "L898902C3", "UTO", "740812", "F", "120415")
        assertEquals("P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<", m.substring(0, 44))
        assertEquals("L898902C36UTO7408122F1204159<<<<<<<<<<<<<<08", m.substring(44))
    }

    @Test
    fun personFraDg1() {
        val dg1 = Testpass.dg1(Testpass.mrz("Nordmann", "Ola", "AB1234567", "NOR", "900101", "M", "300101"))
        val p = Dokument.person(dg1)
        assertEquals("NORDMANN", p.etternavn)
        assertEquals("OLA", p.fornavn)
        assertEquals("NOR", p.nasjonalitet)
        assertEquals("900101", p.fodt)
        assertEquals("AB1234567", p.nummer)
    }

    @Test
    fun asn1Oid() {
        assertEquals("2.23.136.1.1.1", Asn1.oid(Asn1.oidBytes("2.23.136.1.1.1")))
        assertEquals("1.2.840.113549.1.9.4", Asn1.oid(Asn1.oidBytes("1.2.840.113549.1.9.4")))
    }

    @Test
    fun testpassErGyldig() {
        val sert = javaClass.classLoader!!.getResource("zkr_dsc.der")!!.readBytes()
        val nokkel = Testpass.nokkel(javaClass.classLoader!!.getResource("zkr_dsc_nokkel.der")!!.readBytes())
        val d = Testpass.lag("Nordmann", "Ola", "AB1234567", "NOR", "900101", "M", "300101", sert, nokkel)
        val (alg, grupper) = d.hasher()
        assertEquals("SHA-256", alg)
        assertTrue(grupper.containsKey(1))
        val sjekk = d.sjekk()
        assertEquals(null, sjekk.feil)
        assertTrue(sjekk.dataUendret)
        assertTrue(sjekk.signaturGyldig)
    }
}
