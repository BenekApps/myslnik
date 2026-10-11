package com.benekapps.myslnik.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * The only database. Every schema change needs a migration and a test in
 * androidTest/.../MigrationTest: losing entries is the worst possible bug.
 */
@Database(entities = [ThoughtRecord::class, MoodEntry::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun thoughtRecords(): ThoughtRecordDao
    abstract fun moodEntries(): MoodEntryDao

    companion object {
        const val NAME = "myslnik.db"

        /**
         * Opens the database encrypted with SQLCipher. [rawKey] is a random
         * 32-byte key; it is passed in SQLCipher's raw key format so no slow
         * key derivation runs on every start.
         */
        fun openEncrypted(context: Context, rawKey: ByteArray, name: String = NAME): AppDatabase {
            System.loadLibrary("sqlcipher")
            val hex = rawKey.joinToString("") { "%02x".format(it) }
            val factory = SupportOpenHelperFactory("x'$hex'".toByteArray(Charsets.US_ASCII))
            return Room.databaseBuilder(context, AppDatabase::class.java, name)
                .openHelperFactory(factory)
                .build()
        }

        fun databaseFiles(context: Context): List<java.io.File> {
            val db = context.getDatabasePath(NAME)
            return listOf(db, java.io.File("${db.path}-wal"), java.io.File("${db.path}-shm"), java.io.File("${db.path}-journal"))
        }
    }
}
