package com.benekapps.myslnik.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A date range in epoch millis, both ends inclusive. */
data class TimeRange(val from: Long, val to: Long) {
    operator fun contains(time: Long) = time in from..to

    companion object {
        val ALL = TimeRange(0, Long.MAX_VALUE)

        /** Whole days from [start] to [end] in the given zone. */
        fun ofDays(start: LocalDate, end: LocalDate, zone: ZoneId): TimeRange = TimeRange(
            from = start.atStartOfDay(zone).toInstant().toEpochMilli(),
            to = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
        )

        /** The last [days] days including today. */
        fun lastDays(days: Int, now: Long, zone: ZoneId): TimeRange {
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            return ofDays(today.minusDays(days - 1L), today, zone)
        }
    }
}

enum class Period(val days: Int?) {
    WEEK(7), MONTH(30), QUARTER(90), ALL(null), CUSTOM(null);

    fun range(now: Long, zone: ZoneId, custom: TimeRange? = null): TimeRange = when (this) {
        CUSTOM -> custom ?: TimeRange.ALL
        ALL -> TimeRange.ALL
        else -> TimeRange.lastDays(days!!, now, zone)
    }
}

/** Average mood per day for the chart; days without entries are null. */
fun dailyAverages(
    entries: List<Pair<Long, Int>>,
    days: Int,
    now: Long,
    zone: ZoneId,
): List<Pair<LocalDate, Double?>> {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val byDay = entries.groupBy({ Instant.ofEpochMilli(it.first).atZone(zone).toLocalDate() }, { it.second })
    return (days - 1 downTo 0).map { offset ->
        val day = today.minusDays(offset.toLong())
        day to byDay[day]?.average()
    }
}
