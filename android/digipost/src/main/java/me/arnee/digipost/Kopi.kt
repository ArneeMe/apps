package me.arnee.digipost

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray

object Kopi {
    private const val JSON = "digipost-kunder.json"
    private const val CSV = "digipost-kunder.csv"

    private fun lager(k: Context) = k.getSharedPreferences("kopi", Context.MODE_PRIVATE)

    fun mappe(k: Context): Uri? = lager(k).getString("mappe", null)?.let { Uri.parse(it) }

    fun sist(k: Context) = lager(k).getLong("sist", 0)

    fun settMappe(k: Context, uri: Uri?) {
        mappe(k)?.let { gammel -> runCatching { k.contentResolver.releasePersistableUriPermission(gammel, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        if (uri != null) k.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        lager(k).edit().putString("mappe", uri?.toString()).apply()
    }

    fun navn(k: Context, uri: Uri): String = runCatching {
        k.contentResolver.query(DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: "valgt mappe"

    private fun finn(k: Context, tre: Uri, navn: String): Uri? {
        val barn = DocumentsContract.buildChildDocumentsUriUsingTree(tre, DocumentsContract.getTreeDocumentId(tre))
        k.contentResolver.query(barn, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) if (c.getString(1) == navn) return DocumentsContract.buildDocumentUriUsingTree(tre, c.getString(0))
        }
        return null
    }

    private fun skrivFil(k: Context, tre: Uri, navn: String, type: String, innhold: ByteArray) {
        val fil = finn(k, tre, navn)
            ?: DocumentsContract.createDocument(k.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tre, DocumentsContract.getTreeDocumentId(tre)), type, navn)
            ?: throw IllegalStateException("Fikk ikke laget $navn")
        k.contentResolver.openOutputStream(fil, "wt")!!.use { it.write(innhold) }
    }

    fun skriv(k: Context): String? {
        val tre = mappe(k) ?: return null
        return runCatching {
            val kunder = Kunder.alle(k)
            skrivFil(k, tre, JSON, "application/json", JSONArray(kunder.map { it.json() }).toString(2).toByteArray())
            skrivFil(k, tre, CSV, "text/csv", ("﻿" + Kunder.eksport(kunder)).toByteArray())
            lager(k).edit().putLong("sist", System.currentTimeMillis()).apply()
            null
        }.getOrElse { "Fikk ikke skrevet kopien: ${it.message}" }
    }

    fun les(k: Context): List<Kunde>? {
        val tre = mappe(k) ?: return null
        val fil = finn(k, tre, JSON) ?: return null
        val tekst = k.contentResolver.openInputStream(fil)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return null
        val liste = JSONArray(tekst)
        return (0 until liste.length()).map { Kunde.fra(liste.getJSONObject(it)) }
    }
}
