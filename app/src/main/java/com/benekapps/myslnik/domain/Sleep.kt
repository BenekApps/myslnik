package com.benekapps.myslnik.domain

import com.benekapps.myslnik.data.db.SleepEntry

/**
 * Sleep diary arithmetic (as in CBT for insomnia). All times are minutes from
 * noon of the night's date, see [SleepEntry].
 *
 * Time in bed = out of bed − into bed.
 * Total sleep = final wake − tried to sleep − time to fall asleep − time awake at night.
 * Sleep efficiency = total sleep / time in bed × 100 %.
 */
object SleepMath {
    private const val NOON = 12 * 60
    private const val DAY = 24 * 60

    /** Clock time (0–23, 0–59) to minutes from noon. 12:00 → 0, 23:00 → 660, 06:30 → 1110. */
    fun fromClock(hour: Int, minute: Int): Int = ((hour * 60 + minute) - NOON + DAY) % DAY

    /** Minutes from noon back to minutes from midnight (for display). */
    fun toMinuteOfDay(minutesFromNoon: Int): Int = (minutesFromNoon + NOON) % DAY

    fun timeInBed(e: SleepEntry): Int = (e.outOfBed - e.inBed).coerceAtLeast(0)

    fun totalSleep(e: SleepEntry): Int =
        (e.finalWake - e.triedToSleep - e.minutesToFallAsleep - e.minutesAwake).coerceAtLeast(0)

    /** 0–100, or null when time in bed is zero. */
    fun efficiency(e: SleepEntry): Int? {
        val inBed = timeInBed(e)
        if (inBed == 0) return null
        return (totalSleep(e) * 100.0 / inBed).coerceIn(0.0, 100.0).let { Math.round(it).toInt() }
    }

    enum class Problem { ORDER, TOO_MUCH_AWAKE }

    /** What is wrong with the entered times, or null when they make sense. */
    fun validate(e: SleepEntry): Problem? = when {
        !(e.inBed <= e.triedToSleep && e.triedToSleep <= e.finalWake && e.finalWake <= e.outOfBed) -> Problem.ORDER
        e.minutesToFallAsleep + e.minutesAwake > e.finalWake - e.triedToSleep -> Problem.TOO_MUCH_AWAKE
        else -> null
    }

    /** Averages over the given nights, null when there are none. */
    data class Summary(val nights: Int, val averageSleepMinutes: Int, val averageEfficiency: Int?)

    fun summary(entries: List<SleepEntry>): Summary? {
        if (entries.isEmpty()) return null
        val efficiencies = entries.mapNotNull { efficiency(it) }
        return Summary(
            nights = entries.size,
            averageSleepMinutes = entries.map { totalSleep(it) }.average().let { Math.round(it).toInt() },
            averageEfficiency = efficiencies.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it).toInt() },
        )
    }
}
