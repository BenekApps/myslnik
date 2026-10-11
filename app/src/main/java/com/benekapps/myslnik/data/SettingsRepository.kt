package com.benekapps.myslnik.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val onboardingDone: Boolean = false,
    val pinHash: String? = null,
    val biometricEnabled: Boolean = false,
    /** FLAG_SECURE: hides content in recent apps and blocks screenshots. */
    val secureScreen: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lastBackupAt: Long? = null,
    val backupReminderSnoozedUntil: Long = 0,
    val failedPinAttempts: Int = 0,
    val pinLockedUntil: Long = 0,
) {
    val lockEnabled: Boolean get() = pinHash != null
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            onboardingDone = p[ONBOARDING_DONE] ?: false,
            pinHash = p[PIN_HASH],
            biometricEnabled = p[BIOMETRIC] ?: false,
            secureScreen = p[SECURE_SCREEN] ?: true,
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            lastBackupAt = p[LAST_BACKUP],
            backupReminderSnoozedUntil = p[BACKUP_SNOOZE] ?: 0,
            failedPinAttempts = p[FAILED_ATTEMPTS] ?: 0,
            pinLockedUntil = p[PIN_LOCKED_UNTIL] ?: 0,
        )
    }

    suspend fun setOnboardingDone() = store.edit { it[ONBOARDING_DONE] = true }

    /** Sets a new PIN hash, or turns the lock off with null (and fingerprint with it). */
    suspend fun setPinHash(hash: String?) = store.edit {
        if (hash == null) {
            it.remove(PIN_HASH)
            it[BIOMETRIC] = false
        } else {
            it[PIN_HASH] = hash
        }
        it.remove(FAILED_ATTEMPTS)
        it.remove(PIN_LOCKED_UNTIL)
    }

    suspend fun setBiometricEnabled(enabled: Boolean) = store.edit { it[BIOMETRIC] = enabled }
    suspend fun setSecureScreen(enabled: Boolean) = store.edit { it[SECURE_SCREEN] = enabled }
    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[THEME] = mode.name }
    suspend fun setLastBackupAt(time: Long) = store.edit { it[LAST_BACKUP] = time }
    suspend fun snoozeBackupReminder(until: Long) = store.edit { it[BACKUP_SNOOZE] = until }

    suspend fun recordFailedPin(attempts: Int, lockedUntil: Long) = store.edit {
        it[FAILED_ATTEMPTS] = attempts
        it[PIN_LOCKED_UNTIL] = lockedUntil
    }

    suspend fun clearFailedPins() = store.edit {
        it.remove(FAILED_ATTEMPTS)
        it.remove(PIN_LOCKED_UNTIL)
    }

    private companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val SECURE_SCREEN = booleanPreferencesKey("secure_screen")
        val THEME = stringPreferencesKey("theme_mode")
        val LAST_BACKUP = longPreferencesKey("last_backup_at")
        val BACKUP_SNOOZE = longPreferencesKey("backup_reminder_snoozed_until")
        val FAILED_ATTEMPTS = intPreferencesKey("failed_pin_attempts")
        val PIN_LOCKED_UNTIL = longPreferencesKey("pin_locked_until")
    }
}
