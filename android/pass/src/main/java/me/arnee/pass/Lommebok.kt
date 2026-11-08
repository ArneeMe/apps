package me.arnee.pass

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class Lagret(val id: String, val dokument: Dokument)

object Lommebok {
    private const val ALIAS = "pass"

    private fun nokkel(): SecretKey {
        val lager = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (lager.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun mappe(context: Context) = File(context.filesDir, "pass").apply { mkdirs() }

    fun lagre(context: Context, d: Dokument): String {
        val rå = ByteArrayOutputStream().also { b ->
            DataOutputStream(b).apply {
                writeBoolean(d.test)
                writeInt(d.dg1.size); write(d.dg1)
                writeInt(d.sod.size); write(d.sod)
            }
        }.toByteArray()
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, nokkel()) }
        val id = "${System.currentTimeMillis()}"
        File(mappe(context), "$id.bin").writeBytes(c.iv + c.doFinal(rå))
        return id
    }

    fun hent(context: Context, id: String): Lagret? = runCatching {
        val data = File(mappe(context), "$id.bin").readBytes()
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, nokkel(), GCMParameterSpec(128, data.copyOf(12))) }
        val inn = DataInputStream(c.doFinal(data, 12, data.size - 12).inputStream())
        val test = inn.readBoolean()
        val dg1 = ByteArray(inn.readInt()).also { inn.readFully(it) }
        val sod = ByteArray(inn.readInt()).also { inn.readFully(it) }
        Lagret(id, Dokument(dg1, sod, test))
    }.getOrNull()

    fun alle(context: Context): List<Lagret> =
        mappe(context).listFiles { f -> f.name.endsWith(".bin") }?.sortedBy { it.name }?.mapNotNull { hent(context, it.nameWithoutExtension) } ?: emptyList()

    fun slett(context: Context, id: String) = File(mappe(context), "$id.bin").delete()
}
