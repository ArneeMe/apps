package me.arnee.pass

import java.math.BigInteger
import java.security.SecureRandom

class Punkt(val x: BigInteger?, val y: BigInteger?) {
    val uendelig get() = x == null
    override fun equals(other: Any?) = other is Punkt && other.x == x && other.y == y
    override fun hashCode() = (x?.hashCode() ?: 0) * 31 + (y?.hashCode() ?: 0)
}

class Kurve(val navn: String, p: String, a: String, b: String, gx: String, gy: String, n: String) {
    val p = BigInteger(p, 16)
    val a = BigInteger(a, 16)
    val b = BigInteger(b, 16)
    val g = Punkt(BigInteger(gx, 16), BigInteger(gy, 16))
    val n = BigInteger(n, 16)
    val bytes = (this.p.bitLength() + 7) / 8

    companion object {
        val INF = Punkt(null, null)

        val P256 = Kurve(
            "secp256r1",
            "FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF",
            "FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC",
            "5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B",
            "6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296",
            "4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5",
            "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551",
        )

        val BRAINPOOL256 = Kurve(
            "brainpoolP256r1",
            "A9FB57DBA1EEA9BC3E660A909D838D726E3BF623D52620282013481D1F6E5377",
            "7D5A0975FC2C3057EEF67530417AFFE7FB8055C126DC5C6CE94A4B44F330B5D9",
            "26DC5C6CE94A4B44F330B5D9BBD77CBF958416295CF7E1CE6BCCDC18FF8C07B6",
            "8BD2AEB9CB7E57CB2C4B482FFC81B7AFB9DE27E1E3BD23C23A4453BD9ACE3262",
            "547EF835C3DAC4FD97F8461A14611DC9C27745132DED8E545C1D54C72F046997",
            "A9FB57DBA1EEA9BC3E660A909D838D718C397AA3B561A6F7901E0E82974856A7",
        )

        fun fraParameter(id: Int): Kurve = when (id) {
            12 -> P256
            13 -> BRAINPOOL256
            else -> throw IllegalStateException("Passet bruker kurve $id, som appen ikke støtter ennå")
        }
    }

    fun paKurven(q: Punkt): Boolean {
        if (q.uendelig) return true
        val v = q.y!!.multiply(q.y).mod(p)
        val h = q.x!!.pow(3).add(a.multiply(q.x)).add(b).mod(p)
        return v == h
    }

    fun pluss(s: Punkt, t: Punkt): Punkt {
        if (s.uendelig) return t
        if (t.uendelig) return s
        if (s.x == t.x) {
            if ((s.y!!.add(t.y!!)).mod(p) == BigInteger.ZERO) return INF
            val l = s.x!!.pow(2).multiply(BigInteger.valueOf(3)).add(a).multiply(s.y.shiftLeft(1).modInverse(p)).mod(p)
            val x = l.pow(2).subtract(s.x.shiftLeft(1)).mod(p)
            return Punkt(x, l.multiply(s.x.subtract(x)).subtract(s.y).mod(p))
        }
        val l = t.y!!.subtract(s.y!!).multiply(t.x!!.subtract(s.x!!).modInverse(p)).mod(p)
        val x = l.pow(2).subtract(s.x).subtract(t.x).mod(p)
        return Punkt(x, l.multiply(s.x.subtract(x)).subtract(s.y).mod(p))
    }

    fun gange(k: BigInteger, q: Punkt): Punkt {
        var r = INF
        var d = q
        for (i in 0 until k.bitLength()) {
            if (k.testBit(i)) r = pluss(r, d)
            d = pluss(d, d)
        }
        return r
    }

    fun hemmelig(): BigInteger {
        val r = SecureRandom()
        while (true) {
            val k = BigInteger(n.bitLength(), r)
            if (k.signum() > 0 && k < n) return k
        }
    }

    fun tall(v: BigInteger): ByteArray {
        val b = v.toByteArray()
        return when {
            b.size == bytes -> b
            b.size > bytes -> b.copyOfRange(b.size - bytes, b.size)
            else -> ByteArray(bytes - b.size) + b
        }
    }

    fun kode(q: Punkt) = byteArrayOf(4) + tall(q.x!!) + tall(q.y!!)

    fun punkt(d: ByteArray): Punkt {
        require(d[0] == 4.toByte()) { "Bare ukomprimerte punkter støttes" }
        val q = Punkt(BigInteger(1, d.copyOfRange(1, 1 + bytes)), BigInteger(1, d.copyOfRange(1 + bytes, 1 + 2 * bytes)))
        require(paKurven(q)) { "Punktet fra passet er ikke på kurven" }
        return q
    }
}
