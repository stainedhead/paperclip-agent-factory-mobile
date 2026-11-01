package com.stainedhead.paperclipmobile.application.approval

import com.stainedhead.paperclipmobile.application.DeviceAuthGuard
import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.failed
import com.stainedhead.paperclipmobile.application.needsReread
import com.stainedhead.paperclipmobile.application.retryingRead
import com.stainedhead.paperclipmobile.application.toMutation
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.Approval
import com.stainedhead.paperclipmobile.domain.ApprovalComment
import com.stainedhead.paperclipmobile.domain.ApprovalDetail
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Permissions
import com.stainedhead.paperclipmobile.domain.port.ApprovalGateway
import com.stainedhead.paperclipmobile.domain.port.BiometricResult

/** Loads an approval with its linked issues and comments (PRD FR-050). Reads are retried (FR-096). */
class LoadApproval(
    private val gateway: ApprovalGateway,
) {
    suspend operator fun invoke(
        scope: CompanyScope,
        id: ApprovalId,
    ): DomainResult<ApprovalDetail> {
        val approval =
            when (val read = retryingRead { gateway.get(scope, id) }) {
                is DomainResult.Success -> read.value
                is DomainResult.Failure -> return read
            }
        val issues =
            when (val read = retryingRead { gateway.linkedIssues(scope, id) }) {
                is DomainResult.Success -> read.value
                is DomainResult.Failure -> return read
            }
        return when (val read = retryingRead { gateway.comments(scope, id) }) {
            is DomainResult.Success -> DomainResult.Success(ApprovalDetail(approval, issues, read.value))
            is DomainResult.Failure -> read
        }
    }
}

/** Approves from pending or revision requested (PRD FR-051). A note is optional. */
class ApproveApproval(
    private val gateway: ApprovalGateway,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        id: ApprovalId,
        status: ApprovalStatus,
        note: String?,
    ): MutationResult<Approval> {
        if (!Permissions.allows(context.role, Action.ApproveApproval)) return MutationResult.Denied
        if (!status.isResolvable) return MutationResult.Invalid(InvalidInput.WrongStatus)
        return reconcileApproval(context, id, gateway.approve(context.scope, id, note), gateway)
    }
}

/** Rejects with a required note, after device authentication (PRD FR-051, FR-014). */
class RejectApproval(
    private val gateway: ApprovalGateway,
    private val deviceAuth: DeviceAuthGuard,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        id: ApprovalId,
        status: ApprovalStatus,
        note: String?,
    ): MutationResult<Approval> {
        if (!Permissions.allows(context.role, Action.RejectApproval)) return MutationResult.Denied
        if (!status.isResolvable) return MutationResult.Invalid(InvalidInput.WrongStatus)
        if (note.isNullOrBlank()) return MutationResult.Invalid(InvalidInput.NoteRequired)
        val auth = deviceAuth(Action.RejectApproval)
        if (auth != BiometricResult.Approved) return MutationResult.NotAuthorized(auth)
        return reconcileApproval(context, id, gateway.reject(context.scope, id, note), gateway)
    }
}

/** Sends an approval back for changes, with a required note, from pending only (PRD FR-051). */
class RequestRevision(
    private val gateway: ApprovalGateway,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        id: ApprovalId,
        status: ApprovalStatus,
        note: String?,
    ): MutationResult<Approval> {
        if (!Permissions.allows(context.role, Action.RequestApprovalRevision)) return MutationResult.Denied
        if (status != ApprovalStatus.Pending) return MutationResult.Invalid(InvalidInput.WrongStatus)
        if (note.isNullOrBlank()) return MutationResult.Invalid(InvalidInput.NoteRequired)
        return reconcileApproval(
            context,
            id,
            gateway.requestRevision(context.scope, id, note),
            gateway,
        )
    }
}

/** Adds a comment to an approval (PRD FR-051). */
class CommentOnApproval(
    private val gateway: ApprovalGateway,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        id: ApprovalId,
        body: String,
    ): MutationResult<ApprovalComment> {
        if (!Permissions.allows(context.role, Action.CommentOnApproval)) return MutationResult.Denied
        if (body.isBlank()) return MutationResult.Invalid(InvalidInput.BodyRequired)
        return gateway.addComment(context.scope, id, body).toMutation()
    }
}

/**
 * An approval can be resolved elsewhere between load and tap. The server answers a second, different decision with a
 * 422 (not a 409), so `Rejected` re-reads like `Timeout` and `Conflict` do (research, approvals).
 */
private suspend fun reconcileApproval(
    context: CompanyContext,
    id: ApprovalId,
    result: DomainResult<Approval>,
    gateway: ApprovalGateway,
): MutationResult<Approval> =
    when (result) {
        is DomainResult.Success -> {
            MutationResult.Done(result.value)
        }

        is DomainResult.Failure -> {
            failed(result.error, { it.needsReread() || it is DomainError.Rejected }) { gateway.get(context.scope, id) }
        }
    }
