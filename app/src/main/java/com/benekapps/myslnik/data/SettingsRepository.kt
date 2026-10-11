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
import com.benekapps.myslnik.reminders.ReminderType
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
    // Reminders (1.1); times are minutes from midnight.
    val reminderExercise: Boolean = false,
    val reminderExerciseTime: Int = 20 * 60,
    val reminderSleep: Boolean = false,
    val reminderSleepTime: Int = 8 * 60,
    val reminderWorry: Boolean = false,
    /** Daily worry time (1.2), also used by its reminder. */
    val worryTime: Int = 18 * 60,
    /** Copy of the in-app language choice for background work; null = system. */
    val language: String? = null,
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
            reminderExercise = p[REMINDER_EXERCISE] ?: false,
            reminderExerciseTime = p[REMINDER_EXERCISE_TIME] ?: (20 * 60),
            reminderSleep = p[REMINDER_SLEEP] ?: false,
            reminderSleepTime = p[REMINDER_SLEEP_TIME] ?: (8 * 60),
            reminderWorry = p[REMINDER_WORRY] ?: false,
            worryTime = p[WORRY_TIME] ?: (18 * 60),
            language = p[LANGUAGE],
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

    suspend fun setReminder(type: ReminderType, enabled: Boolean) = store.edit {
        when (type) {
            ReminderType.EXERCISE -> it[REMINDER_EXERCISE] = enabled
            ReminderType.SLEEP_DIARY -> it[REMINDER_SLEEP] = enabled
            ReminderType.WORRY_TIME -> it[REMINDER_WORRY] = enabled
        }
    }

    suspend fun setReminderTime(type: ReminderType, minuteOfDay: Int) = store.edit {
        when (type) {
            ReminderType.EXERCISE -> it[REMINDER_EXERCISE_TIME] = minuteOfDay
            ReminderType.SLEEP_DIARY -> it[REMINDER_SLEEP_TIME] = minuteOfDay
            ReminderType.WORRY_TIME -> it[WORRY_TIME] = minuteOfDay
        }
    }

    suspend fun setLanguage(tag: String?) = store.edit {
        if (tag == null) it.remove(LANGUAGE) else it[LANGUAGE] = tag
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
        val REMINDER_EXERCISE = booleanPreferencesKey("reminder_exercise")
        val REMINDER_EXERCISE_TIME = intPreferencesKey("reminder_exercise_time")
        val REMINDER_SLEEP = booleanPreferencesKey("reminder_sleep")
        val REMINDER_SLEEP_TIME = intPreferencesKey("reminder_sleep_time")
        val REMINDER_WORRY = booleanPreferencesKey("reminder_worry")
        val WORRY_TIME = intPreferencesKey("worry_time")
        val LANGUAGE = stringPreferencesKey("language")
    }
}
