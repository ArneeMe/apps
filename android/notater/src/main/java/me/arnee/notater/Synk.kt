package me.arnee.notater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

object Synk {
    private fun lager(context: Context) = context.getSharedPreferences("synk", Context.MODE_PRIVATE)

    fun mappe(context: Context): Uri? = lager(context).getString("mappe", null)?.let { Uri.parse(it) }

    fun settMappe(context: Context, uri: Uri?) {
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        lager(context).edit().putString("mappe", uri?.toString()).remove("kjente").apply()
    }

    fun navn(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: "valgt mappe"

    private class Fjern(val uri: Uri, val endret: Long)

    private fun fjerne(context: Context, tre: Uri): Map<String, Fjern> {
        val barn = DocumentsContract.buildChildDocumentsUriUsingTree(tre, DocumentsContract.getTreeDocumentId(tre))
        val kart = mutableMapOf<String, Fjern>()
        context.contentResolver.query(
            barn,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val navn = c.getString(1) ?: continue
                if (!navn.endsWith(".txt")) continue
                kart[navn] = Fjern(DocumentsContract.buildDocumentUriUsingTree(tre, c.getString(0)), c.getLong(2))
            }
        }
        return kart
    }

    private fun lokale(context: Context): Map<String, File> {
        val kart = Lager.notater(context).associateBy { it.name }.toMutableMap()
        val oppgaver = File(context.filesDir, "oppgaver.txt")
        if (oppgaver.exists()) kart["oppgaver.txt"] = oppgaver
        return kart
    }

    private fun lokalFil(context: Context, navn: String) =
        if (navn == "oppgaver.txt") File(context.filesDir, navn) else Lager.notat(context, navn)

    @Synchronized
    fun kjor(context: Context): String? {
        val tre = mappe(context) ?: return null
        return runCatching {
            val kjente = lager(context).getStringSet("kjente", emptySet())!!.toMutableSet()
            val fjern = fjerne(context, tre)
            val lokal = lokale(context)
            val alle = fjern.keys + lokal.keys + kjente
            val treId = DocumentsContract.buildDocumentUriUsingTree(tre, DocumentsContract.getTreeDocumentId(tre))
            for (navn in alle) {
                val f = fjern[navn]
                val l = lokal[navn]
                when {
                    f == null && l == null -> kjente.remove(navn)
                    f == null && navn in kjente -> {
                        l!!.delete()
                        kjente.remove(navn)
                    }
                    l == null && navn in kjente -> {
                        DocumentsContract.deleteDocument(context.contentResolver, f!!.uri)
                        kjente.remove(navn)
                    }
                    f == null -> {
                        val ny = DocumentsContract.createDocument(context.contentResolver, treId, "text/plain", navn)!!
                        context.contentResolver.openOutputStream(ny, "wt")!!.use { it.write(l!!.readBytes()) }
                        kjente.add(navn)
                    }
                    l == null -> {
                        val fil = lokalFil(context, navn)
                        context.contentResolver.openInputStream(f.uri)!!.use { inn -> fil.outputStream().use { inn.copyTo(it) } }
                        fil.setLastModified(f.endret)
                        kjente.add(navn)
                    }
                    f.endret > l.lastModified() + 2000 -> {
                        context.contentResolver.openInputStream(f.uri)!!.use { inn -> l.outputStream().use { inn.copyTo(it) } }
                        l.setLastModified(f.endret)
                        kjente.add(navn)
                    }
                    l.lastModified() > f.endret + 2000 -> {
                        context.contentResolver.openOutputStream(f.uri, "wt")!!.use { it.write(l.readBytes()) }
                        kjente.add(navn)
                    }
                    else -> kjente.add(navn)
                }
            }
            lager(context).edit().putStringSet("kjente", kjente).apply()
            null
        }.exceptionOrNull()?.let { "Synk feilet: ${it.message}" }
    }
}
