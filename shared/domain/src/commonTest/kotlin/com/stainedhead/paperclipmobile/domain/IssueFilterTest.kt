package com.stainedhead.paperclipmobile.domain

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

internal fun testIssue(
    id: String = "i1",
    status: IssueStatus = IssueStatus.InProgress,
    priority: IssuePriority = IssuePriority.High,
    assigneeAgentId: String? = null,
    assigneeUserId: String? = null,
    parentId: String? = null,
) = Issue(
    id = IssueId(id),
    identifier = "PAP-1182",
    title = "Ship the billing migration",
    status = status,
    priority = priority,
    assigneeAgentId = assigneeAgentId?.let(::AgentId),
    assigneeUserId = assigneeUserId,
    projectId = null,
    parentId = parentId?.let(::IssueId),
    createdAt = Instant.parse("2026-11-01T08:00:00Z"),
    updatedAt = Instant.parse("2026-11-01T09:00:00Z"),
)

class IssueFilterTest {
    private val me = "user-1"

    @Test
    fun `FR-060 each saved filter says which server statuses it asks for`() {
        val open = listOf("backlog", "todo", "in_progress", "in_review", "blocked")
        IssueFilter.AllOpen.serverStatuses shouldBe open
        IssueFilter.CriticalAndHigh.serverStatuses shouldBe open
        IssueFilter.AssignedToMe.serverStatuses shouldBe open
        IssueFilter.InReview.serverStatuses shouldBe listOf("in_review")
        IssueFilter.Blocked.serverStatuses shouldBe emptyList()
    }

    @Test
    fun `FR-060 only blocked uses the attention parameter and only assigned to me uses the me parameter`() {
        IssueFilter.entries.filter { it.serverAttentionBlocked } shouldBe listOf(IssueFilter.Blocked)
        IssueFilter.entries.filter { it.serverAssigneeMe } shouldBe listOf(IssueFilter.AssignedToMe)
    }

    @Test
    fun `FR-060 critical and high keeps only urgent open issues`() {
        val filter = IssueFilter.CriticalAndHigh
        filter.matches(testIssue(priority = IssuePriority.Critical), me) shouldBe true
        filter.matches(testIssue(priority = IssuePriority.High), me) shouldBe true
        filter.matches(testIssue(priority = IssuePriority.Medium), me) shouldBe false
        filter.matches(testIssue(priority = IssuePriority.High, status = IssueStatus.Done), me) shouldBe false
    }

    @Test
    fun `FR-060 the other filters match on status and assignee`() {
        IssueFilter.AllOpen.matches(testIssue(status = IssueStatus.Backlog), me) shouldBe true
        IssueFilter.AllOpen.matches(testIssue(status = IssueStatus.Cancelled), me) shouldBe false
        IssueFilter.InReview.matches(testIssue(status = IssueStatus.InReview), me) shouldBe true
        IssueFilter.InReview.matches(testIssue(status = IssueStatus.Todo), me) shouldBe false
        IssueFilter.Blocked.matches(testIssue(status = IssueStatus.Blocked), me) shouldBe true
        IssueFilter.Blocked.matches(testIssue(status = IssueStatus.Todo), me) shouldBe false
        IssueFilter.AssignedToMe.matches(testIssue(assigneeUserId = me), me) shouldBe true
        IssueFilter.AssignedToMe.matches(testIssue(assigneeUserId = "other"), me) shouldBe false
        IssueFilter.AssignedToMe.matches(testIssue(assigneeUserId = me, status = IssueStatus.Done), me) shouldBe false
        IssueFilter.AssignedToMe.matches(testIssue(assigneeUserId = me), null) shouldBe false
    }

    @Test
    fun `a search matches the identifier in any common spelling and the title`() {
        val issue = testIssue()
        IssueQuery.matchesSearch(issue, "PAP-1182") shouldBe true
        IssueQuery.matchesSearch(issue, "pap1182") shouldBe true
        IssueQuery.matchesSearch(issue, "pap 1182") shouldBe true
        IssueQuery.matchesSearch(issue, "billing") shouldBe true
        IssueQuery.matchesSearch(issue, "nothing here") shouldBe false
        IssueQuery.matchesSearch(issue, "  ") shouldBe true
    }

    @Test
    fun `an issue query defaults to the first page of all open issues`() {
        val query = IssueQuery()
        query.filter shouldBe IssueFilter.AllOpen
        query.search shouldBe null
        query.offset shouldBe 0
        query.limit shouldBe IssueQuery.DEFAULT_LIMIT
        query.nextPage().offset shouldBe IssueQuery.DEFAULT_LIMIT
    }
}
