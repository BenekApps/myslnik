package com.benekapps.myslnik.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.benekapps.myslnik.MainActivity
import com.benekapps.myslnik.MyslnikApplication
import com.benekapps.myslnik.R
import com.benekapps.myslnik.data.AppSettings
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ReminderType(
    val workName: String,
    @param:StringRes val text: Int,
    val notificationId: Int,
) {
    EXERCISE("reminder_exercise", R.string.reminder_exercise_text, 1),
    SLEEP_DIARY("reminder_sleep", R.string.reminder_sleep_text, 2),
    WORRY_TIME("reminder_worry", R.string.reminder_worry_text, 3),
}

/** Whether a reminder is on and its time (minutes from midnight). */
fun AppSettings.reminder(type: ReminderType): Pair<Boolean, Int> = when (type) {
    ReminderType.EXERCISE -> reminderExercise to reminderExerciseTime
    ReminderType.SLEEP_DIARY -> reminderSleep to reminderSleepTime
    ReminderType.WORRY_TIME -> reminderWorry to worryTime
}

/**
 * Daily reminders with WorkManager: no exact-alarm permission, survives
 * restarts. Each reminder is a one-time job that schedules the next day's
 * job when it runs, so the time does not drift.
 */
class ReminderScheduler(private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)

    fun schedule(type: ReminderType, minuteOfDay: Int, now: ZonedDateTime = ZonedDateTime.now()) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayUntil(minuteOfDay, now), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_TYPE to type.name))
            .build()
        workManager.enqueueUniqueWork(type.workName, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(type: ReminderType) {
        workManager.cancelUniqueWork(type.workName)
    }

    /** Applies the reminder settings: schedules what is on, cancels what is off. */
    fun sync(settings: AppSettings) {
        ReminderType.entries.forEach { type ->
            val (enabled, time) = settings.reminder(type)
            if (enabled) schedule(type, time) else cancel(type)
        }
    }

    companion object {
        /** Milliseconds until the next [minuteOfDay] strictly after [now]. */
        fun delayUntil(minuteOfDay: Int, now: ZonedDateTime): Long {
            var next = now.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(now.zone)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next).toMillis()
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val type = inputData.getString(KEY_TYPE)?.let { name -> ReminderType.entries.firstOrNull { it.name == name } }
            ?: return Result.failure()
        val container = (applicationContext as MyslnikApplication).container
        val settings = container.settings.settings.first()
        val (enabled, time) = settings.reminder(type)
        if (!enabled) return Result.success()
        Notifications.showReminder(localized(applicationContext, settings.language), type)
        container.reminders.schedule(type, time)
        return Result.success()
    }

    companion object {
        const val KEY_TYPE = "type"

        /** Below Android 13 the app language is not applied to background work. */
        fun localized(context: Context, languageTag: String?): Context {
            if (languageTag == null) return context
            val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(languageTag)) }
            return context.createConfigurationContext(config)
        }
    }
}

object Notifications {
    private const val CHANNEL_REMINDERS = "reminders"

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.reminders_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * The lock screen only shows "Myślnik – Przypomnienie": what the reminder
     * is about stays private until the phone is unlocked.
     */
    fun showReminder(context: Context, type: ReminderType) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context,
            type.notificationId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.reminder_public_text))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(type.text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(type.text)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checked in canNotify()
        NotificationManagerCompat.from(context).notify(type.notificationId, notification)
    }
}
