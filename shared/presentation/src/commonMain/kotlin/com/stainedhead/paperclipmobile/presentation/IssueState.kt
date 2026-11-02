package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.ResolveCompanyContext
import com.stainedhead.paperclipmobile.application.instance.GetInstance
import com.stainedhead.paperclipmobile.application.interaction.LoadInteractions
import com.stainedhead.paperclipmobile.application.interaction.RespondToInteraction
import com.stainedhead.paperclipmobile.application.issue.AssignIssue
import com.stainedhead.paperclipmobile.application.issue.ChangeIssueStatus
import com.stainedhead.paperclipmobile.application.issue.CommentOnIssue
import com.stainedhead.paperclipmobile.application.issue.CreateIssue
import com.stainedhead.paperclipmobile.application.issue.ListIssueAttachments
import com.stainedhead.paperclipmobile.application.issue.LoadIssue
import com.stainedhead.paperclipmobile.application.issue.LoadIssuePlan
import com.stainedhead.paperclipmobile.application.run.ListIssueRuns

enum class IssueTab {
    Thread,
    Plan,
    Runs,
    Files,
}

data class IssueHeaderUi(
    val id: String,
    val identifier: String,
    val title: String,
    val summary: String?,
    val status: IssueStatusUi,
    val priority: IssuePriorityUi,
    val assigneeName: String?,
    val assigneeIsMe: Boolean,
    val projectName: String?,
    val parent: IssueRefUi?,
    val blockedBy: List<IssueRefUi>,
)

enum class CommentAuthorUi {
    User,
    Agent,
    System,
}

data class IssueCommentUi(
    val id: String,
    val body: String,
    val authorKind: CommentAuthorUi,
    val authorName: String?,
    val isMine: Boolean,
    val createdAtEpochMillis: Long,
)

enum class ThreadItemKind {
    Comment,
    Interaction,
}

/** One entry of the Thread tab: a comment or an interaction, in time order (PRD FR-061). */
data class ThreadItemUi(
    val id: String,
    val kind: ThreadItemKind,
    val comment: IssueCommentUi?,
    val interaction: InteractionUi?,
    val createdAtEpochMillis: Long,
)

data class PlanUi(
    val title: String?,
    val body: String,
    val updatedAtEpochMillis: Long?,
)

data class FileUi(
    val id: String,
    val name: String?,
    val contentType: String,
    val byteSize: Long,
    /** Path under the factory's `/api`; the platform opens it with HTTP Range. */
    val contentPath: String,
)

enum class AssigneeKindUi {
    None,
    Me,
    Agent,
}

data class AssigneeChoiceUi(
    val kind: AssigneeKindUi,
    val id: String?,
    val name: String?,
)

data class IssueState(
    val phase: DetailPhase = DetailPhase.Loading,
    val tab: IssueTab = IssueTab.Thread,
    val header: IssueHeaderUi? = null,
    val subtasks: List<IssueRowUi> = emptyList(),
    val thread: List<ThreadItemUi> = emptyList(),
    val interactionsUnavailable: Boolean = false,
    val plan: PlanUi? = null,
    val runs: List<RunRowUi> = emptyList(),
    val runsLoaded: Boolean = false,
    val files: List<FileUi> = emptyList(),
    val filesLoaded: Boolean = false,
    val tabError: ErrorUi? = null,
    val canComment: Boolean = false,
    /** The issue is closed, so a comment may reopen it. */
    val canReopen: Boolean = false,
    /** The issue is in progress, so a comment may interrupt the running agent. */
    val canInterrupt: Boolean = false,
    val canChangeStatus: Boolean = false,
    val statusOptions: List<IssueStatusUi> = emptyList(),
    val canAssign: Boolean = false,
    val assigneeChoices: List<AssigneeChoiceUi> = emptyList(),
    val canCreateSubtask: Boolean = false,
    val isBusy: Boolean = false,
    val role: RoleUi? = null,
    val isOffline: Boolean = false,
    val actionsDisabledReason: ActionsDisabledReason? = null,
    val updatedAtEpochMillis: Long? = null,
    val refreshIntervalSeconds: Int = SCREEN_REFRESH_SECONDS,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
)

sealed interface IssueIntent {
    data object Refresh : IssueIntent

    data class TabSelected(
        val tab: IssueTab,
    ) : IssueIntent

    data class CommentSubmitted(
        val body: String,
        val reopen: Boolean,
        val interrupt: Boolean,
    ) : IssueIntent

    data class StatusSelected(
        val status: IssueStatusUi,
    ) : IssueIntent

    /** [id] is the agent id for [AssigneeKindUi.Agent] and unused otherwise. */
    data class AssigneeSelected(
        val kind: AssigneeKindUi,
        val id: String?,
    ) : IssueIntent

    data class SubtaskSubmitted(
        val title: String,
    ) : IssueIntent

    data class InteractionPrimaryTapped(
        val interactionId: String,
    ) : IssueIntent

    data class InteractionRejectTapped(
        val interactionId: String,
    ) : IssueIntent

    data class InteractionSkipTapped(
        val interactionId: String,
    ) : IssueIntent

    /** [groupId] is the question id for a question, and empty for suggested tasks and checkbox options. */
    data class InteractionChoiceToggled(
        val interactionId: String,
        val groupId: String,
        val optionId: String,
    ) : IssueIntent

    /** [targetId] is a question id (free text), an item id (verdict reason) or empty (the reject reason). */
    data class InteractionTextChanged(
        val interactionId: String,
        val targetId: String,
        val text: String,
    ) : IssueIntent

    data class InteractionVerdictChosen(
        val interactionId: String,
        val itemId: String,
        val verdict: VerdictUi,
    ) : IssueIntent

    data object MessageDismissed : IssueIntent
}

data class IssueEditUseCases(
    val comment: CommentOnIssue,
    val status: ChangeIssueStatus,
    val assign: AssignIssue,
    val create: CreateIssue,
)

data class InteractionUseCases(
    val load: LoadInteractions,
    val respond: RespondToInteraction,
)

data class IssueUseCases(
    val resolveContext: ResolveCompanyContext,
    val getInstance: GetInstance,
    val load: LoadIssue,
    val plan: LoadIssuePlan,
    val runs: ListIssueRuns,
    val files: ListIssueAttachments,
    val edits: IssueEditUseCases,
    val interactions: InteractionUseCases,
)
