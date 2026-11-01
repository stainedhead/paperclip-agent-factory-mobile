package com.stainedhead.paperclipmobile.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The three Inbox groups in display order (PRD FR-030). */
enum class InboxGroup {
    DecideNow,
    ThisWeek,
    Whenever,
}

/** Mirrors the server's `isDecideNow` and `isNewToday` with UTC day boundaries (research Q1, ADR-015). */
object AttentionRules {
    private const val DAYS_IN_WEEK = 7

    /** Due today or overdue. `this_week` is due on a UTC Sunday, because the end of the week is the end of the day. */
    fun isDecideNow(
        decideBy: DecideBy,
        now: Instant,
    ): Boolean = deadline(decideBy, now.utcDate())?.let { it <= now.utcDate() } == true

    /** Surfaced on the current UTC day (arrival). */
    fun isNewToday(
        createdAt: Instant,
        now: Instant,
    ): Boolean = createdAt >= now.utcDate().atStartOfDayIn(TimeZone.UTC)

    fun group(
        decideBy: DecideBy,
        now: Instant,
    ): InboxGroup {
        val today = now.utcDate()
        val deadline = deadline(decideBy, today)
        return when {
            deadline == null -> InboxGroup.Whenever
            deadline <= today -> InboxGroup.DecideNow
            deadline <= endOfWeek(today) -> InboxGroup.ThisWeek
            else -> InboxGroup.Whenever
        }
    }

    /** The last UTC day the item may be decided, or null when it has no deadline. */
    private fun deadline(
        decideBy: DecideBy,
        today: LocalDate,
    ): LocalDate? =
        when (decideBy) {
            DecideBy.Today -> today
            DecideBy.ThisWeek -> endOfWeek(today)
            is DecideBy.OnDate -> decideBy.date
            DecideBy.Whenever, DecideBy.None -> null
        }

    /** The Sunday that ends the Monday to Sunday week containing [today] (the server's `endOfUtcWeek`). */
    private fun endOfWeek(today: LocalDate): LocalDate {
        val daysUntilSunday = if (today.dayOfWeek == DayOfWeek.SUNDAY) 0 else DAYS_IN_WEEK - today.dayOfWeek.ordinal - 1
        return today.plus(daysUntilSunday, DateTimeUnit.DAY)
    }

    private fun Instant.utcDate(): LocalDate = toLocalDateTime(TimeZone.UTC).date
}
