package com.stainedhead.paperclipmobile.application.issue

import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.failed
import com.stainedhead.paperclipmobile.application.toMutation
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.Comment
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Issue
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.NewIssue
import com.stainedhead.paperclipmobile.domain.Permissions
import com.stainedhead.paperclipmobile.domain.port.IssueGateway

private suspend fun IssueGateway.readIssue(
    context: CompanyContext,
    id: IssueId,
): DomainResult<Issue> =
    when (val read = get(context.scope, id)) {
        is DomainResult.Failure -> read
        is DomainResult.Success -> DomainResult.Success(read.value.issue)
    }

/**
 * Comments on an issue, with optional reopen or interrupt (PRD FR-062). Order (ADR-019): permission, text, ONE gateway
 * call. After a timeout or conflict the thread is read once; [MutationResult.Reconciled.current] is then its newest
 * comment and the screen reloads the thread.
 */
class CommentOnIssue(
    private val issues: IssueGateway,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        id: IssueId,
        body: String,
        options: CommentOptions,
    ): MutationResult<Comment> {
        if (!Permissions.allows(context.role, Action.CommentOnIssue)) return MutationResult.Denied
        val text = body.trim()
        if (text.isEmpty()) return MutationResult.Invalid(InvalidInput.BodyRequired)
        return when (val result = issues.addComment(context.scope, id, text, options)) {
            is DomainResult.Success -> {
                MutationResult.Done(result.value)
            }

            is DomainResult.Failure -> {
                failed(result.error) {
                    when (val thread = issues.comments(context.scope, id)) {
                        is DomainResult.Failure -> {
                            thread
                        }

                        is DomainResult.Success -> {
                            thread.value
                                .sortedBy { it.createdAt }
                                .lastOrNull()
                                ?.let { DomainResult.Success(it) }
                                ?: DomainResult.Failure(DomainError.NotFound)
                        }
                    }
                }
            }
        }
    }
}

/** Changes an issue's status (PRD FR-062). Same status or an unknown one is refused before any call. */
class ChangeIssueStatus(
    private val issues: IssueGateway,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        issue: Issue,
        status: IssueStatus,
    ): MutationResult<Issue> {
        if (!Permissions.allows(context.role, Action.ChangeIssueStatus)) return MutationResult.Denied
        if (status == issue.status ||
            status == IssueStatus.Unknown
        ) {
            return MutationResult.Invalid(InvalidInput.WrongStatus)
        }
        return when (val result = issues.setStatus(context.scope, issue.id, status)) {
            is DomainResult.Success -> MutationResult.Done(result.value)
            is DomainResult.Failure -> failed(result.error) { issues.readIssue(context, issue.id) }
        }
    }
}

/** Assigns an issue to an agent or a person, or clears the assignee (PRD FR-062). */
class AssignIssue(
    private val issues: IssueGateway,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        issue: Issue,
        assignee: Assignee,
    ): MutationResult<Issue> {
        if (!Permissions.allows(context.role, Action.AssignIssue)) return MutationResult.Denied
        return when (val result = issues.assign(context.scope, issue.id, assignee)) {
            is DomainResult.Success -> MutationResult.Done(result.value)
            is DomainResult.Failure -> failed(result.error) { issues.readIssue(context, issue.id) }
        }
    }
}

/**
 * Creates an issue or, with a parent, a subtask (PRD FR-062). A create has no entity to read again, so a timeout is
 * reported as failed and the Work list refreshes (it is the re-read).
 */
class CreateIssue(
    private val issues: IssueGateway,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        issue: NewIssue,
    ): MutationResult<Issue> {
        if (!Permissions.allows(context.role, Action.CreateIssue)) return MutationResult.Denied
        val title = issue.title.trim()
        if (title.isEmpty()) return MutationResult.Invalid(InvalidInput.TitleRequired)
        return issues.create(context.scope, issue.copy(title = title)).toMutation()
    }
}
