package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.ErrorBanner
import com.stainedhead.paperclipmobile.android.designsystem.OfflineBanner
import com.stainedhead.paperclipmobile.android.designsystem.ScreenTitleBar
import com.stainedhead.paperclipmobile.android.designsystem.SectionHeader
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.env.rememberIssueHolder
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.android.shell.AppTab
import com.stainedhead.paperclipmobile.android.shell.TabNavigator
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.IssueHeaderUi
import com.stainedhead.paperclipmobile.presentation.IssueIntent
import com.stainedhead.paperclipmobile.presentation.IssueState
import com.stainedhead.paperclipmobile.presentation.IssueTab

/**
 * Screen 4 (AND-061). It renders the shared holder's state: who may comment, change the status or the assignee, what a
 * comment may reopen or interrupt, which statuses and assignees are offered and the tabs' content are the holder's;
 * the screen lays them out and forwards intents. It refreshes while on screen through the shared policy.
 */
@Composable
fun IssueScreen(
    session: GraphSession,
    route: AppRoute.Issue,
    navigator: TabNavigator,
) {
    val holder = rememberIssueHolder(session, route.id)
    val state by holder.state.collectAsStateWithLifecycle()
    RefreshWhileVisible(session, holder.holder.refreshTarget)
    IssueContent(
        state,
        holder::onIntent,
        onOpenIssue = { navigator.push(AppRoute.Issue(it)) },
        onOpenRun = { navigator.push(AppRoute.Run(it)) },
        onBack = navigator.pop,
        onSignInAgain = { navigator.select(AppTab.Factories) },
    )
}

/** What the Issue screen shows for a state. Stateless: tests and screenshots give it any state (a viewer's too). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueContent(
    state: IssueState,
    send: (IssueIntent) -> Unit,
    onOpenIssue: (String) -> Unit,
    onOpenRun: (String) -> Unit,
    onBack: () -> Unit,
    onSignInAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showsSubtask by rememberSaveable { mutableStateOf(false) }
    val header = state.header
    val hasContent = state.phase == DetailPhase.Content && header != null
    Column(modifier.fillMaxSize().imePadding().testTag("issue-root")) {
        ScreenTitleBar(header?.identifier.orEmpty(), Modifier.padding(horizontal = Spacing.page), onBack)
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = { send(IssueIntent.Refresh) },
            modifier = Modifier.weight(1f),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.page, vertical = Spacing.roomy)
                    .testTag("issue-screen"),
                verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
            ) {
                if (state.isOffline && state.phase == DetailPhase.Content) OfflineBanner(state.updatedAtEpochMillis)
                if (hasContent && header != null) {
                    IssueBody(state, header, send, onOpenIssue, onOpenRun, onSignInAgain, showsSubtask) {
                        showsSubtask = true
                    }
                } else {
                    DetailUnavailable(
                        state.phase,
                        state.error,
                        state.isOffline,
                        onRetry = { send(IssueIntent.Refresh) },
                        onSignInAgain = onSignInAgain,
                    )
                }
            }
        }
        if (hasContent) IssueCommentBar(state, send)
    }
    if (showsSubtask) SubtaskSheet(state, send) { showsSubtask = false }
}

@Composable
private fun IssueBody(
    state: IssueState,
    header: IssueHeaderUi,
    send: (IssueIntent) -> Unit,
    onOpenIssue: (String) -> Unit,
    onOpenRun: (String) -> Unit,
    onSignInAgain: () -> Unit,
    sheetOpen: Boolean,
    onAddSubtask: () -> Unit,
) {
    IssueHeader(state, header, send, onOpenIssue)
    // While the subtask sheet is open it shows the outcome of adding a subtask itself.
    if (!sheetOpen) state.feedback?.let { FeedbackBanner(it, { send(IssueIntent.MessageDismissed) }) }
    state.error?.let { ErrorBanner(it, onRetry = { send(IssueIntent.Refresh) }, onSignInAgain = onSignInAgain) }
    if (state.subtasks.isNotEmpty()) {
        SectionHeader(stringResource(R.string.issue_subtasks))
        for (row in state.subtasks) WorkRow(row, { onOpenIssue(row.id) }, tagPrefix = "issue-subtask")
    }
    IssueTabs(state) { send(IssueIntent.TabSelected(it)) }
    state.tabError?.let { ErrorBanner(it, onRetry = { send(IssueIntent.TabSelected(state.tab)) }) }
    when (state.tab) {
        IssueTab.Thread -> {
            IssueThread(state, send)
            if (state.canCreateSubtask) {
                AppButton(
                    stringResource(R.string.issue_add_subtask),
                    onAddSubtask,
                    Modifier.testTag("issue-add-subtask"),
                    kind = ButtonKind.Secondary,
                )
            }
        }

        IssueTab.Plan -> {
            PlanTab(state)
        }

        IssueTab.Runs -> {
            RunsTab(state, onOpenRun)
        }

        IssueTab.Files -> {
            FilesTab(state)
        }
    }
}
