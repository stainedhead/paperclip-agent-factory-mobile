package com.stainedhead.paperclipmobile.domain

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ApprovalActionsTest {
    private fun actions(
        status: ApprovalStatus,
        role: Role,
    ) = ApprovalActions.availableFor(status, role)

    @Test
    fun `FR-051 approve and reject work from pending and revision requested`() {
        actions(ApprovalStatus.Pending, Role.Operator) shouldBe
            setOf(ApprovalAction.Approve, ApprovalAction.Reject, ApprovalAction.RequestRevision, ApprovalAction.Comment)
        actions(ApprovalStatus.RevisionRequested, Role.Owner) shouldBe
            setOf(ApprovalAction.Approve, ApprovalAction.Reject, ApprovalAction.Comment)
    }

    @Test
    fun `FR-051 request revision is offered only from pending`() {
        ApprovalStatus.entries.filter { it != ApprovalStatus.Pending }.forEach {
            (ApprovalAction.RequestRevision in actions(it, Role.Owner)) shouldBe false
        }
    }

    @Test
    fun `FR-044 a resolved approval offers no decision action`() {
        listOf(
            ApprovalStatus.Approved,
            ApprovalStatus.Rejected,
            ApprovalStatus.Cancelled,
            ApprovalStatus.Unknown,
        ).forEach {
            actions(it, Role.Owner) shouldBe setOf(ApprovalAction.Comment)
        }
    }

    @Test
    fun `a viewer has no action at all`() {
        ApprovalStatus.entries.forEach { actions(it, Role.Viewer) shouldBe emptySet() }
    }

    @Test
    fun `statuses parse and unknown ones are safe`() {
        ApprovalStatus.fromServer("pending") shouldBe ApprovalStatus.Pending
        ApprovalStatus.fromServer("revision_requested") shouldBe ApprovalStatus.RevisionRequested
        ApprovalStatus.fromServer("approved") shouldBe ApprovalStatus.Approved
        ApprovalStatus.fromServer("rejected") shouldBe ApprovalStatus.Rejected
        ApprovalStatus.fromServer("cancelled") shouldBe ApprovalStatus.Cancelled
        ApprovalStatus.fromServer("limbo") shouldBe ApprovalStatus.Unknown
        ApprovalStatus.Pending.isResolvable shouldBe true
        ApprovalStatus.Approved.isResolvable shouldBe false
    }
}

class BudgetIncidentTest {
    private fun incident(
        observed: Long = 9_000,
        status: IncidentStatus = IncidentStatus.Open,
    ) = BudgetIncident(
        id = BudgetIncidentId("inc-1"),
        scopeName = "Engineer 1",
        scopeType = "agent",
        threshold = BudgetThreshold.Hard,
        observed = Money.usdCents(observed),
        limit = Money.usdCents(10_000),
        status = status,
    )

    @Test
    fun `FR-052 a raise must exceed the observed spend`() {
        val i = incident(observed = 9_000)
        i.validate(BudgetResolution.RaiseAndResume(Money.usdCents(9_001))) shouldBe null
        i.validate(BudgetResolution.RaiseAndResume(Money.usdCents(9_000))) shouldBe ResolutionIssue.MustExceedObserved
        i.validate(BudgetResolution.RaiseAndResume(Money.usdCents(0))) shouldBe ResolutionIssue.MustExceedObserved
    }

    @Test
    fun `a raise in another currency is refused`() {
        incident().validate(BudgetResolution.RaiseAndResume(Money(50_000, "EUR"))) shouldBe
            ResolutionIssue.WrongCurrency
    }

    @Test
    fun `keeping the budget paused needs no amount`() {
        incident().validate(BudgetResolution.KeepPaused) shouldBe null
    }

    @Test
    fun `only an open incident can be resolved`() {
        incident(status = IncidentStatus.Open).isOpen shouldBe true
        incident(status = IncidentStatus.Resolved).isOpen shouldBe false
        incident(status = IncidentStatus.Dismissed).isOpen shouldBe false
        IncidentStatus.fromServer("open") shouldBe IncidentStatus.Open
        IncidentStatus.fromServer("resolved") shouldBe IncidentStatus.Resolved
        IncidentStatus.fromServer("dismissed") shouldBe IncidentStatus.Dismissed
        IncidentStatus.fromServer("zzz") shouldBe IncidentStatus.Unknown
    }
}

class IdsTest {
    @Test
    fun `decision approval and incident ids must not be blank`() {
        DecisionId("d").value shouldBe "d"
        ApprovalId("a").value shouldBe "a"
        BudgetIncidentId("b").value shouldBe "b"
        assertFailsWith<IllegalArgumentException> { DecisionId(" ") }
        assertFailsWith<IllegalArgumentException> { ApprovalId("") }
        assertFailsWith<IllegalArgumentException> { BudgetIncidentId("") }
    }
}
