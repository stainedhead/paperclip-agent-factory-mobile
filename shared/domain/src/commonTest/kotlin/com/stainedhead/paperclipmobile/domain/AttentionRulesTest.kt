package com.stainedhead.paperclipmobile.domain

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.time.Instant

/**
 * Table-driven mirror of the server's `isDecideNow` and `isNewToday` (`attention.ts:508-516`, research Q1/Q2, ADR-015).
 * 2026-11-01 is a UTC Sunday; 2026-11-04 is a Wednesday.
 */
class AttentionRulesTest {
    private val sunday = Instant.parse("2026-11-01T12:00:00Z")
    private val wednesday = Instant.parse("2026-11-04T12:00:00Z")
    private val sundayLastMs = Instant.parse("2026-11-01T23:59:59.999Z")
    private val mondayFirstMs = Instant.parse("2026-11-02T00:00:00Z")

    private data class DecideCase(
        val label: String,
        val decideBy: DecideBy,
        val now: Instant,
        val decideNow: Boolean,
        val group: InboxGroup,
    )

    private fun date(text: String) = DecideBy.OnDate(LocalDate.parse(text))

    private val cases =
        listOf(
            DecideCase("today", DecideBy.Today, wednesday, true, InboxGroup.DecideNow),
            DecideCase("this_week midweek", DecideBy.ThisWeek, wednesday, false, InboxGroup.ThisWeek),
            DecideCase("this_week on a UTC Sunday is due", DecideBy.ThisWeek, sunday, true, InboxGroup.DecideNow),
            DecideCase(
                "this_week at the last millisecond of Sunday",
                DecideBy.ThisWeek,
                sundayLastMs,
                true,
                InboxGroup.DecideNow,
            ),
            DecideCase(
                "this_week at the first millisecond of Monday",
                DecideBy.ThisWeek,
                mondayFirstMs,
                false,
                InboxGroup.ThisWeek,
            ),
            DecideCase("whenever", DecideBy.Whenever, wednesday, false, InboxGroup.Whenever),
            DecideCase("none", DecideBy.None, wednesday, false, InboxGroup.Whenever),
            DecideCase("past date", date("2026-11-01"), wednesday, true, InboxGroup.DecideNow),
            DecideCase("today's date", date("2026-11-04"), wednesday, true, InboxGroup.DecideNow),
            DecideCase(
                "tomorrow midweek is still this week",
                date("2026-11-05"),
                wednesday,
                false,
                InboxGroup.ThisWeek,
            ),
            DecideCase(
                "the Sunday of this week is this week",
                date("2026-11-08"),
                wednesday,
                false,
                InboxGroup.ThisWeek,
            ),
            DecideCase(
                "the Monday after is beyond this week",
                date("2026-11-09"),
                wednesday,
                false,
                InboxGroup.Whenever,
            ),
            DecideCase(
                "on a Sunday the next day is already next week",
                date("2026-11-02"),
                sunday,
                false,
                InboxGroup.Whenever,
            ),
        )

    @Test
    fun `FR-030 every decideBy case gives the server's decide-now answer and the app's group`() {
        cases.forEach { case ->
            withClue(case.label) {
                AttentionRules.isDecideNow(case.decideBy, case.now) shouldBe case.decideNow
                AttentionRules.group(case.decideBy, case.now) shouldBe case.group
            }
        }
    }

    @Test
    fun `isNewToday uses the UTC day including the midnight edge`() {
        val now = Instant.parse("2026-07-09T12:00:00Z")
        AttentionRules.isNewToday(Instant.parse("2026-07-09T00:00:00Z"), now) shouldBe true
        AttentionRules.isNewToday(Instant.parse("2026-07-09T00:00:01Z"), now) shouldBe true
        AttentionRules.isNewToday(Instant.parse("2026-07-08T23:59:59.999Z"), now) shouldBe false
        AttentionRules.isNewToday(Instant.parse("2026-07-10T00:00:00Z"), now) shouldBe true
    }

    @Test
    fun `the same instant is on different UTC days only across midnight UTC`() {
        val lastMsBefore = Instant.parse("2026-07-09T23:59:59.999Z")
        val firstMsAfter = Instant.parse("2026-07-10T00:00:00Z")
        val created = Instant.parse("2026-07-09T10:00:00Z")
        AttentionRules.isNewToday(created, lastMsBefore) shouldBe true
        AttentionRules.isNewToday(created, firstMsAfter) shouldBe false
    }

    private inline fun withClue(
        clue: String,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (error: AssertionError) {
            throw AssertionError("$clue: ${error.message}", error)
        }
    }
}
