package com.benekapps.myslnik.data.backup

import android.content.ContentResolver
import android.net.Uri
import com.benekapps.myslnik.data.EntriesRepository
import com.benekapps.myslnik.data.SettingsRepository
import com.benekapps.myslnik.data.security.BackupCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes and reads password-protected backup files through the Storage
 * Access Framework, so the user picks the place (phone, Google Drive...).
 * The app itself never sends anything anywhere.
 */
class BackupManager(
    private val contentResolver: ContentResolver,
    private val entries: EntriesRepository,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun createBackup(target: Uri, password: CharArray) = withContext(Dispatchers.Default) {
        val time = now()
        val json = BackupSerializer.toJson(entries.exportAll(time))
        val encrypted = BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), password)
        withContext(Dispatchers.IO) {
            val out = contentResolver.openOutputStream(target, "wt")
                ?: throw java.io.IOException("Cannot open $target")
            out.use { it.write(encrypted) }
        }
        settings.setLastBackupAt(time)
    }

    /** Decrypts and parses a backup without changing anything yet. */
    suspend fun readBackup(source: Uri, password: CharArray): BackupData = withContext(Dispatchers.Default) {
        val bytes = withContext(Dispatchers.IO) {
            val input = contentResolver.openInputStream(source)
                ?: throw java.io.IOException("Cannot open $source")
            input.use { it.readBytes() }
        }
        val json = BackupCrypto.decrypt(bytes, password).toString(Charsets.UTF_8)
        BackupSerializer.fromJson(json)
    }

    suspend fun restore(data: BackupData) = entries.replaceAll(data)
}
