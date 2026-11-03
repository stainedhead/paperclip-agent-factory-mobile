package com.stainedhead.paperclipmobile.infrastructure.persistence

import com.stainedhead.paperclipmobile.domain.ApprovalType
import com.stainedhead.paperclipmobile.domain.AttentionDetail
import com.stainedhead.paperclipmobile.domain.AttentionFeed
import com.stainedhead.paperclipmobile.domain.AttentionItem
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.AttentionSubject
import com.stainedhead.paperclipmobile.domain.BudgetAlertInfo
import com.stainedhead.paperclipmobile.domain.BudgetThreshold
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.DecisionVerb
import com.stainedhead.paperclipmobile.domain.Dismissal
import com.stainedhead.paperclipmobile.domain.DismissalKind
import com.stainedhead.paperclipmobile.domain.InteractionKind
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.ResolverAudience
import com.stainedhead.paperclipmobile.domain.ResolverPolicy
import com.stainedhead.paperclipmobile.domain.ResolverPolicySource
import com.stainedhead.paperclipmobile.domain.Severity
import com.stainedhead.paperclipmobile.domain.SubjectKind
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import com.stainedhead.paperclipmobile.testing.TestData
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Feeds that use every shape the cache must carry: all detail kinds, every optional field, newer-server values. */
object RichFeed {
    private val usd = Money.usdCents(12_345)
    private val limit = Money.usdCents(20_000)

    val details: List<AttentionDetail> =
        listOf(
            AttentionDetail.ApprovalSummary("hire_agent", "Hire a Designer"),
            AttentionDetail.PlanApproval("Issue", "Plan", null),
            AttentionDetail.Confirmation("Proceed?"),
            AttentionDetail.Questions(2, "Which region?"),
            AttentionDetail.SuggestedTasks(3, null),
            AttentionDetail.CheckboxConfirmation(4, "Tick all"),
            AttentionDetail.ItemVerdicts(5, null),
            AttentionDetail.FailedRun("Engineer 1", "exit 1"),
            AttentionDetail.Blocker("PAP-1", "Blocker", 2),
            AttentionDetail.Blocker(null, null, null),
            AttentionDetail.Budget(61, usd, limit),
            AttentionDetail.AgentError("QA agent", null),
            AttentionDetail.Generic("Something"),
            AttentionDetail.Unsupported("future_detail"),
        )

    private val everyOptionalSet =
        AttentionFixtures
            .item(
                "full",
                AttentionSourceKind.IssueThreadInteraction,
                AttentionSubject(
                    kind = SubjectKind.Interaction,
                    id = "subject-1",
                    title = "Title",
                    identifier = "PAP-9",
                    status = "open",
                    href = "/PAP/issues/PAP-9",
                    issueId = "iss-9",
                    approvalType = ApprovalType.Unsupported("future_type"),
                    interactionKind = InteractionKind.Unsupported("future_kind"),
                    budget = BudgetAlertInfo(BudgetThreshold.Soft, usd, limit, "company"),
                ),
            ).copy(
                severity = Severity.Critical,
                decideBy = DecideBy.OnDate(LocalDate(2026, 11, 9)),
                decisionVerbs = listOf(DecisionVerb("a", "Approve", "Does a"), DecisionVerb("b", "Reject")),
                inlineResolvable = true,
                dismissal =
                    Dismissal(
                        DismissalKind.Snooze,
                        Instant.parse("2026-11-02T08:00:00.123456789Z"),
                        Instant.parse("2026-11-03T08:00:00Z"),
                        isActive = true,
                    ),
                snoozedUntil = Instant.parse("2026-11-03T08:00:00Z"),
                originAgentName = "CEO",
                relatedIssue = AttentionSubject(SubjectKind.Issue, "iss-9", title = "Related"),
                resolverAudience =
                    ResolverAudience(
                        ResolverPolicy.Anyone,
                        ResolverPolicy.NotCreator,
                        ResolverPolicySource.CompanyCap,
                        addresseeAgentId = "agent-1",
                        addresseeUserId = "user-1",
                        addresseeName = "Alex",
                        createdByAgentId = "agent-2",
                        createdByAgentName = "Engineer 2",
                    ),
            )

    private val everyOptionalUnset =
        AttentionFixtures.item("bare", AttentionSourceKind.Unsupported("future_kind")).copy(
            decisionVerbs = emptyList(),
            decideBy = DecideBy.None,
        )

    /** One item per detail kind plus the two extremes of optional fields. */
    fun feed(): AttentionFeed {
        val perDetail =
            details.mapIndexed { index, detail ->
                AttentionFixtures.item("d$index").copy(detail = detail)
            }
        val items: List<AttentionItem> =
            listOf(everyOptionalSet, everyOptionalUnset) + perDetail + DecideBy.entriesForTest()
        return AttentionFeed(
            generatedAt = Instant.parse("2026-11-02T09:30:00.000000007Z"),
            totalCount = 99,
            deskBadgeCount = 7,
            nextCursor = "cursor-2",
            countsBySourceKind =
                mapOf(
                    AttentionSourceKind.Approval to 3,
                    AttentionSourceKind.Unsupported("future_kind") to 1,
                ),
            items = items,
        )
    }

    private fun DecideBy.Companion.entriesForTest(): List<AttentionItem> =
        listOf(DecideBy.Today, DecideBy.ThisWeek, DecideBy.Whenever).mapIndexed { index, decideBy ->
            AttentionFixtures.item("by$index").copy(decideBy = decideBy)
        }

    fun many(
        count: Int,
        cursor: String? = "next",
    ): AttentionFeed =
        AttentionFixtures
            .feed(
                items = (1..count).map { AttentionFixtures.item("m$it") },
                next = cursor,
                total = count + 50,
            ).copy(generatedAt = TestData.epoch)
}
