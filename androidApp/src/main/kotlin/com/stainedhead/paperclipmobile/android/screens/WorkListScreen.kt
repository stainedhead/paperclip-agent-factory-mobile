package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.DisabledReason
import com.stainedhead.paperclipmobile.android.designsystem.ErrorBanner
import com.stainedhead.paperclipmobile.android.designsystem.LoadingState
import com.stainedhead.paperclipmobile.android.designsystem.OfflineBanner
import com.stainedhead.paperclipmobile.android.designsystem.ScreenTitleBar
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.env.rememberSelection
import com.stainedhead.paperclipmobile.android.env.rememberWorkHolder
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.android.shell.AppTab
import com.stainedhead.paperclipmobile.android.shell.TabNavigator
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.WorkListIntent
import com.stainedhead.paperclipmobile.presentation.WorkListState

/**
 * Screen 10, the root of the Work tab (AND-060). It renders the shared holder's state: the saved filters, the search,
 * the rows and who may create an issue are the holder's; the screen lays them out and opens an issue from a row. It
 * refreshes while on screen through the shared policy and keeps no timer of its own.
 */
@Composable
fun WorkListScreen(
    session: GraphSession,
    navigator: TabNavigator,
) {
    val selection = rememberSelection(session)
    if (selection == null) {
        LoadingState(stringResource(R.string.common_loading), Modifier.testTag("work-loading"))
        return
    }
    val holder = rememberWorkHolder(session, selection)
    val state by holder.state.collectAsStateWithLifecycle()
    RefreshWhileVisible(session, holder.holder.refreshTarget)
    WorkListContent(
        state,
        holder::onIntent,
        onOpenIssue = { navigator.push(AppRoute.Issue(it)) },
        onSignInAgain = { navigator.select(AppTab.Factories) },
    )
}

/** What the Work list shows for a state. Stateless: tests and screenshots give it any state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkListContent(
    state: WorkListState,
    send: (WorkListIntent) -> Unit,
    onOpenIssue: (String) -> Unit,
    onSignInAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showsCreate by rememberSaveable { mutableStateOf(false) }
    // The new issue opens once; the holder is told it was seen so a rotation does not open it again.
    LaunchedEffect(state.createdIssueId) {
        val created = state.createdIssueId ?: return@LaunchedEffect
        send(WorkListIntent.CreatedAcknowledged)
        showsCreate = false
        onOpenIssue(created)
    }
    Column(modifier.fillMaxSize().testTag("work-root")) {
        ScreenTitleBar(
            stringResource(R.string.tab_work),
            Modifier.padding(horizontal = Spacing.page),
            isLarge = true,
        )
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = { send(WorkListIntent.Refresh) },
            modifier = Modifier.weight(1f),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.page, vertical = Spacing.roomy)
                    .testTag("work-screen"),
                verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
            ) {
                if (state.phase == DetailPhase.Content) {
                    WorkBody(state, send, onOpenIssue, onSignInAgain, onNewIssue = { showsCreate = true }, showsCreate)
                } else {
                    DetailUnavailable(
                        state.phase,
                        state.error,
                        state.isOffline,
                        onRetry = { send(WorkListIntent.Refresh) },
                        onSignInAgain = onSignInAgain,
                    )
                }
            }
        }
    }
    if (showsCreate) CreateIssueSheet(state, send) { showsCreate = false }
}

@Composable
private fun WorkBody(
    state: WorkListState,
    send: (WorkListIntent) -> Unit,
    onOpenIssue: (String) -> Unit,
    onSignInAgain: () -> Unit,
    onNewIssue: () -> Unit,
    sheetOpen: Boolean,
) {
    if (state.isOffline) OfflineBanner(state.updatedAtEpochMillis)
    state.actionsDisabledReason?.let { DisabledReason(it) }
    // While the sheet is open it shows the outcome of a create itself.
    if (!sheetOpen) state.feedback?.let { FeedbackBanner(it, { send(WorkListIntent.MessageDismissed) }) }
    state.error?.let {
        ErrorBanner(it, onRetry = { send(WorkListIntent.Refresh) }, onSignInAgain = onSignInAgain)
    }
    WorkSearchField(state.search) { send(WorkListIntent.SearchChanged(it)) }
    FilterChips(state.filter) { send(WorkListIntent.FilterSelected(it)) }
    CountRow(state, onNewIssue)
    if (state.rows.isEmpty()) {
        WorkEmpty(state.search.isNotEmpty())
    } else {
        for (row in state.rows) WorkRow(row, onOpen = { onOpenIssue(row.id) })
        if (state.hasMore) LoadMoreRow { send(WorkListIntent.LoadMore) }
    }
}
