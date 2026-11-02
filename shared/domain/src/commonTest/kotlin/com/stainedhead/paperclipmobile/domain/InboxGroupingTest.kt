package com.stainedhead.paperclipmobile.domain

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

class InboxGroupingTest {
    private val wednesday = Instant.parse("2026-11-04T12:00:00Z")

    private fun item(
        id: String,
        decideBy: DecideBy,
        severity: Severity = Severity.Medium,
    ) = AttentionItem(
        id = id,
        sourceKind = AttentionSourceKind.Approval,
        subject = AttentionSubject(SubjectKind.Approval, id),
        whyNow = "why",
        severity = severity,
        dismissalKey = "attention:$id",
        activityAt = wednesday,
        createdAt = wednesday,
        decideBy = decideBy,
    )

    @Test
    fun `FR-030 items fall into decide now then this week then whenever and empty groups are omitted`() {
        val sections =
            InboxGrouping.group(
                listOf(
                    item("w", DecideBy.Whenever),
                    item("t", DecideBy.Today),
                    item("n", DecideBy.None),
                ),
                wednesday,
            )

        sections.map { it.group } shouldBe listOf(InboxGroup.DecideNow, InboxGroup.Whenever)
        sections[0].items.map { it.id } shouldBe listOf("t")
        sections[1].items.map { it.id } shouldBe listOf("w", "n")
    }

    @Test
    fun `FR-030 severity orders items inside a group and never moves one between groups`() {
        val sections =
            InboxGrouping.group(
                listOf(
                    item("low-today", DecideBy.Today, Severity.Low),
                    item("crit-whenever", DecideBy.Whenever, Severity.Critical),
                    item("crit-today", DecideBy.Today, Severity.Critical),
                    item("med-today", DecideBy.Today, Severity.Medium),
                    item("high-week", DecideBy.ThisWeek, Severity.High),
                ),
                wednesday,
            )

        sections.map { it.group } shouldBe listOf(InboxGroup.DecideNow, InboxGroup.ThisWeek, InboxGroup.Whenever)
        sections[0].items.map { it.id } shouldBe listOf("crit-today", "med-today", "low-today")
        sections[2].items.map { it.id } shouldBe listOf("crit-whenever")
    }

    @Test
    fun `items with the same severity keep the server's order`() {
        val sections =
            InboxGrouping.group(
                listOf(item("b", DecideBy.Today), item("a", DecideBy.Today), item("c", DecideBy.Today)),
                wednesday,
            )

        sections.single().items.map { it.id } shouldBe listOf("b", "a", "c")
    }

    @Test
    fun `an empty feed has no sections`() {
        InboxGrouping.group(emptyList(), wednesday) shouldBe emptyList()
    }
}
