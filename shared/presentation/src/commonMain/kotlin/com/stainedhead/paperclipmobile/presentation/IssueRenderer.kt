package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.Comment
import com.stainedhead.paperclipmobile.domain.CommentAuthorType
import com.stainedhead.paperclipmobile.domain.Issue
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.Permissions
import com.stainedhead.paperclipmobile.domain.Role

/** Holds the Issue screen's display rules (PRD FR-061, FR-062, FR-095). */
internal object IssueRenderer {
    fun render(
        model: IssueModel,
        online: Boolean,
    ): IssueState {
        val view = model.view
        val names = view?.agentNames.orEmpty()
        return IssueState(
            phase =
                when {
                    view != null -> DetailPhase.Content
                    model.loading -> DetailPhase.Loading
                    else -> DetailPhase.Failed
                },
            tab = model.tab,
            header = view?.detail?.issue?.let { header(model, it) },
            subtasks =
                view
                    ?.detail
                    ?.subtasks
                    .orEmpty()
                    .map { it.toRow(names, model.userId) },
            thread = thread(model, online),
            interactionsUnavailable = model.interactionsUnavailable,
            plan = model.plan?.let { PlanUi(it.title, it.body, it.updatedAt?.toEpochMilliseconds()) },
            runs = model.runs.orEmpty().map { it.toRow().copy(agentName = names[it.agentId]) },
            runsLoaded = model.runs != null,
            files =
                model.files.orEmpty().map {
                    FileUi(
                        it.id,
                        it.fileName,
                        it.contentType,
                        it.byteSize,
                        it.contentPath,
                    )
                },
            filesLoaded = model.files != null,
            tabError = model.tabError,
            assigneeChoices = assigneeChoices(model),
            role = model.role?.toUi(),
            updatedAtEpochMillis = model.loadedAtMillis,
            error = model.error,
            feedback = model.feedback,
        ).withCapabilities(model, online)
    }

    /** What the person may do now: by role, by connection, and while no other change is in flight. */
    private fun IssueState.withCapabilities(
        model: IssueModel,
        online: Boolean,
    ): IssueState {
        val role = model.role
        val may = { action: Action -> online && !model.busy && role != null && Permissions.allows(role, action) }
        val issue = model.view?.detail?.issue
        val canStatus = may(Action.ChangeIssueStatus)
        return copy(
            canComment = may(Action.CommentOnIssue),
            canReopen = issue != null && !issue.status.isOpen,
            canInterrupt = issue?.status == IssueStatus.InProgress,
            canChangeStatus = canStatus,
            statusOptions =
                if (canStatus && issue != null) {
                    IssueStatusUi.entries.filter { it != IssueStatusUi.Unknown && it != issue.status.toUi() }
                } else {
                    emptyList()
                },
            canAssign = may(Action.AssignIssue),
            canCreateSubtask = may(Action.CreateIssue),
            isBusy = model.busy,
            isOffline = !online,
            actionsDisabledReason =
                when {
                    !online -> ActionsDisabledReason.Offline
                    role == Role.Viewer -> ActionsDisabledReason.ViewerRole
                    else -> null
                },
        )
    }

    private fun header(
        model: IssueModel,
        issue: Issue,
    ): IssueHeaderUi {
        val detail = model.view?.detail
        return IssueHeaderUi(
            id = issue.id.value,
            identifier = issue.identifier,
            title = issue.title,
            description = issue.description,
            status = issue.status.toUi(),
            priority = issue.priority.toUi(),
            assigneeName = issue.assigneeAgentId?.let { model.view?.agentNames?.get(it) },
            assigneeIsMe = model.userId != null && issue.assigneeUserId == model.userId,
            projectName = detail?.projectName,
            parent = detail?.parent?.toUi(),
            blockedBy = detail?.blockedBy.orEmpty().map { it.toUi() },
        )
    }

    private fun assigneeChoices(model: IssueModel): List<AssigneeChoiceUi> {
        val names = model.view?.agentNames.orEmpty()
        return listOf(AssigneeChoiceUi(AssigneeKindUi.None, null, null)) +
            listOfNotNull(model.userId?.let { AssigneeChoiceUi(AssigneeKindUi.Me, it, null) }) +
            names.entries.sortedBy { it.value }.map { AssigneeChoiceUi(AssigneeKindUi.Agent, it.key.value, it.value) }
    }

    private fun thread(
        model: IssueModel,
        online: Boolean,
    ): List<ThreadItemUi> {
        val viewer = InteractionViewer(model.role, model.userId, online, model.issueUrl)
        val comments =
            model.view?.comments.orEmpty().map { comment ->
                ThreadItemUi(
                    comment.id,
                    ThreadItemKind.Comment,
                    comment.toUi(model.userId),
                    null,
                    comment.createdAt.toEpochMilliseconds(),
                )
            }
        val cards =
            model.interactions.map { interaction ->
                val id = interaction.meta.id.value
                val card = InteractionRenderer.render(interaction, model.drafts[id] ?: InteractionDraft(), viewer)
                ThreadItemUi(
                    id,
                    ThreadItemKind.Interaction,
                    null,
                    card,
                    interaction.meta.createdAt.toEpochMilliseconds(),
                )
            }
        return (comments + cards).sortedBy { it.createdAtEpochMillis }
    }
}

private fun Comment.toUi(userId: String?) =
    IssueCommentUi(
        id = id,
        body = body,
        authorKind =
            when (authorType) {
                CommentAuthorType.User -> CommentAuthorUi.User
                CommentAuthorType.Agent -> CommentAuthorUi.Agent
                CommentAuthorType.System -> CommentAuthorUi.System
            },
        authorName = authorName,
        isMine = userId != null && authorUserId == userId,
        createdAtEpochMillis = createdAt.toEpochMilliseconds(),
    )
