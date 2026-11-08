package me.arnee.pass

import java.io.ByteArrayOutputStream

class Tlv(val tag: Int, val verdi: ByteArray, val hele: ByteArray) {
    val barn: List<Tlv> by lazy { Asn1.les(verdi) }
    fun finn(tag: Int): Tlv? = barn.firstOrNull { it.tag == tag }
    fun alle(tag: Int): List<Tlv> = barn.filter { it.tag == tag }
}

object Asn1 {
    fun les(data: ByteArray, start: Int = 0, slutt: Int = data.size): List<Tlv> {
        val ut = mutableListOf<Tlv>()
        var i = start
        while (i < slutt) {
            val begynn = i
            var tag = data[i++].toInt() and 0xFF
            if (tag == 0x00 || tag == 0xFF) continue
            if (tag and 0x1F == 0x1F) {
                do {
                    val b = data[i++].toInt() and 0xFF
                    tag = (tag shl 8) or b
                } while (b and 0x80 != 0)
            }
            var lengde = data[i++].toInt() and 0xFF
            if (lengde and 0x80 != 0) {
                val n = lengde and 0x7F
                lengde = 0
                repeat(n) { lengde = (lengde shl 8) or (data[i++].toInt() and 0xFF) }
            }
            val verdi = data.copyOfRange(i, i + lengde)
            i += lengde
            ut += Tlv(tag, verdi, data.copyOfRange(begynn, i))
        }
        return ut
    }

    fun en(data: ByteArray): Tlv = les(data).first()

    fun lengde(n: Int): ByteArray = when {
        n < 0x80 -> byteArrayOf(n.toByte())
        n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
        n < 0x10000 -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
        else -> byteArrayOf(0x83.toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    }

    fun tlv(tag: Int, vararg deler: ByteArray): ByteArray {
        val innhold = ByteArrayOutputStream().apply { deler.forEach { write(it) } }.toByteArray()
        val t = ByteArrayOutputStream()
        if (tag > 0xFFFF) t.write(tag shr 16)
        if (tag > 0xFF) t.write(tag shr 8)
        t.write(tag)
        t.write(lengde(innhold.size))
        t.write(innhold)
        return t.toByteArray()
    }

    fun oid(data: ByteArray): String {
        val deler = mutableListOf<Long>()
        val forste = data[0].toInt() and 0xFF
        deler += (forste / 40).toLong()
        deler += (forste % 40).toLong()
        var v = 0L
        for (i in 1 until data.size) {
            val b = data[i].toInt() and 0xFF
            v = (v shl 7) or (b and 0x7F).toLong()
            if (b and 0x80 == 0) {
                deler += v
                v = 0
            }
        }
        return deler.joinToString(".")
    }

    fun oidBytes(oid: String): ByteArray {
        val deler = oid.split(".").map { it.toLong() }
        val ut = ByteArrayOutputStream()
        ut.write((deler[0] * 40 + deler[1]).toInt())
        for (d in deler.drop(2)) {
            val biter = mutableListOf<Int>()
            var x = d
            do {
                biter.add(0, (x and 0x7F).toInt())
                x = x shr 7
            } while (x > 0)
            biter.forEachIndexed { i, b -> ut.write(if (i < biter.size - 1) b or 0x80 else b) }
        }
        return ut.toByteArray()
    }

    fun heltall(n: Int): ByteArray {
        var bytes = java.math.BigInteger.valueOf(n.toLong()).toByteArray()
        return tlv(0x02, bytes)
    }
}
