package me.arnee.sms

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import org.json.JSONObject

object Doh {
    private val tjenere = listOf("https://1.1.1.1/dns-query", "https://1.0.0.1/dns-query")

    fun hent(url: String): ByteArray = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        if (c.responseCode != 200) throw IllegalStateException("Svar ${c.responseCode} fra ${URL(url).host}")
        c.inputStream.use { it.readBytes() }
    } catch (e: UnknownHostException) {
        hentDirekte(URL(url))
    }

    fun slaOpp(vert: String): List<String> {
        for (tjener in tjenere) {
            val svar = runCatching {
                val c = URL("$tjener?name=$vert&type=A").openConnection() as HttpURLConnection
                c.setRequestProperty("Accept", "application/dns-json")
                c.connectTimeout = 10_000
                c.readTimeout = 10_000
                JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            }.getOrNull() ?: continue
            val svarliste = svar.optJSONArray("Answer") ?: continue
            val ip = (0 until svarliste.length()).map { svarliste.getJSONObject(it) }.filter { it.optInt("type") == 1 }.map { it.getString("data") }
            if (ip.isNotEmpty()) return ip
        }
        throw UnknownHostException("Fant ikke $vert, heller ikke med DNS over HTTPS")
    }

    private fun hentDirekte(url: URL): ByteArray {
        val vert = url.host
        val ip = slaOpp(vert).first()
        val adresse = InetAddress.getByAddress(vert, InetAddress.getByName(ip).address)
        val ra = Socket().apply {
            connect(InetSocketAddress(adresse, 443), 15_000)
            soTimeout = 30_000
        }
        val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(ra, vert, 443, true) as SSLSocket
        tls.use { s ->
            s.startHandshake()
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(vert, s.session)) throw SecurityException("Feil sertifikat for $vert")
            val sti = url.file.ifEmpty { "/" }
            s.outputStream.write("GET $sti HTTP/1.1\r\nHost: $vert\r\nUser-Agent: meldinger\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n".toByteArray())
            s.outputStream.flush()
            val inn = BufferedInputStream(s.inputStream)
            val status = linje(inn)
            val kode = status.split(" ").getOrNull(1)?.toIntOrNull() ?: 0
            val hoder = mutableMapOf<String, String>()
            while (true) {
                val l = linje(inn)
                if (l.isEmpty()) break
                hoder[l.substringBefore(":").trim().lowercase()] = l.substringAfter(":").trim()
            }
            if (kode != 200) throw IllegalStateException("Svar $kode fra $vert")
            return when {
                hoder["transfer-encoding"]?.contains("chunked") == true -> biter(inn)
                hoder["content-length"] != null -> {
                    val n = hoder.getValue("content-length").toInt()
                    val data = ByteArray(n)
                    var lest = 0
                    while (lest < n) {
                        val r = inn.read(data, lest, n - lest)
                        if (r < 0) break
                        lest += r
                    }
                    data
                }
                else -> inn.readBytes()
            }
        }
    }

    private fun linje(inn: InputStream): String {
        val ut = ByteArrayOutputStream()
        while (true) {
            val c = inn.read()
            if (c < 0 || c == '\n'.code) break
            if (c != '\r'.code) ut.write(c)
        }
        return ut.toString("ISO-8859-1")
    }

    private fun biter(inn: InputStream): ByteArray {
        val ut = ByteArrayOutputStream()
        while (true) {
            val n = linje(inn).substringBefore(";").trim().toInt(16)
            if (n == 0) break
            val bit = ByteArray(n)
            var lest = 0
            while (lest < n) {
                val r = inn.read(bit, lest, n - lest)
                if (r < 0) throw IllegalStateException("Avbrutt svar")
                lest += r
            }
            ut.write(bit)
            linje(inn)
        }
        return ut.toByteArray()
    }
}
