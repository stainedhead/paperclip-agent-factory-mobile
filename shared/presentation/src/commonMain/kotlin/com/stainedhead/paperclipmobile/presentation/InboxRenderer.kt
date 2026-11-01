package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.attention.AvailableSnoozePresets
import com.stainedhead.paperclipmobile.domain.AttentionItem
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.InboxGrouping
import com.stainedhead.paperclipmobile.domain.InlineActionPolicy
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.port.Clock

/** Turns the [InboxModel] into the state the screen draws. Holds every Inbox display rule (PRD FR-030 to FR-033). */
internal class InboxRenderer(
    private val snoozePresets: AvailableSnoozePresets,
    private val clock: Clock,
) {
    fun render(
        model: InboxModel,
        online: Boolean,
    ): InboxState {
        val role = model.context?.role
        val visible = model.items.filter { it.id !in model.hidden }
        val shown = visible.filter { model.filter == null || it.sourceKind.toUi() == model.filter }
        return InboxState(
            phase = phase(model, online, visible),
            sections =
                InboxGrouping.group(shown, clock.now()).map { section ->
                    InboxSectionUi(section.group.toUi(), section.items.map { item(it, model, role) })
                },
            filters = chips(model.counts),
            selectedFilter = model.filter,
            badgeCount = model.badge,
            totalCount = model.total,
            updatedAtEpochMillis = model.generatedAt?.toEpochMilliseconds(),
            isFromCache = model.fromCache,
            isRefreshing = model.refreshing,
            isLoadingMore = model.loadingMore,
            canLoadMore = model.nextCursor != null && online,
            isOffline = !online,
            role = role?.toUi(),
            actionsDisabledReason = disabledReason(online, role),
            isMutating = model.mutating,
            snoozeOptions = snoozePresets().map { it.toUi() },
            undo = model.undo,
            rejectPrompt = model.rejectPrompt,
            error = model.error,
            feedback = model.feedback,
        )
    }

    private fun phase(
        model: InboxModel,
        online: Boolean,
        visible: List<AttentionItem>,
    ): InboxPhase =
        when {
            model.hasFeed -> if (visible.isEmpty()) InboxPhase.Empty else InboxPhase.Content
            !online -> InboxPhase.OfflineEmpty
            model.error != null -> InboxPhase.Failed
            else -> InboxPhase.Loading
        }

    private fun disabledReason(
        online: Boolean,
        role: Role?,
    ): ActionsDisabledReason? =
        when {
            !online -> ActionsDisabledReason.Offline
            role == Role.Viewer -> ActionsDisabledReason.ViewerRole
            else -> null
        }

    /** One chip per source kind the server counted; kinds this app does not know share one chip. */
    private fun chips(counts: Map<AttentionSourceKind, Int>): List<FilterChipUi> =
        counts.entries
            .filter { it.value > 0 }
            .groupBy({ it.key.toUi() }, { it.value })
            .map { FilterChipUi(it.key, it.value.sum()) }
            .sortedBy { it.kind.ordinal }

    private fun item(
        item: AttentionItem,
        model: InboxModel,
        role: Role?,
    ): InboxItemUi {
        val userId = model.context?.userId
        return InboxItemUi(
            id = item.id,
            kind = item.sourceKind.toUi(),
            rawKind = (item.sourceKind as? AttentionSourceKind.Unsupported)?.raw,
            severity = item.severity.toUi(),
            title = item.subject.title,
            identifier = item.subject.identifier,
            whyNow = item.whyNow,
            originAgentName = item.originAgentName,
            activityAtEpochMillis = item.activityAt.toEpochMilliseconds(),
            decideBy = item.decideBy.toUi(),
            inlineActions =
                role
                    ?.let {
                        InlineActionPolicy.actionsFor(item, it).map { action ->
                            action.toUi()
                        }
                    }.orEmpty(),
            open = InlineActionPolicy.destinationOf(item).toUi(),
            webUrl = webUrl(model.baseUrl, item.subject),
            isReadOnly = role == null || InlineActionPolicy.isReadOnly(item, role, userId),
            audience = item.resolverAudience?.toUi(userId),
        )
    }
}
