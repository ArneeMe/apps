package me.arnee.pass

import android.util.Base64
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import org.json.JSONObject

class Foresporsel(
    val domene: String,
    val nokkel: String,
    val sporring: JSONObject,
    val tjeneste: JSONObject,
    val modus: String,
    val versjon: String,
    val test: Boolean,
    val bro: String,
) {
    companion object {
        fun fra(lenke: String): Foresporsel {
            val sporsmal = lenke.substringAfter("?", "")
            val felt = sporsmal.split("&").filter { it.contains("=") }.associate { del ->
                val navn = del.substringBefore("=")
                val verdi = java.net.URLDecoder.decode(del.substringAfter("=").replace("+", "%2B"), "UTF-8")
                navn to verdi
            }
            val b64 = { s: String? -> s?.let { String(Base64.decode(it.replace(' ', '+'), Base64.DEFAULT)) } }
            val tjeneste = b64(felt["s"])?.let { JSONObject(it) } ?: JSONObject()
            return Foresporsel(
                felt["d"] ?: throw IllegalArgumentException("Lenken mangler domene"),
                felt["p"] ?: felt["t"] ?: throw IllegalArgumentException("Lenken mangler nøkkel"),
                JSONObject(b64(felt["c"]) ?: "{}"),
                tjeneste,
                felt["m"] ?: "fast",
                felt["v"] ?: "",
                felt["dev"] == "1",
                tjeneste.optString("bridgeUrl").ifEmpty { "wss://bridge.zkpassport.id" },
            )
        }
    }
}

object BroKrypto {
    fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    fun fraHex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    fun nokkel(min: BigInteger, deres: String): ByteArray {
        val k = Kurve.SECP256K1
        return k.komprimert(k.gange(min, k.punkt(fraHex(deres)))).copyOf(32)
    }

    fun nonce(broId: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(broId.toByteArray()).copyOf(12)

    fun krypter(nokkel: ByteArray, nonce: ByteArray, d: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(nokkel, "AES"), GCMParameterSpec(128, nonce)) }.doFinal(d)

    fun dekrypter(nokkel: ByteArray, nonce: ByteArray, d: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(nokkel, "AES"), GCMParameterSpec(128, nonce)) }.doFinal(d)

    fun pakk(d: ByteArray): ByteArray {
        val z = Deflater()
        z.setInput(d)
        z.finish()
        val ut = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!z.finished()) ut.write(buf, 0, z.deflate(buf))
        z.end()
        return ut.toByteArray()
    }

    fun pakkUt(d: ByteArray): ByteArray {
        val z = Inflater()
        z.setInput(d)
        val ut = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!z.finished()) {
            val n = z.inflate(buf)
            if (n == 0 && (z.needsInput() || z.needsDictionary())) break
            ut.write(buf, 0, n)
        }
        z.end()
        return ut.toByteArray()
    }

    fun id(): String = hex(ByteArray(16).also { SecureRandom().nextBytes(it) })
}

class WebSocket(adresse: String) {
    private val uri = URI(adresse)
    private val sokkel: Socket
    private val inn: InputStream
    private val ut: OutputStream

    init {
        val vert = uri.host
        val port = if (uri.port > 0) uri.port else 443
        val ra = Socket().apply { connect(InetSocketAddress(vert, port), 15_000) }
        val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(ra, vert, port, true) as SSLSocket
        tls.startHandshake()
        if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(vert, tls.session)) throw SecurityException("Feil sertifikat for $vert")
        sokkel = tls
        inn = BufferedInputStream(tls.inputStream)
        ut = tls.outputStream
        val nokkel = Base64.encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP)
        val sti = (uri.rawPath.ifEmpty { "/" }) + (uri.rawQuery?.let { "?$it" } ?: "")
        ut.write("GET $sti HTTP/1.1\r\nHost: $vert\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: $nokkel\r\nSec-WebSocket-Version: 13\r\n\r\n".toByteArray())
        ut.flush()
        val status = linje()
        if (!status.contains(" 101")) throw IllegalStateException("Broen svarte: $status")
        while (linje().isNotEmpty()) Unit
    }

    private fun linje(): String {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = inn.read()
            if (c < 0 || c == '\n'.code) break
            if (c != '\r'.code) b.write(c)
        }
        return b.toString("UTF-8")
    }

    @Synchronized
    fun send(tekst: String, opkode: Int = 1) {
        val d = tekst.toByteArray()
        val f = ByteArrayOutputStream()
        f.write(0x80 or opkode)
        when {
            d.size < 126 -> f.write(0x80 or d.size)
            d.size < 65536 -> { f.write(0x80 or 126); f.write(d.size shr 8); f.write(d.size) }
            else -> { f.write(0x80 or 127); for (i in 7 downTo 0) f.write((d.size.toLong() shr (8 * i)).toInt()) }
        }
        val maske = ByteArray(4).also { SecureRandom().nextBytes(it) }
        f.write(maske)
        f.write(ByteArray(d.size) { (d[it].toInt() xor maske[it % 4].toInt()).toByte() })
        ut.write(f.toByteArray())
        ut.flush()
    }

    private fun les(n: Long): ByteArray {
        val b = ByteArray(n.toInt())
        var lest = 0
        while (lest < b.size) {
            val r = inn.read(b, lest, b.size - lest)
            if (r < 0) throw IllegalStateException("Broen lukket forbindelsen")
            lest += r
        }
        return b
    }

    fun motta(): String? {
        val melding = ByteArrayOutputStream()
        while (true) {
            val b0 = inn.read()
            if (b0 < 0) return null
            val b1 = inn.read()
            val opkode = b0 and 0x0F
            var n = (b1 and 0x7F).toLong()
            if (n == 126L) n = ((inn.read() shl 8) or inn.read()).toLong()
            else if (n == 127L) { n = 0; repeat(8) { n = (n shl 8) or inn.read().toLong() } }
            val maske = if (b1 and 0x80 != 0) les(4) else null
            val data = les(n).also { d -> if (maske != null) for (i in d.indices) d[i] = (d[i].toInt() xor maske[i % 4].toInt()).toByte() }
            when (opkode) {
                8 -> return null
                9 -> { send(String(data), 10); continue }
                10 -> continue
            }
            melding.write(data)
            if (b0 and 0x80 != 0) return melding.toString("UTF-8")
        }
    }

    fun lukk() = runCatching { sokkel.close() }
}

class Bro(private val f: Foresporsel, private val hendelse: (String, JSONObject?) -> Unit) {
    private val min = Kurve.SECP256K1.hemmelig()
    private val nokkel = BroKrypto.nokkel(min, f.nokkel)
    private val nonce = BroKrypto.nonce(f.nokkel)
    private var ws: WebSocket? = null
    @Volatile var klar = false

    fun koble() {
        val k = Kurve.SECP256K1
        val hilsen = BroKrypto.hex(BroKrypto.krypter(nokkel, nonce, "hello".toByteArray()))
        val handtrykk = JSONObject().put("jsonrpc", "2.0").put("id", BroKrypto.id()).put("method", "handshake")
            .put("params", JSONObject().put("pubkey", BroKrypto.hex(k.komprimert(k.gange(min, k.g)))).put("greeting", hilsen))
        val moc = URLEncoder.encode(Base64.encodeToString(handtrykk.toString().toByteArray(), Base64.NO_WRAP), "UTF-8")
        val socket = WebSocket("${f.bro}?id=${f.nokkel}&v=1&moc=$moc")
        ws = socket
        Thread {
            runCatching {
                while (true) {
                    val tekst = socket.motta() ?: break
                    val ramme = runCatching { JSONObject(tekst) }.getOrNull() ?: continue
                    when (ramme.optString("method")) {
                        "ping" -> socket.send(JSONObject().put("method", "pong").put("params", JSONObject()).put("nocache", true).toString())
                        "encryptedMessage" -> {
                            val payload = Base64.decode(ramme.getJSONObject("params").getString("payload"), Base64.DEFAULT)
                            val indre = JSONObject(String(BroKrypto.dekrypter(nokkel, nonce, payload)))
                            val metode = indre.optString("method")
                            if (metode == "hello") klar = true
                            hendelse(metode, indre.optJSONObject("params"))
                        }
                    }
                }
            }.onFailure { hendelse("brudd", JSONObject().put("melding", it.message ?: it.javaClass.simpleName)) }
            hendelse("lukket", null)
        }.start()
    }

    fun send(metode: String, params: JSONObject = JSONObject()) {
        val socket = ws ?: throw IllegalStateException("Ikke koblet til")
        val komprimert = Base64.encodeToString(BroKrypto.pakk(params.toString().toByteArray()), Base64.NO_WRAP)
        val biter = komprimert.chunked(16384)
        val bitId = BroKrypto.id()
        biter.forEachIndexed { i, bit ->
            val indre = JSONObject().put("method", metode).put("params", bit)
                .put("chunk", JSONObject().put("id", bitId).put("index", i).put("length", biter.size))
            val payload = Base64.encodeToString(BroKrypto.krypter(nokkel, nonce, indre.toString().toByteArray()), Base64.NO_WRAP)
            socket.send(JSONObject().put("jsonrpc", "2.0").put("id", BroKrypto.id()).put("method", "encryptedMessage").put("params", JSONObject().put("payload", payload)).toString())
            if (biter.size > 1) Thread.sleep(2)
        }
    }

    fun lukk() = ws?.lukk()
}
