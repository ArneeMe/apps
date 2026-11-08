package me.arnee.sms

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

class Filer : ContentProvider() {
    companion object {
        fun mappe(context: Context) = File(context.cacheDir, "mms").apply { mkdirs() }

        fun uri(context: Context, navn: String): Uri = Uri.parse("content://${context.packageName}.filer/$navn")

        fun fil(context: Context, navn: String) = File(mappe(context), navn)
    }

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val navn = uri.lastPathSegment ?: throw IllegalArgumentException("Mangler filnavn")
        if (navn.contains("/") || navn.contains("..")) throw IllegalArgumentException("Ugyldig filnavn")
        val fil = fil(context!!, navn)
        val modus = if (mode.contains("w")) {
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        } else {
            ParcelFileDescriptor.MODE_READ_ONLY
        }
        return ParcelFileDescriptor.open(fil, modus)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String = when {
        uri.lastPathSegment?.endsWith(".jpg") == true -> "image/jpeg"
        uri.lastPathSegment?.endsWith(".png") == true -> "image/png"
        uri.lastPathSegment?.endsWith(".gif") == true -> "image/gif"
        else -> "application/vnd.wap.mms-message"
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
