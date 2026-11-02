package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.Issue
import com.stainedhead.paperclipmobile.domain.NewIssue

/** The edits of the Issue screen: comment, status, assignee, subtask (PRD FR-062). */
internal class IssueEdits(
    private val session: IssueSession,
    private val loader: IssueLoader,
) {
    fun handle(intent: IssueIntent) {
        when (intent) {
            is IssueIntent.CommentSubmitted -> comment(intent)
            is IssueIntent.StatusSelected -> status(intent.status)
            is IssueIntent.AssigneeSelected -> assign(intent)
            is IssueIntent.SubtaskSubmitted -> subtask(intent.title)
            else -> Unit
        }
    }

    private fun issue(): Issue? =
        session.model.value.view
            ?.detail
            ?.issue

    private fun comment(intent: IssueIntent.CommentSubmitted) {
        if (!session.may(Action.CommentOnIssue)) return
        session.change(
            {
                mutate(session.context) {
                    session.useCases.edits.comment(
                        it,
                        session.id,
                        intent.body,
                        CommentOptions(intent.reopen, intent.interrupt),
                    )
                }
            },
            { model, _ -> model },
            loader::load,
        )
    }

    private fun status(status: IssueStatusUi) {
        val issue = issue() ?: return
        if (!session.may(Action.ChangeIssueStatus)) return
        session.change(
            { mutate(session.context) { session.useCases.edits.status(it, issue, status.toDomain()) } },
            { model, updated -> model.withIssue(updated) },
        )
    }

    private fun assign(intent: IssueIntent.AssigneeSelected) {
        val issue = issue() ?: return
        val assignee = assigneeOf(intent) ?: return
        if (!session.may(Action.AssignIssue)) return
        session.change(
            { mutate(session.context) { session.useCases.edits.assign(it, issue, assignee) } },
            { model, updated -> model.withIssue(updated) },
        )
    }

    private fun assigneeOf(intent: IssueIntent.AssigneeSelected): Assignee? =
        when (intent.kind) {
            AssigneeKindUi.None -> {
                Assignee.None
            }

            AssigneeKindUi.Me -> {
                session.model.value.userId
                    ?.let { Assignee.UserAssignee(it) }
            }

            AssigneeKindUi.Agent -> {
                intent.id?.let { Assignee.AgentAssignee(AgentId(it)) }
            }
        }

    private fun subtask(title: String) {
        if (!session.may(Action.CreateIssue)) return
        session.change(
            { mutate(session.context) { session.useCases.edits.create(it, NewIssue(title, parentId = session.id)) } },
            { model, _ -> model },
            loader::load,
        )
    }
}

private fun IssueModel.withIssue(updated: Issue) = copy(view = view?.copy(detail = view.detail.copy(issue = updated)))
