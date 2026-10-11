package com.benekapps.myslnik.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the database key on the phone only.
 *
 * A random 32-byte database key is wrapped (AES-GCM) with a non-exportable
 * key in the Android Keystore and stored in noBackupFilesDir. The Keystore
 * key never leaves the device, so a copied database file is unreadable.
 */
class DatabaseKeyManager(private val context: Context) {

    private val keyFile = AtomicFile(File(context.noBackupFilesDir, KEY_FILE))

    /** Returns the database key, creating it (and a fresh database) on first use. */
    fun getOrCreateDatabaseKey(): ByteArray {
        val wrapped = runCatching { keyFile.readFully() }.getOrNull()
        if (wrapped != null) return unwrap(wrapped)

        // A database without its key can never be opened again (for example
        // after a device-to-device transfer). Start fresh instead of crashing.
        deleteDatabaseFiles()

        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val out = keyFile.startWrite()
        try {
            out.write(wrap(key))
            keyFile.finishWrite(out)
        } catch (e: Exception) {
            keyFile.failWrite(out)
            throw e
        }
        return key
    }

    /** Removes the database, its key and the Keystore entry. */
    fun destroy() {
        deleteDatabaseFiles()
        keyFile.delete()
        runCatching { keyStore().deleteEntry(KEYSTORE_ALIAS) }
    }

    private fun deleteDatabaseFiles() {
        com.benekapps.myslnik.data.db.AppDatabase.databaseFiles(context).forEach { it.delete() }
    }

    private fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    private fun unwrap(data: ByteArray): ByteArray {
        val ivSize = data[0].toInt()
        val iv = data.copyOfRange(1, 1 + ivSize)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(data, 1 + ivSize, data.size - 1 - ivSize)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun keystoreKey(): SecretKey {
        (keyStore().getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEYSTORE_ALIAS = "myslnik_db_key_wrapper"
        const val KEY_FILE = "db.key"
        const val KEY_BYTES = 32
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
