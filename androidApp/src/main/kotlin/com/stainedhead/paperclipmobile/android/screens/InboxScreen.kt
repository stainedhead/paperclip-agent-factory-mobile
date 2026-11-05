package com.stainedhead.paperclipmobile.android.screens

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.ChoiceChip
import com.stainedhead.paperclipmobile.android.designsystem.DisabledReason
import com.stainedhead.paperclipmobile.android.designsystem.EmptyState
import com.stainedhead.paperclipmobile.android.designsystem.ErrorBanner
import com.stainedhead.paperclipmobile.android.designsystem.LoadingState
import com.stainedhead.paperclipmobile.android.designsystem.OfflineBanner
import com.stainedhead.paperclipmobile.android.designsystem.ScreenTitleBar
import com.stainedhead.paperclipmobile.android.designsystem.SectionHeader
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.UpdatedLabel
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.labels.pluralStringRes
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.android.shell.AppSheet
import com.stainedhead.paperclipmobile.android.shell.TabNavigator
import com.stainedhead.paperclipmobile.presentation.InboxIntent
import com.stainedhead.paperclipmobile.presentation.InboxItemUi
import com.stainedhead.paperclipmobile.presentation.InboxPhase
import com.stainedhead.paperclipmobile.presentation.InboxSectionUi
import com.stainedhead.paperclipmobile.presentation.InboxState

private val UndoClearance = 72.dp
private val EmptyHeight = 320.dp

/**
 * Screen 1, the root of the Inbox tab (AND-030). It renders the session's Inbox holder and forwards intents. The
 * holder belongs to the session: it feeds the tab badge and owns the refresh session, so this screen never closes it
 * and never starts a second one. It asks for one more refresh when it comes back into view, not when it first appears.
 *
 * A budget alert opens the budget incident sheet over the screen (AND-042); which incident is open survives rotation.
 */
@Composable
fun InboxScreen(
    session: GraphSession,
    navigator: TabNavigator,
) {
    val holder by session.inbox.collectAsStateWithLifecycle()
    val current = holder
    if (current == null) {
        LoadingState(stringResource(R.string.common_loading), Modifier.testTag("inbox-loading"))
        return
    }
    val state by current.state.collectAsStateWithLifecycle()
    var budgetIncident by rememberSaveable { mutableStateOf<String?>(null) }
    var appeared by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (appeared) current.send(InboxIntent.Refresh)
        appeared = true
    }
    InboxContent(state, current::send, open = { item ->
        when (val destination = inboxDestination(item)) {
            is InboxDestination.Route -> {
                navigator.push(destination.route)
            }

            is InboxDestination.Sheet -> {
                when (val sheet = destination.sheet) {
                    is AppSheet.BudgetIncident -> budgetIncident = sheet.id
                    AppSheet.Settings -> Unit
                }
            }

            // The web door belongs to the card: it opens through WebOpener and says on the card when it could not.
            is InboxDestination.Web, InboxDestination.None -> {
                Unit
            }
        }
    })
    budgetIncident?.let { id ->
        // The incident may have been resolved in the sheet, so the Inbox reads again when it closes.
        BudgetIncidentSheet(session, id, onClose = {
            budgetIncident = null
            current.send(InboxIntent.Refresh)
        })
    }
}

/** What the Inbox shows for a state. Stateless: tests and screenshots give it any state, including a viewer's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxContent(
    state: InboxState,
    send: (InboxIntent) -> Unit,
    open: (InboxItemUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    var snoozeTarget by rememberSaveable { mutableStateOf<String?>(null) }
    // The activity is fully drawn once the Inbox shows its content: the time to full display a startup benchmark reads.
    ReportDrawnWhen { state.phase == InboxPhase.Content }
    Box(modifier.fillMaxSize().testTag("inbox-root")) {
        Column(Modifier.fillMaxSize()) {
            ScreenTitleBar(
                stringResource(R.string.tab_inbox),
                Modifier.padding(horizontal = Spacing.page),
                isLarge = true,
            )
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { send(InboxIntent.Refresh) },
                modifier = Modifier.weight(1f),
            ) { InboxList(state, send, open, onSnooze = { snoozeTarget = it }) }
        }
        if (state.undo != null) {
            UndoSnackbar({ send(InboxIntent.UndoTapped) }, Modifier.align(Alignment.BottomCenter))
        }
    }
    snoozeTarget?.let { id ->
        SnoozeSheet(
            options = state.snoozeOptions,
            onPick = {
                send(InboxIntent.SnoozeRequested(id, it))
                snoozeTarget = null
            },
            onCancel = { snoozeTarget = null },
        )
    }
    RejectNoteSheet(state, send)
}

@Composable
private fun InboxList(
    state: InboxState,
    send: (InboxIntent) -> Unit,
    open: (InboxItemUi) -> Unit,
    onSnooze: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("inbox-list"),
        contentPadding = PaddingValues(start = Spacing.page, end = Spacing.page, bottom = UndoClearance),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
    ) {
        item(key = "header") { InboxHeader(state, send) }
        when (state.phase) {
            InboxPhase.Loading -> item(key = "loading") { LoadingState(stringResource(R.string.common_loading)) }
            InboxPhase.Failed -> failure(state, send)
            InboxPhase.OfflineEmpty -> placeholder(offline = true)
            InboxPhase.Empty -> placeholder(offline = false)
            InboxPhase.Content -> content(state, send, open, onSnooze)
        }
    }
}

private fun LazyListScope.failure(
    state: InboxState,
    send: (InboxIntent) -> Unit,
) {
    if (state.error != null) return
    item(key = "retry") {
        AppButton(
            stringResource(R.string.common_retry),
            { send(InboxIntent.Refresh) },
            Modifier.testTag("inbox-retry"),
            kind = ButtonKind.Secondary,
        )
    }
}

private fun LazyListScope.placeholder(offline: Boolean) {
    item(key = "placeholder") {
        EmptyState(
            icon = painterResource(if (offline) R.drawable.ic_offline else R.drawable.ic_tab_inbox),
            title = stringResource(if (offline) R.string.inbox_offline_empty_title else R.string.inbox_empty_title),
            message =
                stringResource(
                    if (offline) R.string.inbox_offline_empty_message else R.string.inbox_empty_message,
                ),
            modifier =
                Modifier
                    .heightIn(
                        min = EmptyHeight,
                    ).testTag(if (offline) "inbox-offline-empty" else "inbox-empty"),
        )
    }
}

private fun LazyListScope.content(
    state: InboxState,
    send: (InboxIntent) -> Unit,
    open: (InboxItemUi) -> Unit,
    onSnooze: (String) -> Unit,
) {
    for (section in state.sections) {
        item(key = "section-${section.group.name}") { GroupHeader(section) }
        items(section.items, key = { it.id }) { item -> SwipeableCard(item, state, send, open, onSnooze) }
    }
    if (state.canLoadMore) {
        item(key = "load-more") {
            AppButton(
                stringResource(R.string.inbox_load_more),
                { send(InboxIntent.LoadMore) },
                Modifier.testTag("inbox-load-more"),
                kind = ButtonKind.Secondary,
                enabled = !state.isLoadingMore,
            )
        }
    }
}

@Composable
private fun GroupHeader(section: InboxSectionUi) {
    SectionHeader(
        stringResource(section.group.stringRes()),
        Modifier.testTag("inbox-section-${section.group.name}"),
    )
}

/** The count line, offline banner, why actions are off, the last action's outcome, an error and the filter chips. */
@Composable
private fun InboxHeader(
    state: InboxState,
    send: (InboxIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge)) {
        if (state.isOffline) OfflineBanner(state.updatedAtEpochMillis) else CountLine(state)
        val reason = state.actionsDisabledReason
        if (reason != null && state.phase == InboxPhase.Content) DisabledReason(reason)
        val feedback = state.feedback
        if (feedback != null && state.rejectPrompt == null) {
            FeedbackBanner(feedback, { send(InboxIntent.MessageDismissed) })
        }
        state.error?.let { ErrorBanner(it, onRetry = { send(InboxIntent.Refresh) }) }
        if (state.phase == InboxPhase.Content || state.phase == InboxPhase.Empty) FilterChips(state, send)
    }
}

@Composable
private fun CountLine(state: InboxState) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        Text(
            if (state.badgeCount > 0) {
                pluralStringResource(R.plurals.inbox_due_today, state.badgeCount, state.badgeCount)
            } else {
                stringResource(R.string.inbox_due_today_none)
            },
            Modifier.testTag("inbox-due-count"),
            style = AppType.button,
            color = if (state.badgeCount > 0) colors.destructive else colors.textSecondary,
        )
        UpdatedLabel(state.updatedAtEpochMillis)
    }
}

/** "All 9", "Decisions 3", ...: the counts are the server's, company-wide; the selected chip is announced. */
@Composable
private fun FilterChips(
    state: InboxState,
    send: (InboxIntent) -> Unit,
) {
    val filtersLabel = stringResource(R.string.inbox_filters_label)
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .semantics { contentDescription = filtersLabel }
            .testTag("inbox-filters"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        ChoiceChip(
            stringResource(R.string.inbox_all_count, state.totalCount),
            state.selectedFilter == null,
            "inbox-filter-all",
        ) { send(InboxIntent.FilterSelected(null)) }
        for (filter in state.filters) {
            ChoiceChip(
                stringResource(R.string.inbox_chip, stringResource(filter.kind.pluralStringRes()), filter.count),
                state.selectedFilter == filter.kind,
                "inbox-filter-${filter.kind.name}",
            ) { send(InboxIntent.FilterSelected(filter.kind)) }
        }
    }
}
