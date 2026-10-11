package com.benekapps.myslnik

import android.content.Context
import com.benekapps.myslnik.data.EntriesRepository
import com.benekapps.myslnik.data.RoomEntriesRepository
import com.benekapps.myslnik.data.SettingsRepository
import com.benekapps.myslnik.data.backup.BackupManager
import com.benekapps.myslnik.data.db.AppDatabase
import com.benekapps.myslnik.data.security.DatabaseKeyManager
import com.benekapps.myslnik.export.PdfExporter
import com.benekapps.myslnik.lock.AppLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Manual dependency injection: one instance per process. */
class AppContainer(private val context: Context) {
    /** For work that must finish even when a screen closes (saving drafts). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(context)
    val lockManager = AppLockManager()
    private val keyManager = DatabaseKeyManager(context)

    @Volatile
    private var database: AppDatabase? = null

    /** Opens the encrypted database. Call off the main thread. Throws if it cannot be read. */
    @Synchronized
    fun openDatabase(): AppDatabase {
        database?.let { return it }
        val db = AppDatabase.openEncrypted(context, keyManager.getOrCreateDatabaseKey())
        db.openHelper.writableDatabase // fail now (wrong key, damaged file), not on first query
        database = db
        return db
    }

    /** Last resort when the database cannot be opened: start with an empty one. */
    @Synchronized
    fun resetDatabase() {
        runCatching { database?.close() }
        database = null
        keyManager.destroy()
    }

    val entries: EntriesRepository by lazy { RoomEntriesRepository(openDatabase()) }

    val backupManager: BackupManager by lazy { BackupManager(context.contentResolver, entries, settings) }

    val pdfExporter: PdfExporter by lazy { PdfExporter(context) }

    /** Generated PDFs, shared through FileProvider (res/xml/file_paths.xml). */
    val exportDir = File(context.cacheDir, "exports")
}
