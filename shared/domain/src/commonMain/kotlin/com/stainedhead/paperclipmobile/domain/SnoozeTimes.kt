package com.stainedhead.paperclipmobile.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

enum class SnoozePreset {
    OneHour,
    Tonight,
    Tomorrow,
    NextWeek,
}

/** Snooze cut-offs in the device time zone (ADR-020). Grouping elsewhere stays UTC. */
object SnoozeTimes {
    private const val TONIGHT_HOUR = 20
    private const val MORNING_HOUR = 8
    private const val DAYS_IN_WEEK = 7

    fun resolve(
        preset: SnoozePreset,
        now: Instant,
        zone: TimeZone,
    ): Instant {
        val today = now.toLocalDateTime(zone).date
        return when (preset) {
            SnoozePreset.OneHour -> now + 1.hours
            SnoozePreset.Tonight -> today.atTime(LocalTime(TONIGHT_HOUR, 0)).toInstant(zone)
            SnoozePreset.Tomorrow -> morning(today.plus(1, DateTimeUnit.DAY), zone)
            SnoozePreset.NextWeek -> morning(nextMonday(today), zone)
        }
    }

    /** False when the preset would not be in the future, for example tonight after 20:00. */
    fun isAvailable(
        preset: SnoozePreset,
        now: Instant,
        zone: TimeZone,
    ): Boolean = resolve(preset, now, zone) > now

    private fun nextMonday(today: LocalDate): LocalDate {
        val daysAhead = DAYS_IN_WEEK - today.dayOfWeek.ordinal
        return today.plus(daysAhead, DateTimeUnit.DAY)
    }

    private fun morning(
        date: LocalDate,
        zone: TimeZone,
    ): Instant = date.atTime(LocalTime(MORNING_HOUR, 0)).toInstant(zone)
}
