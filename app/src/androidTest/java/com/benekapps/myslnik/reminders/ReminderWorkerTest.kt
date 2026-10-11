package com.benekapps.myslnik.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.benekapps.myslnik.MyslnikApplication
import com.benekapps.myslnik.R
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val container get() = (context as MyslnikApplication).container
    private val notifications get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        notifications.cancelAll()
    }

    @After
    fun tearDown() {
        runBlocking { container.settings.setReminder(ReminderType.EXERCISE, false) }
        container.reminders.cancel(ReminderType.EXERCISE)
        notifications.cancelAll()
    }

    private fun runWorker(type: ReminderType) = runBlocking {
        TestListenableWorkerBuilder<ReminderWorker>(context)
            .setInputData(workDataOf(ReminderWorker.KEY_TYPE to type.name))
            .build()
            .doWork()
    }

    private fun posted(id: Int): Notification? =
        notifications.activeNotifications.firstOrNull { it.id == id }?.notification

    @Test
    fun enabledReminderShowsAPrivateNotification() {
        runBlocking { container.settings.setReminder(ReminderType.EXERCISE, true) }
        assertEquals(ListenableWorker.Result.success(), runWorker(ReminderType.EXERCISE))
        val n = posted(ReminderType.EXERCISE.notificationId)
        assertNotNull(n)
        assertEquals(Notification.VISIBILITY_PRIVATE, n!!.visibility)
        // The lock screen version says only "Przypomnienie".
        assertEquals(
            context.getString(R.string.reminder_public_text),
            n.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun disabledReminderStaysSilent() {
        runBlocking { container.settings.setReminder(ReminderType.EXERCISE, false) }
        assertEquals(ListenableWorker.Result.success(), runWorker(ReminderType.EXERCISE))
        assertNull(posted(ReminderType.EXERCISE.notificationId))
    }
}
