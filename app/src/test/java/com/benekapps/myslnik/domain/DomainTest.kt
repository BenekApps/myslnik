package com.benekapps.myslnik.domain

import com.benekapps.myslnik.data.AppSettings
import com.benekapps.myslnik.data.security.PinHasher
import com.benekapps.myslnik.lock.AppLockManager
import com.benekapps.myslnik.ui.home.HomeViewModel
import com.benekapps.myslnik.ui.lock.LockViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class PinHasherTest {
    @Test
    fun `correct pin verifies, wrong pin does not`() {
        val stored = PinHasher.hash("2580", iterations = 1_000)
        assertTrue(PinHasher.verify("2580", stored))
        assertFalse(PinHasher.verify("2581", stored))
        assertFalse(PinHasher.verify("", stored))
    }

    @Test
    fun `the pin itself is not stored`() {
        assertFalse(PinHasher.hash("123456", iterations = 1_000).contains("123456"))
    }

    @Test
    fun `same pin gets a different salt each time`() {
        assertFalse(PinHasher.hash("1234", 1_000) == PinHasher.hash("1234", 1_000))
    }

    @Test
    fun `broken stored values never verify`() {
        assertFalse(PinHasher.verify("1234", ""))
        assertFalse(PinHasher.verify("1234", "v1:abc:def:ghi"))
        assertFalse(PinHasher.verify("1234", "v2:1000:AAAA:AAAA"))
    }

    @Test
    fun `pin rules`() {
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("12345678"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("123456789"))
        assertFalse(PinHasher.isValidPin("12a4"))
    }
}

class LockoutTest {
    @Test
    fun `first four wrong pins have no delay`() {
        (1..4).forEach { assertEquals(0L, LockViewModel.lockoutUntil(it, now = 1_000)) }
    }

    @Test
    fun `delay starts at 30 seconds and doubles up to 15 minutes`() {
        assertEquals(1_000 + 30_000L, LockViewModel.lockoutUntil(5, now = 1_000))
        assertEquals(1_000 + 60_000L, LockViewModel.lockoutUntil(6, now = 1_000))
        assertEquals(1_000 + 15 * 60_000L, LockViewModel.lockoutUntil(50, now = 1_000))
    }
}

class AppLockManagerTest {
    private var time = 0L
    private val manager = AppLockManager(timeoutMs = 60_000, clock = { time }).apply {
        lockEnabled = true
        unlock()
    }

    @Test
    fun `short trip to another app does not lock`() {
        manager.onStop(FakeOwner)
        time += 59_000
        manager.onStart(FakeOwner)
        assertFalse(manager.locked.value)
    }

    @Test
    fun `a minute in the background locks`() {
        manager.onStop(FakeOwner)
        time += 60_000
        manager.onStart(FakeOwner)
        assertTrue(manager.locked.value)
    }

    @Test
    fun `without a pin nothing locks`() {
        manager.lockEnabled = false
        manager.onStop(FakeOwner)
        time += 600_000
        manager.onStart(FakeOwner)
        assertFalse(manager.locked.value)
    }

    private object FakeOwner : androidx.lifecycle.LifecycleOwner {
        override val lifecycle: androidx.lifecycle.Lifecycle
            get() = throw UnsupportedOperationException()
    }
}

class CrisisCheckTest {
    @Test
    fun `emotion thresholds`() {
        assertFalse(CrisisCheck.isEmotionCritical(85))
        assertTrue(CrisisCheck.isEmotionCritical(90))
        assertTrue(CrisisCheck.isEmotionCritical(100))
    }

    @Test
    fun `mood thresholds`() {
        assertTrue(CrisisCheck.isMoodCritical(0))
        assertTrue(CrisisCheck.isMoodCritical(1))
        assertFalse(CrisisCheck.isMoodCritical(2))
    }
}

class PeriodsTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private fun at(date: String, hour: Int) =
        ZonedDateTime.of(LocalDate.parse(date).atTime(hour, 0), zone).toInstant().toEpochMilli()

    @Test
    fun `last 7 days covers whole days including today`() {
        val now = at("2026-10-11", 15)
        val range = TimeRange.lastDays(7, now, zone)
        assertTrue(at("2026-10-05", 0) in range)
        assertFalse(at("2026-10-05", 0) - 1 in range)
        assertTrue(at("2026-10-11", 23) in range)
    }

    @Test
    fun `custom range includes the whole last day`() {
        val range = TimeRange.ofDays(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-03"), zone)
        assertTrue(at("2026-10-03", 23) in range)
        assertFalse(at("2026-10-04", 0) in range)
    }

    @Test
    fun `daily averages leave empty days null`() {
        val now = at("2026-10-11", 20)
        val points = dailyAverages(
            listOf(at("2026-10-11", 8) to 4, at("2026-10-11", 18) to 6, at("2026-10-09", 12) to 3),
            days = 3,
            now = now,
            zone = zone,
        )
        assertEquals(listOf(LocalDate.parse("2026-10-09"), LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-11")), points.map { it.first })
        assertEquals(3.0, points[0].second!!, 0.0)
        assertNull(points[1].second)
        assertEquals(5.0, points[2].second!!, 0.0)
    }
}

class BackupReminderTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 100 * day

    @Test
    fun `no reminder for few entries`() {
        assertFalse(HomeViewModel.shouldRemindAboutBackup(9, AppSettings(), now))
    }

    @Test
    fun `reminder when there was never a backup`() {
        assertTrue(HomeViewModel.shouldRemindAboutBackup(10, AppSettings(), now))
    }

    @Test
    fun `no reminder after a recent backup or while snoozed`() {
        assertFalse(HomeViewModel.shouldRemindAboutBackup(50, AppSettings(lastBackupAt = now - 5 * day), now))
        assertFalse(HomeViewModel.shouldRemindAboutBackup(50, AppSettings(backupReminderSnoozedUntil = now + day), now))
        assertTrue(HomeViewModel.shouldRemindAboutBackup(50, AppSettings(lastBackupAt = now - 31 * day), now))
    }
}
