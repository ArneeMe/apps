package me.arnee.pass

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

class BevisMotor(context: Context, private val lytter: Lytter) {
    interface Lytter {
        fun klar(isolert: Boolean)
        fun framdrift(tekst: String)
        fun ferdig(svar: JSONObject)
        fun feil(melding: String)
    }

    companion object {
        private const val VERT = "appassets.androidplatform.net"
        private val mellomlagre = setOf("circuits2.zkpassport.id", "crs.aztec.network", "certificates.zkpassport.id")
        private val typer = mapOf("html" to "text/html", "js" to "text/javascript", "mjs" to "text/javascript", "wasm" to "application/wasm", "json" to "application/json")
    }

    private val hoved = Handler(Looper.getMainLooper())
    private val app = context.applicationContext
    private val lager = File(app.cacheDir, "zkp").apply { mkdirs() }
    @Volatile var erKlar = false
        private set

    @SuppressLint("SetJavaScriptEnabled")
    val visning: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        addJavascriptInterface(Bro(), "Pass")
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                val url = req.url
                return when {
                    url.host == VERT -> fil(url.path ?: "/")
                    req.method == "GET" && url.host in mellomlagre -> hent(url.toString(), req.requestHeaders)
                    else -> null
                }
            }
        }
        loadUrl("https://$VERT/zkp/index.html")
    }

    private fun hoder(ekstra: Map<String, String> = emptyMap()) = mutableMapOf(
        "Cross-Origin-Opener-Policy" to "same-origin",
        "Cross-Origin-Embedder-Policy" to "require-corp",
        "Cross-Origin-Resource-Policy" to "cross-origin",
        "Access-Control-Allow-Origin" to "*",
    ).apply { putAll(ekstra) }

    private fun fil(sti: String): WebResourceResponse {
        val navn = sti.removePrefix("/")
        val type = typer[navn.substringAfterLast('.', "")] ?: "application/octet-stream"
        return runCatching {
            WebResourceResponse(type, null, 200, "OK", hoder(), app.assets.open(navn))
        }.getOrElse { WebResourceResponse("text/plain", null, 404, "Not Found", hoder(), ByteArrayInputStream(ByteArray(0))) }
    }

    private fun hent(url: String, inn: Map<String, String>): WebResourceResponse? = runCatching {
        val omrade = inn.entries.firstOrNull { it.key.equals("Range", true) }?.value
        val nokkel = MessageDigest.getInstance("SHA-256").digest((url + (omrade ?: "")).toByteArray()).joinToString("") { "%02x".format(it) }
        val fil = File(lager, nokkel)
        val varig = url.contains("/by-hash/") || url.contains("crs.aztec.network") || url.contains("certificates.zkpassport.id")
        if (varig && fil.exists()) {
            val status = if (omrade != null) 206 else 200
            return WebResourceResponse("application/octet-stream", null, status, if (status == 206) "Partial Content" else "OK", hoder(), fil.inputStream())
        }
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        omrade?.let { c.setRequestProperty("Range", it) }
        val kode = c.responseCode
        val data = (if (kode >= 400) c.errorStream else c.inputStream)?.use { it.readBytes() } ?: ByteArray(0)
        if (varig && (kode == 200 || kode == 206)) {
            val tmp = File(lager, "$nokkel.tmp")
            tmp.writeBytes(data)
            tmp.renameTo(fil)
        }
        val type = c.contentType?.substringBefore(";") ?: "application/octet-stream"
        WebResourceResponse(type, null, kode, c.responseMessage ?: "OK", hoder(), ByteArrayInputStream(data))
    }.getOrNull()

    private fun js(s: String) = JSONObject.quote(s)

    fun lag(dg1: ByteArray, sod: ByteArray, sporring: JSONObject, domene: String, test: Boolean) {
        val kall = "window.lagBevisFraApp(${js(dg1.hex())},${js(sod.hex())},${js(sporring.toString())},${js(domene)},$test).catch(()=>{})"
        hoved.post { visning.evaluateJavascript(kall, null) }
    }

    fun lukk() = hoved.post { visning.destroy() }

    private inner class Bro {
        @JavascriptInterface fun klar(isolert: String) {
            erKlar = true
            hoved.post { lytter.klar(isolert == "true") }
        }

        @JavascriptInterface fun framdrift(tekst: String) = hoved.post { lytter.framdrift(tekst) }.let { }

        @JavascriptInterface fun ferdig(json: String) = hoved.post { lytter.ferdig(JSONObject(json)) }.let { }

        @JavascriptInterface fun feil(melding: String) = hoved.post { lytter.feil(melding) }.let { }
    }
}
