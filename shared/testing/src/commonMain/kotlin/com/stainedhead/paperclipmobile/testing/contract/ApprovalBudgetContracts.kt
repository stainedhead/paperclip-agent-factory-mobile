package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.BudgetResolution
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.IncidentStatus
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.port.ApprovalGateway
import com.stainedhead.paperclipmobile.domain.port.BudgetGateway

/**
 * The approval state machine every [ApprovalGateway] must have (research, approvals: `routes/approvals.ts`,
 * `lib/approval-records.ts:7,32-39`). The gateway holds at least four pending approvals.
 */
abstract class ApprovalContract {
    abstract fun create(): ApprovalGateway

    abstract val scope: CompanyScope
    abstract val pendingIds: List<ApprovalId>

    suspend fun runAll() {
        anUnknownApprovalIsNotFound()
        approveIsIdempotentButCannotFollowAReject()
        requestRevisionWorksOnlyFromPendingAndApproveFollowsIt()
        commentsAppendAndListedIssuesAreReadable()
    }

    private suspend fun anUnknownApprovalIsNotFound() {
        create().get(scope, ApprovalId("no-such-approval")).mustFailWith(DomainError.NotFound, "an unknown approval")
    }

    private suspend fun approveIsIdempotentButCannotFollowAReject() {
        val gateway = create()
        val approved = gateway.approve(scope, pendingIds[0], "ok").mustSucceed("approve")
        check(approved.status == ApprovalStatus.Approved) { "approve gives approved" }
        gateway.approve(scope, pendingIds[0], null).mustSucceed("approving an approved approval again")
        gateway.reject(scope, pendingIds[0], "late").mustBeRejected("rejecting an approved approval")
        val rejected = gateway.reject(scope, pendingIds[1], "no").mustSucceed("reject")
        check(rejected.status == ApprovalStatus.Rejected) { "reject gives rejected" }
        gateway.approve(scope, pendingIds[1], null).mustBeRejected("approving a rejected approval")
    }

    private suspend fun requestRevisionWorksOnlyFromPendingAndApproveFollowsIt() {
        val gateway = create()
        val id = pendingIds[2]
        val revising = gateway.requestRevision(scope, id, "needs a budget").mustSucceed("request revision")
        check(revising.status == ApprovalStatus.RevisionRequested) { "request revision gives revision requested" }
        gateway.requestRevision(scope, id, "again").mustBeRejected("a second request for revision")
        val approved = gateway.approve(scope, id, null).mustSucceed("approve after a revision request")
        check(approved.status == ApprovalStatus.Approved) { "approve works from revision requested" }
    }

    private companion object {
        const val COMMENT_INDEX = 3
    }

    private suspend fun commentsAppendAndListedIssuesAreReadable() {
        val gateway = create()
        val id = pendingIds[COMMENT_INDEX]
        val before = gateway.comments(scope, id).mustSucceed("comments").size
        gateway.addComment(scope, id, "Looks right.").mustSucceed("add a comment")
        check(gateway.comments(scope, id).mustSucceed("comments").size == before + 1) { "a comment is appended" }
        gateway.linkedIssues(scope, id).mustSucceed("linked issues")
    }
}

/**
 * The budget incident rules every [BudgetGateway] must have (research Q5: `routes/costs.ts:366-377`,
 * `svc/budgets.ts:975-1049`). The gateway holds two open incidents; [observed] is the spend of both.
 */
abstract class BudgetContract {
    abstract fun create(): BudgetGateway

    abstract val scope: CompanyScope
    abstract val openIds: List<BudgetIncidentId>
    abstract val observed: Money

    suspend fun runAll() {
        anUnknownIncidentIsNotFound()
        aRaiseMustExceedTheObservedSpend()
        resolvedAndDismissedIncidentsAreNoLongerActive()
    }

    private suspend fun anUnknownIncidentIsNotFound() {
        create()
            .incident(
                scope,
                BudgetIncidentId("no-such-incident"),
            ).mustFailWith(DomainError.NotFound, "an unknown incident")
    }

    private suspend fun aRaiseMustExceedTheObservedSpend() {
        val gateway = create()
        val id = openIds[0]
        check(
            gateway.incident(scope, id).mustSucceed("incident").status == IncidentStatus.Open,
        ) { "an incident starts open" }
        gateway
            .resolve(
                scope,
                id,
                BudgetResolution.RaiseAndResume(observed),
                null,
            ).mustBeRejected("a raise that does not exceed spend")
    }

    private suspend fun resolvedAndDismissedIncidentsAreNoLongerActive() {
        val gateway = create()
        val raised = Money(observed.minorUnits + 1, observed.currency)
        val resolved =
            gateway
                .resolve(
                    scope,
                    openIds[0],
                    BudgetResolution.RaiseAndResume(raised),
                    "raise",
                ).mustSucceed("raise")
        check(resolved.status == IncidentStatus.Resolved) { "a raise resolves the incident" }
        val kept = gateway.resolve(scope, openIds[1], BudgetResolution.KeepPaused, null).mustSucceed("keep paused")
        check(kept.status == IncidentStatus.Dismissed) { "keeping paused dismisses the incident" }
        gateway.incident(scope, openIds[0]).mustFailWith(DomainError.NotFound, "reading a resolved incident")
        gateway.incident(scope, openIds[1]).mustFailWith(DomainError.NotFound, "reading a dismissed incident")
        gateway
            .resolve(
                scope,
                openIds[1],
                BudgetResolution.KeepPaused,
                null,
            ).mustFailWith(DomainError.NotFound, "resolving twice")
    }
}
