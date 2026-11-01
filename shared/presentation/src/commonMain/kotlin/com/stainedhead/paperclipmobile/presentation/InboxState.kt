package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.ResolveCompanyContext
import com.stainedhead.paperclipmobile.application.approval.ApproveApproval
import com.stainedhead.paperclipmobile.application.approval.RejectApproval
import com.stainedhead.paperclipmobile.application.attention.AvailableSnoozePresets
import com.stainedhead.paperclipmobile.application.attention.DismissItem
import com.stainedhead.paperclipmobile.application.attention.LoadInbox
import com.stainedhead.paperclipmobile.application.attention.LoadMoreInbox
import com.stainedhead.paperclipmobile.application.attention.ReadCachedInbox
import com.stainedhead.paperclipmobile.application.attention.RefreshInbox
import com.stainedhead.paperclipmobile.application.attention.SetDecideBy
import com.stainedhead.paperclipmobile.application.attention.SnoozeItem
import com.stainedhead.paperclipmobile.application.attention.UndoDismiss
import com.stainedhead.paperclipmobile.application.budget.ResolveBudgetIncident
import com.stainedhead.paperclipmobile.application.instance.GetInstance

enum class InboxPhase {
    Loading,
    Content,
    Empty,

    /** Offline and nothing cached: explain, do not show a blank screen (PRD FR-095). */
    OfflineEmpty,
    Failed,
}

/** One filter chip; [count] is the server's company-wide count for the kind (PRD FR-031). */
data class FilterChipUi(
    val kind: SourceKindUi,
    val count: Int,
)

/** [rawKind] is the server's own kind name for [SourceKindUi.Unsupported] cards. */
data class InboxItemUi(
    val id: String,
    val kind: SourceKindUi,
    val rawKind: String?,
    val severity: SeverityUi,
    val title: String?,
    val identifier: String?,
    val whyNow: String,
    val originAgentName: String?,
    val activityAtEpochMillis: Long,
    val decideBy: DecideByUi,
    val inlineActions: List<InlineActionUi>,
    val open: OpenTargetUi,
    val webUrl: String?,
    val isReadOnly: Boolean,
    val audience: AudienceUi?,
)

data class InboxSectionUi(
    val group: InboxGroupUi,
    val items: List<InboxItemUi>,
)

/** Why action buttons are disabled. The platform UI words it (PRD FR-095). */
enum class ActionsDisabledReason {
    Offline,
    ViewerRole,
}

/** The five-second undo offer after a dismiss or snooze (PRD FR-034). */
data class UndoUi(
    val itemId: String,
    val expiresAtEpochMillis: Long,
)

/** The note prompt shown before a reject (PRD FR-051). */
data class RejectPromptUi(
    val itemId: String,
)

data class InboxState(
    val phase: InboxPhase = InboxPhase.Loading,
    val sections: List<InboxSectionUi> = emptyList(),
    val filters: List<FilterChipUi> = emptyList(),
    val selectedFilter: SourceKindUi? = null,
    /** The server's `deskBadgeCount` (PRD FR-032). The app never computes it. */
    val badgeCount: Int = 0,
    val totalCount: Int = 0,
    val updatedAtEpochMillis: Long? = null,
    val isFromCache: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val isOffline: Boolean = false,
    val role: RoleUi? = null,
    val actionsDisabledReason: ActionsDisabledReason? = null,
    val isMutating: Boolean = false,
    val snoozeOptions: List<SnoozePresetUi> = emptyList(),
    val undo: UndoUi? = null,
    val rejectPrompt: RejectPromptUi? = null,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
)

sealed interface InboxIntent {
    /** Pull to refresh, and refresh on foreground (PRD FR-035). */
    data object Refresh : InboxIntent

    data object LoadMore : InboxIntent

    /** Null clears the filter. */
    data class FilterSelected(
        val kind: SourceKindUi?,
    ) : InboxIntent

    data class SnoozeRequested(
        val itemId: String,
        val preset: SnoozePresetUi,
    ) : InboxIntent

    data class DismissTapped(
        val itemId: String,
    ) : InboxIntent

    data object UndoTapped : InboxIntent

    /** [isoDate] is `YYYY-MM-DD` and used only with [DecideByKindUi.OnDate]. */
    data class DecideBySet(
        val itemId: String,
        val kind: DecideByKindUi,
        val isoDate: String?,
    ) : InboxIntent

    data class ApproveTapped(
        val itemId: String,
    ) : InboxIntent

    data class RejectTapped(
        val itemId: String,
    ) : InboxIntent

    data class RejectConfirmed(
        val note: String,
    ) : InboxIntent

    data object RejectCancelled : InboxIntent

    data class KeepPausedTapped(
        val itemId: String,
    ) : InboxIntent

    data object MessageDismissed : InboxIntent
}

data class InboxUseCases(
    val resolveContext: ResolveCompanyContext,
    val getInstance: GetInstance,
    val refresh: RefreshInbox,
    val load: LoadInbox,
    val readCache: ReadCachedInbox,
    val loadMore: LoadMoreInbox,
    val snooze: SnoozeItem,
    val dismiss: DismissItem,
    val undo: UndoDismiss,
    val setDecideBy: SetDecideBy,
    val approve: ApproveApproval,
    val reject: RejectApproval,
    val resolveBudget: ResolveBudgetIncident,
    val snoozePresets: AvailableSnoozePresets,
)
