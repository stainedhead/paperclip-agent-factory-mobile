package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.CardSurface
import com.stainedhead.paperclipmobile.android.designsystem.ErrorBanner
import com.stainedhead.paperclipmobile.android.designsystem.OfflineBanner
import com.stainedhead.paperclipmobile.android.designsystem.ScreenTitleBar
import com.stainedhead.paperclipmobile.android.designsystem.SectionHeader
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.StatusPill
import com.stainedhead.paperclipmobile.android.designsystem.UpdatedLabel
import com.stainedhead.paperclipmobile.android.designsystem.rememberNowMillis
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.env.rememberRunHolder
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.android.shell.ConfirmDestructiveSheet
import com.stainedhead.paperclipmobile.android.shell.TabNavigator
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.RunEventUi
import com.stainedhead.paperclipmobile.presentation.RunRowUi
import com.stainedhead.paperclipmobile.presentation.RunViewerIntent
import com.stainedhead.paperclipmobile.presentation.RunViewerState

private const val MILLIS_PER_SECOND = 1_000L
private const val LIVE_TICK_MILLIS = 1_000L
private val OffsetWidth = 44.dp

/**
 * Screen 7 (AND-052). It renders the shared holder's state: the events, the log tail (read from a byte offset by the
 * holder, never again from the start), whether the run can be cancelled, the confirmation and the device owner on
 * Cancel (inside the use case), and the five second refresh while the run is live (the shared policy, through
 * `RefreshWhileVisible`). The screen lays them out and holds no timer.
 */
@Composable
fun RunViewerScreen(
    session: GraphSession,
    route: AppRoute.Run,
    navigator: TabNavigator,
) {
    val holder = rememberRunHolder(session, route.id)
    val state by holder.state.collectAsStateWithLifecycle()
    RefreshWhileVisible(session, holder.holder.refreshTarget)
    RunViewerContent(
        state,
        holder::onIntent,
        onBack = navigator.pop,
        onOpenIssue = { navigator.push(AppRoute.Issue(it)) },
    )
}

/** What the Run viewer shows for a state. Stateless: tests and screenshots give it any state, including a viewer's. */
@Composable
fun RunViewerContent(
    state: RunViewerState,
    send: (RunViewerIntent) -> Unit,
    onBack: () -> Unit,
    onOpenIssue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().imePadding().testTag("run-root")) {
        ScreenTitleBar(stringResource(R.string.run_nav_title), Modifier.padding(horizontal = Spacing.page), onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.page, vertical = Spacing.roomy)
                .testTag("run-screen"),
            verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
        ) {
            val run = state.run
            if (state.isOffline && state.phase == DetailPhase.Content) OfflineBanner(state.updatedAtEpochMillis)
            if (state.phase == DetailPhase.Content && run != null) {
                RunBody(state, run, send, onOpenIssue)
            } else {
                DetailUnavailable(state.phase, state.error, state.isOffline, { send(RunViewerIntent.Refresh) })
            }
        }
        CancelBar(state, send)
    }
    if (state.confirmCancel) {
        ConfirmDestructiveSheet(
            title = stringResource(R.string.run_cancel_title),
            message = stringResource(R.string.run_cancel_message),
            confirmTitle = stringResource(R.string.run_cancel_run),
            onConfirm = { send(RunViewerIntent.CancelConfirmed) },
            onCancel = { send(RunViewerIntent.CancelDismissed) },
            isWorking = state.isCancelling,
        )
    }
}

@Composable
private fun RunBody(
    state: RunViewerState,
    run: RunRowUi,
    send: (RunViewerIntent) -> Unit,
    onOpenIssue: (String) -> Unit,
) {
    RunHeader(state, run, onOpenIssue)
    state.feedback?.let { FeedbackBanner(it, { send(RunViewerIntent.MessageDismissed) }) }
    state.error?.let { ErrorBanner(it, onRetry = { send(RunViewerIntent.Refresh) }) }
    RunEvents(state, run)
    LogTail(state, send)
}

/** The status with how long the run has gone, who ran it, the issue it is for, and how old the read is. */
@Composable
private fun RunHeader(
    state: RunViewerState,
    run: RunRowUi,
    onOpenIssue: (String) -> Unit,
) {
    val colors = AppTheme.colors
    val now by rememberNowMillis(if (state.isLive) LIVE_TICK_MILLIS else AGE_TICK_MILLIS)
    val status = stringResource(run.status.stringRes())
    val elapsed =
        run.startedAtEpochMillis?.let {
            RunTime.elapsed(((run.finishedAtEpochMillis ?: now) - it) / MILLIS_PER_SECOND)
        }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        StatusPill(
            if (elapsed != null) stringResource(R.string.run_status_line, status, elapsed) else status,
            run.status.tone(),
            Modifier.testTag("run-status"),
        )
        Text(
            run.agentName?.let { stringResource(R.string.run_heartbeat, it) } ?: runTitle(run),
            Modifier.semantics { heading() }.testTag("run-title"),
            style = AppType.title,
            color = colors.textPrimary,
        )
        val line = listOfNotNull(run.issueIdentifier, run.issueTitle).joinToString(" · ")
        val issueId = run.issueId
        if (line.isNotEmpty() && issueId != null) {
            Text(
                line,
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.minimumTarget)
                    .clickable(onClickLabel = stringResource(R.string.run_open_issue)) { onOpenIssue(issueId) }
                    .padding(vertical = Spacing.medium)
                    .testTag("run-issue"),
                style = AppType.body,
                color = colors.accent,
            )
        }
        UpdatedLabel(state.updatedAtEpochMillis)
    }
}

private const val AGE_TICK_MILLIS = 15_000L

/** What happened and when, as the offset from the start of the run. */
@Composable
private fun RunEvents(
    state: RunViewerState,
    run: RunRowUi,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        SectionHeader(stringResource(R.string.run_events))
        if (state.events.isEmpty()) {
            CardSurface(Modifier.testTag("run-no-events")) {
                Text(
                    stringResource(R.string.run_no_events),
                    style = AppType.body,
                    color = AppTheme.colors.textSecondary,
                )
            }
        } else {
            CardSurface(Modifier.testTag("run-events")) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge)) {
                    for (event in state.events) EventRow(event, run.startedAtEpochMillis)
                }
            }
        }
    }
}

@Composable
private fun EventRow(
    event: RunEventUi,
    startedAt: Long?,
) {
    val colors = AppTheme.colors
    val offset = startedAt?.let { RunTime.offset((event.createdAtEpochMillis - it) / MILLIS_PER_SECOND) }.orEmpty()
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("run-event-${event.seq}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            offset,
            Modifier.defaultMinSize(minWidth = OffsetWidth),
            style = AppType.secondary.copy(fontFamily = FontFamily.Monospace),
            color = colors.textSecondary,
        )
        Text(event.message ?: event.type, style = AppType.body, color = colors.textPrimary)
    }
}
