package com.stainedhead.paperclipmobile.domain

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

class InlineActionPolicyTest {
    private val at = Instant.parse("2026-11-04T12:00:00Z")

    private fun item(
        kind: AttentionSourceKind,
        subject: AttentionSubject,
        inline: Boolean = true,
        audience: ResolverAudience? = null,
    ) = AttentionItem(
        id = "${kind.serverName}:1",
        sourceKind = kind,
        subject = subject,
        whyNow = "why",
        severity = Severity.High,
        dismissalKey = "attention:1",
        activityAt = at,
        createdAt = at,
        inlineResolvable = inline,
        resolverAudience = audience,
    )

    private val approval =
        item(
            AttentionSourceKind.Approval,
            AttentionSubject(SubjectKind.Approval, "ap-1", approvalType = ApprovalType.HireAgent),
        )

    private fun budget(threshold: BudgetThreshold) =
        item(
            AttentionSourceKind.BudgetAlert,
            AttentionSubject(
                SubjectKind.BudgetIncident,
                "inc-1",
                budget = BudgetAlertInfo(threshold, Money.usdCents(9_000), Money.usdCents(10_000)),
            ),
        )

    @Test
    fun `FR-033 an approval offers approve and reject to an operator and an owner`() {
        InlineActionPolicy.actionsFor(approval, Role.Owner) shouldBe listOf(InlineAction.Approve, InlineAction.Reject)
        InlineActionPolicy.actionsFor(approval, Role.Operator) shouldBe
            listOf(InlineAction.Approve, InlineAction.Reject)
    }

    @Test
    fun `a viewer gets no inline action on anything`() {
        InlineActionPolicy.actionsFor(approval, Role.Viewer) shouldBe emptyList()
        InlineActionPolicy.actionsFor(budget(BudgetThreshold.Hard), Role.Viewer) shouldBe emptyList()
    }

    @Test
    fun `FR-033 a hard stop offers keep paused to operators and raise only to owners`() {
        InlineActionPolicy.actionsFor(budget(BudgetThreshold.Hard), Role.Owner) shouldBe
            listOf(InlineAction.KeepPaused, InlineAction.RaiseAndResume)
        InlineActionPolicy.actionsFor(budget(BudgetThreshold.Hard), Role.Operator) shouldBe
            listOf(InlineAction.KeepPaused)
    }

    @Test
    fun `a soft budget warning opens the sheet but has no inline action`() {
        InlineActionPolicy.actionsFor(budget(BudgetThreshold.Soft), Role.Owner) shouldBe emptyList()
    }

    @Test
    fun `the server flag is required`() {
        InlineActionPolicy.actionsFor(approval.copy(inlineResolvable = false), Role.Owner) shouldBe emptyList()
        InlineActionPolicy.actionsFor(budget(BudgetThreshold.Hard).copy(inlineResolvable = false), Role.Owner) shouldBe
            emptyList()
    }

    @Test
    fun `every other kind has no inline action even when the server says it is resolvable`() {
        val others =
            listOf(
                AttentionSourceKind.Decision,
                AttentionSourceKind.IssueThreadInteraction,
                AttentionSourceKind.JoinRequest,
                AttentionSourceKind.FailedRun,
                AttentionSourceKind.AgentErrorAlert,
                AttentionSourceKind.Review,
                AttentionSourceKind.Unsupported("new_kind"),
            )
        others.forEach {
            InlineActionPolicy.actionsFor(item(it, AttentionSubject(SubjectKind.Unknown, "x")), Role.Owner) shouldBe
                emptyList()
        }
    }

    @Test
    fun `an approval without budget metadata on a budget alert offers nothing`() {
        val noMeta = item(AttentionSourceKind.BudgetAlert, AttentionSubject(SubjectKind.BudgetIncident, "inc-1"))
        InlineActionPolicy.actionsFor(noMeta, Role.Owner) shouldBe emptyList()
    }

    private data class Route(
        val kind: AttentionSourceKind,
        val subject: AttentionSubject,
        val expected: Destination,
    )

    private val href = "/PAP/anything"

    private val inAppRoutes =
        listOf(
            Route(
                AttentionSourceKind.Approval,
                AttentionSubject(SubjectKind.Approval, "a1", href = href),
                Destination(DestinationKind.Approval, "a1", webPath = href),
            ),
            Route(
                AttentionSourceKind.Decision,
                AttentionSubject(SubjectKind.Decision, "d1"),
                Destination(DestinationKind.Decision, "d1"),
            ),
            Route(
                AttentionSourceKind.IssueThreadInteraction,
                AttentionSubject(SubjectKind.Interaction, "i1", issueId = "iss-1", href = href),
                Destination(DestinationKind.Interaction, "i1", issueId = "iss-1", webPath = href),
            ),
            Route(
                AttentionSourceKind.BudgetAlert,
                AttentionSubject(SubjectKind.BudgetIncident, "b1"),
                Destination(DestinationKind.BudgetIncident, "b1"),
            ),
            Route(
                AttentionSourceKind.FailedRun,
                AttentionSubject(SubjectKind.Run, "r1", href = href),
                Destination(DestinationKind.Run, "r1", webPath = href),
            ),
            Route(
                AttentionSourceKind.AgentErrorAlert,
                AttentionSubject(SubjectKind.Agent, "g1"),
                Destination(DestinationKind.Agent, "g1"),
            ),
            Route(
                AttentionSourceKind.Review,
                AttentionSubject(SubjectKind.Issue, "s1"),
                Destination(DestinationKind.Issue, "s1"),
            ),
            Route(
                AttentionSourceKind.BlockerAttention,
                AttentionSubject(SubjectKind.Issue, "s2"),
                Destination(DestinationKind.Issue, "s2"),
            ),
        )

    private val webOnlyRoutes =
        listOf(
            Route(
                AttentionSourceKind.JoinRequest,
                AttentionSubject(SubjectKind.JoinRequest, "j1", href = href),
                Destination(DestinationKind.WebOnly, "j1", webPath = href),
            ),
            Route(
                AttentionSourceKind.RecoveryAction,
                AttentionSubject(SubjectKind.RecoveryAction, "c1"),
                Destination(DestinationKind.WebOnly, "c1"),
            ),
            Route(
                AttentionSourceKind.ProductivityReview,
                AttentionSubject(SubjectKind.Unknown, "p1"),
                Destination(DestinationKind.WebOnly, "p1"),
            ),
            Route(
                AttentionSourceKind.Unsupported("new"),
                AttentionSubject(SubjectKind.Unknown, "n1", href = null),
                Destination(DestinationKind.WebOnly, "n1"),
            ),
        )

    @Test
    fun `each source kind with a screen opens that screen`() {
        inAppRoutes.forEach { InlineActionPolicy.destinationOf(item(it.kind, it.subject)) shouldBe it.expected }
    }

    @Test
    fun `kinds without a screen open the web app`() {
        webOnlyRoutes.forEach { InlineActionPolicy.destinationOf(item(it.kind, it.subject)) shouldBe it.expected }
    }

    @Test
    fun `an interaction is read-only for a viewer or when it is addressed to someone else`() {
        fun audience(user: String?) =
            ResolverAudience(
                ResolverPolicy.Anyone,
                ResolverPolicy.Anyone,
                ResolverPolicySource.Requested,
                addresseeUserId = user,
            )
        val interaction =
            item(
                AttentionSourceKind.IssueThreadInteraction,
                AttentionSubject(SubjectKind.Interaction, "i1"),
                audience = audience("user-2"),
            )
        InlineActionPolicy.isReadOnly(interaction, Role.Operator, "user-1") shouldBe true
        InlineActionPolicy.isReadOnly(interaction, Role.Operator, "user-2") shouldBe false
        InlineActionPolicy.isReadOnly(interaction, Role.Viewer, "user-2") shouldBe true
        InlineActionPolicy.isReadOnly(
            interaction.copy(resolverAudience = audience(null)),
            Role.Owner,
            "user-1",
        ) shouldBe
            false
        InlineActionPolicy.isReadOnly(interaction.copy(resolverAudience = null), Role.Owner, "user-1") shouldBe false
    }
}

class BudgetIncidentFromItemTest {
    private val at = Instant.parse("2026-11-04T12:00:00Z")

    private fun item(
        kind: AttentionSourceKind,
        subject: AttentionSubject,
    ) = AttentionItem("x", kind, subject, "why", Severity.High, "attention:x", at, at)

    @Test
    fun `a budget alert card carries enough to build its incident`() {
        val subject =
            AttentionSubject(
                SubjectKind.BudgetIncident,
                "inc-1",
                title = "Engineer 2 budget hard stop",
                status = "open",
                budget = BudgetAlertInfo(BudgetThreshold.Hard, Money.usdCents(9_000), Money.usdCents(10_000), "agent"),
            )

        item(AttentionSourceKind.BudgetAlert, subject).budgetIncident() shouldBe
            BudgetIncident(
                id = BudgetIncidentId("inc-1"),
                scopeName = "Engineer 2 budget hard stop",
                scopeType = "agent",
                threshold = BudgetThreshold.Hard,
                observed = Money.usdCents(9_000),
                limit = Money.usdCents(10_000),
                status = IncidentStatus.Open,
            )
    }

    @Test
    fun `other cards and budget cards without metadata have no incident`() {
        item(AttentionSourceKind.Approval, AttentionSubject(SubjectKind.Approval, "a")).budgetIncident() shouldBe null
        item(
            AttentionSourceKind.BudgetAlert,
            AttentionSubject(SubjectKind.BudgetIncident, "b"),
        ).budgetIncident() shouldBe
            null
    }

    @Test
    fun `a missing title and scope type fall back to empty text`() {
        val subject =
            AttentionSubject(
                SubjectKind.BudgetIncident,
                "inc-2",
                budget = BudgetAlertInfo(BudgetThreshold.Soft, Money.usdCents(1), Money.usdCents(2)),
            )

        val incident = item(AttentionSourceKind.BudgetAlert, subject).budgetIncident()

        incident?.scopeName shouldBe ""
        incident?.scopeType shouldBe ""
    }
}
