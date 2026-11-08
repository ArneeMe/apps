package me.arnee.sms

import java.io.ByteArrayOutputStream

class Del(val type: String, val tegnsett: Int, val navn: String?, val id: String?, val plassering: String?, val data: ByteArray) {
    fun tekst(): String = String(data, when (tegnsett) {
        4 -> Charsets.ISO_8859_1
        3 -> Charsets.US_ASCII
        1000, 1015 -> Charsets.UTF_16
        else -> Charsets.UTF_8
    })
}

class Pakke {
    var type = 0
    var transaksjon: String? = null
    var plassering: String? = null
    var meldingId: String? = null
    var fra: String? = null
    val til = mutableListOf<String>()
    val kopi = mutableListOf<String>()
    var dato = 0L
    var emne: String? = null
    var innholdstype: String? = null
    val deler = mutableListOf<Del>()
}

object Pdu {
    const val SEND_REQ = 0x80
    const val NOTIFICATION_IND = 0x82
    const val RETRIEVE_CONF = 0x84

    private val kjente = arrayOf(
        "*/*", "text/*", "text/html", "text/plain", "text/x-hdml", "text/x-ttml", "text/x-vCalendar", "text/x-vCard",
        "text/vnd.wap.wml", "text/vnd.wap.wmlscript", "text/vnd.wap.wta-event", "multipart/*", "multipart/mixed",
        "multipart/form-data", "multipart/byterantes", "multipart/alternative", "application/*", "application/java-vm",
        "application/x-www-form-urlencoded", "application/x-hdmlc", "application/vnd.wap.wmlc", "application/vnd.wap.wmlscriptc",
        "application/vnd.wap.wta-eventc", "application/vnd.wap.uaprof", "application/vnd.wap.wtls-ca-certificate",
        "application/vnd.wap.wtls-user-certificate", "application/x-x509-ca-cert", "application/x-x509-user-cert",
        "image/*", "image/gif", "image/jpeg", "image/tiff", "image/png", "image/vnd.wap.wbmp",
        "application/vnd.wap.multipart.*", "application/vnd.wap.multipart.mixed", "application/vnd.wap.multipart.form-data",
        "application/vnd.wap.multipart.byteranges", "application/vnd.wap.multipart.alternative", "application/xml", "text/xml",
        "application/vnd.wap.wbxml", "application/x-x968-cross-cert", "application/x-x968-ca-cert", "application/x-x968-user-cert",
        "text/vnd.wap.si", "application/vnd.wap.sic", "text/vnd.wap.sl", "application/vnd.wap.slc", "text/vnd.wap.co",
        "application/vnd.wap.coc", "application/vnd.wap.multipart.related",
    )

    private class Leser(val b: ByteArray) {
        var i = 0
        fun har() = i < b.size
        fun titt() = b[i].toInt() and 0xFF
        fun byte() = b[i++].toInt() and 0xFF

        fun uintvar(): Long {
            var v = 0L
            while (true) {
                val c = byte()
                v = (v shl 7) or (c and 0x7F).toLong()
                if (c and 0x80 == 0) return v
            }
        }

        fun tekst(): String {
            if (har() && (titt() == 0x7F || titt() == 0x22)) i++
            val start = i
            while (i < b.size && b[i] != 0.toByte()) i++
            val s = String(b, start, i - start, Charsets.UTF_8)
            if (i < b.size) i++
            return s
        }

        fun lengde(): Int {
            val c = byte()
            return if (c <= 30) c else uintvar().toInt()
        }

        fun langtTall(): Long {
            val n = byte()
            var v = 0L
            repeat(n) { v = (v shl 8) or byte().toLong() }
            return v
        }

        fun heltall(): Long = if (titt() >= 0x80) (byte() and 0x7F).toLong() else langtTall()

        fun hopp() {
            val c = titt()
            when {
                c <= 30 -> i += 1 + c
                c == 31 -> {
                    i++
                    val n = uintvar().toInt()
                    i += n
                }
                c < 0x80 -> tekst()
                else -> i++
            }
        }

        fun kodetTekst(): String {
            if (titt() > 31) return tekst()
            val n = lengde()
            val slutt = i + n
            val tegnsett = heltall().toInt()
            val start = i
            var stopp = start
            while (stopp < slutt && b[stopp] != 0.toByte()) stopp++
            var fra = start
            if (fra < stopp && (b[fra].toInt() and 0xFF) == 0x7F) fra++
            val s = String(b, fra, stopp - fra, if (tegnsett == 1000 || tegnsett == 1015) Charsets.UTF_16 else Charsets.UTF_8)
            i = slutt
            return s
        }

        fun innholdstype(): Triple<String, Int, Map<String, String>> {
            val c = titt()
            if (c >= 0x80) {
                i++
                return Triple(kjente.getOrElse(c and 0x7F) { "application/octet-stream" }, 0, emptyMap())
            }
            if (c >= 32) return Triple(tekst(), 0, emptyMap())
            val n = lengde()
            val slutt = i + n
            val media = if (titt() >= 0x80) kjente.getOrElse(byte() and 0x7F) { "application/octet-stream" } else tekst()
            var tegnsett = 0
            val param = mutableMapOf<String, String>()
            while (i < slutt) {
                val p = titt()
                if (p >= 0x80) {
                    i++
                    when (p and 0x7F) {
                        0x01 -> tegnsett = heltall().toInt()
                        0x05, 0x17 -> param["name"] = tekst()
                        0x06, 0x18 -> param["filename"] = tekst()
                        0x09 -> if (titt() >= 0x80) i++ else param["type"] = tekst()
                        0x0A, 0x19 -> param["start"] = tekst()
                        else -> hopp()
                    }
                } else {
                    val navn = tekst()
                    param[navn] = if (har() && titt() >= 32 && titt() < 0x80) tekst() else { hopp(); "" }
                }
            }
            i = slutt
            return Triple(media, tegnsett, param)
        }

        fun adresse(): String = kodetTekst().substringBefore("/TYPE")
    }

    fun les(data: ByteArray): Pakke {
        val r = Leser(data)
        val p = Pakke()
        while (r.har()) {
            val felt = r.byte()
            if (felt < 0x80) break
            when (felt and 0x7F) {
                0x0C -> p.type = r.byte()
                0x18 -> p.transaksjon = r.tekst()
                0x03 -> p.plassering = r.tekst()
                0x0B -> p.meldingId = r.tekst()
                0x05 -> p.dato = r.langtTall()
                0x16 -> p.emne = r.kodetTekst()
                0x17 -> p.til += r.adresse()
                0x02 -> p.kopi += r.adresse()
                0x09 -> {
                    val n = r.lengde()
                    val slutt = r.i + n
                    if (r.byte() == 0x80) p.fra = r.adresse()
                    r.i = slutt
                }
                0x04 -> {
                    p.innholdstype = r.innholdstype().first
                    lesDeler(r, p)
                    return p
                }
                else -> r.hopp()
            }
        }
        return p
    }

    private fun lesDeler(r: Leser, p: Pakke) {
        if (!r.har()) return
        val antall = r.uintvar().toInt()
        repeat(antall) {
            val hodeLengde = r.uintvar().toInt()
            val dataLengde = r.uintvar().toInt()
            val start = r.i
            val (type, tegnsett, param) = r.innholdstype()
            var id: String? = null
            var plassering: String? = null
            while (r.i < start + hodeLengde) {
                val f = r.titt()
                if (f >= 0x80) {
                    r.i++
                    when (f and 0x7F) {
                        0x40 -> id = r.tekst().trim('<', '>', '"')
                        0x0E -> plassering = r.tekst()
                        else -> r.hopp()
                    }
                } else {
                    r.tekst()
                    r.tekst()
                }
            }
            r.i = start + hodeLengde
            val data = r.b.copyOfRange(r.i, minOf(r.i + dataLengde, r.b.size))
            r.i += dataLengde
            p.deler += Del(type, tegnsett, param["name"] ?: param["filename"], id, plassering, data)
        }
    }

    private class Skriver {
        val ut = ByteArrayOutputStream()
        fun b(v: Int) = ut.write(v)
        fun alle(v: ByteArray) = ut.write(v)
        fun tekst(s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            if (bytes.isNotEmpty() && (bytes[0].toInt() and 0xFF) >= 0x80) b(0x7F)
            alle(bytes)
            b(0)
        }
        fun uintvar(v: Long) {
            val deler = mutableListOf<Int>()
            var x = v
            do {
                deler.add(0, (x and 0x7F).toInt())
                x = x shr 7
            } while (x > 0)
            deler.forEachIndexed { i, d -> b(if (i < deler.size - 1) d or 0x80 else d) }
        }
        fun lengde(n: Int) {
            if (n <= 30) b(n) else {
                b(31)
                uintvar(n.toLong())
            }
        }
        fun bytes() = ut.toByteArray()
    }

    private fun adresse(nummer: String) = if (nummer.contains("@")) nummer else nummer.filter { it.isDigit() || it == '+' } + "/TYPE=PLMN"

    fun send(til: List<String>, tekst: String?, bilde: ByteArray?, transaksjon: String): ByteArray {
        val s = Skriver()
        s.b(0x8C); s.b(SEND_REQ)
        s.b(0x98); s.tekst(transaksjon)
        s.b(0x8D); s.b(0x92)
        s.b(0x89); s.b(0x01); s.b(0x81)
        til.forEach { s.b(0x97); s.tekst(adresse(it)) }
        s.b(0x8A); s.b(0x80)
        s.b(0x8F); s.b(0x81)
        s.b(0x86); s.b(0x81)
        s.b(0x90); s.b(0x81)

        val type = Skriver()
        type.b(0xB3)
        type.b(0x89); type.tekst("application/smil")
        type.b(0x8A); type.tekst("<smil>")
        val typeBytes = type.bytes()
        s.b(0x84); s.lengde(typeBytes.size); s.alle(typeBytes)

        val smil = buildString {
            append("<smil><head><layout><root-layout/>")
            if (bilde != null) append("<region id=\"Image\" top=\"0\" left=\"0\" height=\"80%\" width=\"100%\" fit=\"meet\"/>")
            if (tekst != null) append("<region id=\"Text\" top=\"80%\" left=\"0\" height=\"20%\" width=\"100%\" fit=\"scroll\"/>")
            append("</layout></head><body><par dur=\"5000ms\">")
            if (bilde != null) append("<img src=\"image.jpg\" region=\"Image\"/>")
            if (tekst != null) append("<text src=\"text.txt\" region=\"Text\"/>")
            append("</par></body></smil>")
        }

        val deler = mutableListOf<Pair<ByteArray, ByteArray>>()
        deler += hode({ it.tekst("application/smil") }, "smil.xml", "smil") to smil.toByteArray()
        if (bilde != null) deler += hode({ it.b(0x9E) }, "image.jpg", "image") to bilde
        if (tekst != null) deler += hode({ it.b(0x03); it.b(0x83); it.b(0x81); it.b(0xEA) }, "text.txt", "text") to tekst.toByteArray(Charsets.UTF_8)

        s.uintvar(deler.size.toLong())
        deler.forEach { (h, d) ->
            s.uintvar(h.size.toLong())
            s.uintvar(d.size.toLong())
            s.alle(h)
            s.alle(d)
        }
        return s.bytes()
    }

    private fun hode(type: (Skriver) -> Unit, plassering: String, id: String): ByteArray {
        val h = Skriver()
        type(h)
        h.b(0x8E); h.tekst(plassering)
        h.b(0xC0); h.b(0x22); h.alle("<$id>".toByteArray()); h.b(0)
        return h.bytes()
    }

    fun kvittering(transaksjon: String): ByteArray {
        val s = Skriver()
        s.b(0x8C); s.b(0x85)
        s.b(0x98); s.tekst(transaksjon)
        s.b(0x8D); s.b(0x92)
        return s.bytes()
    }

}
