package me.arnee.sms

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PduTest {

    @Test
    fun sendOgLesTilbake() {
        val bilde = ByteArray(5000) { (it % 251).toByte() }
        val p = Pdu.les(Pdu.send(listOf("+4712345678", "98765432"), "Hei på deg æøå", bilde, "T123"))
        assertEquals(Pdu.SEND_REQ, p.type)
        assertEquals("T123", p.transaksjon)
        assertEquals(listOf("+4712345678", "98765432"), p.til)
        assertEquals("application/vnd.wap.multipart.related", p.innholdstype)
        assertEquals(listOf("application/smil", "image/jpeg", "text/plain"), p.deler.map { it.type })
        assertArrayEquals(bilde, p.deler[1].data)
        assertEquals("Hei på deg æøå", p.deler[2].tekst())
        assertEquals("image", p.deler[1].id)
        assertEquals("text.txt", p.deler[2].plassering)
    }

    @Test
    fun varsel() {
        val fra = "+4799999999/TYPE=PLMN\u0000".toByteArray()
        val pdu = byteArrayOf(0x8C.toByte(), 0x82.toByte(), 0x98.toByte()) + "TID1\u0000".toByteArray() +
            byteArrayOf(0x8D.toByte(), 0x92.toByte(), 0x89.toByte(), (1 + fra.size).toByte(), 0x80.toByte()) + fra +
            byteArrayOf(0x8A.toByte(), 0x80.toByte(), 0x8E.toByte(), 0x02, 0x10, 0x00, 0x88.toByte(), 0x05, 0x81.toByte(), 0x03, 0x02, 0xA3.toByte(), 0x00, 0x83.toByte()) +
            "http://mms.telia.no/abc?x=1\u0000".toByteArray()
        val p = Pdu.les(pdu)
        assertEquals(Pdu.NOTIFICATION_IND, p.type)
        assertEquals("TID1", p.transaksjon)
        assertEquals("+4799999999", p.fra)
        assertEquals("http://mms.telia.no/abc?x=1", p.plassering)
    }

    @Test
    fun kvittering() {
        assertArrayEquals(
            byteArrayOf(0x8C.toByte(), 0x85.toByte(), 0x98.toByte(), 'A'.code.toByte(), 0, 0x8D.toByte(), 0x92.toByte()),
            Pdu.kvittering("A"),
        )
    }
}
