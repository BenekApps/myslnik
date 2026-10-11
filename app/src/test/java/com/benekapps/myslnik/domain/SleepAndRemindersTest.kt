package com.benekapps.myslnik.domain

import com.benekapps.myslnik.data.db.SleepEntry
import com.benekapps.myslnik.reminders.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SleepMathTest {
    private fun night(
        inBed: Pair<Int, Int> = 23 to 0,
        tried: Pair<Int, Int> = 23 to 0,
        latency: Int = 0,
        awake: Int = 0,
        wake: Pair<Int, Int> = 7 to 0,
        out: Pair<Int, Int> = 7 to 0,
    ) = SleepEntry(
        createdAt = 0, updatedAt = 0, night = 0,
        inBed = SleepMath.fromClock(inBed.first, inBed.second),
        triedToSleep = SleepMath.fromClock(tried.first, tried.second),
        minutesToFallAsleep = latency, awakenings = 1, minutesAwake = awake,
        finalWake = SleepMath.fromClock(wake.first, wake.second),
        outOfBed = SleepMath.fromClock(out.first, out.second),
        quality = 3,
    )

    @Test
    fun `clock conversion around noon and midnight`() {
        assertEquals(0, SleepMath.fromClock(12, 0))
        assertEquals(660, SleepMath.fromClock(23, 0))
        assertEquals(720, SleepMath.fromClock(0, 0))
        assertEquals(1110, SleepMath.fromClock(6, 30))
        assertEquals(6 * 60 + 30, SleepMath.toMinuteOfDay(1110))
        assertEquals(23 * 60, SleepMath.toMinuteOfDay(660))
    }

    @Test
    fun `perfect night is 100 percent`() {
        val e = night()
        assertEquals(8 * 60, SleepMath.timeInBed(e))
        assertEquals(8 * 60, SleepMath.totalSleep(e))
        assertEquals(100, SleepMath.efficiency(e))
    }

    @Test
    fun `typical night`() {
        // 22:30 in bed, 23:00 lights off, 30 min to fall asleep, 30 min awake,
        // 6:30 final wake, 7:00 out of bed: TIB 8 h 30, TST 6 h 30, SE 76 %.
        val e = night(inBed = 22 to 30, tried = 23 to 0, latency = 30, awake = 30, wake = 6 to 30, out = 7 to 0)
        assertEquals(510, SleepMath.timeInBed(e))
        assertEquals(390, SleepMath.totalSleep(e))
        assertEquals(76, SleepMath.efficiency(e))
        assertNull(SleepMath.validate(e))
    }

    @Test
    fun `going to bed after midnight works`() {
        val e = night(inBed = 1 to 0, tried = 1 to 30, wake = 8 to 30, out = 9 to 0)
        assertEquals(480, SleepMath.timeInBed(e))
        assertEquals(420, SleepMath.totalSleep(e))
        assertEquals(88, SleepMath.efficiency(e))
    }

    @Test
    fun `wrong order and impossible minutes are reported`() {
        assertEquals(SleepMath.Problem.ORDER, SleepMath.validate(night(wake = 7 to 0, out = 6 to 0)))
        assertEquals(SleepMath.Problem.TOO_MUCH_AWAKE, SleepMath.validate(night(latency = 300, awake = 300)))
    }

    @Test
    fun `summary averages`() {
        val s = SleepMath.summary(listOf(night(), night(awake = 60)))!!
        assertEquals(2, s.nights)
        assertEquals(450, s.averageSleepMinutes)
        assertEquals(94, s.averageEfficiency)
        assertNull(SleepMath.summary(emptyList()))
    }
}

class ReminderDelayTest {
    private val zone = ZoneId.of("Europe/Warsaw")

    @Test
    fun `later today`() {
        val now = ZonedDateTime.of(2026, 10, 11, 7, 0, 0, 0, zone)
        assertEquals(60 * 60 * 1000L, ReminderScheduler.delayUntil(8 * 60, now))
    }

    @Test
    fun `already passed means tomorrow`() {
        val now = ZonedDateTime.of(2026, 10, 11, 20, 0, 0, 0, zone)
        assertEquals(24 * 60 * 60 * 1000L, ReminderScheduler.delayUntil(20 * 60, now))
    }

    @Test
    fun `daylight saving change keeps the clock time`() {
        // 25 October 2026: clocks go back one hour in Poland.
        val now = ZonedDateTime.of(2026, 10, 24, 20, 0, 0, 0, zone)
        assertEquals(25 * 60 * 60 * 1000L, ReminderScheduler.delayUntil(20 * 60, now))
    }
}
