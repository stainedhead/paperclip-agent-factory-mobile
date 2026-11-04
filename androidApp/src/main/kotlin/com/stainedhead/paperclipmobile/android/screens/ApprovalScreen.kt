package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AdaptiveRow
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ConfirmHapticOnRise
import com.stainedhead.paperclipmobile.android.designsystem.ErrorBanner
import com.stainedhead.paperclipmobile.android.designsystem.OfflineBanner
import com.stainedhead.paperclipmobile.android.designsystem.ResultCard
import com.stainedhead.paperclipmobile.android.designsystem.ScreenTitleBar
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.StatusPill
import com.stainedhead.paperclipmobile.android.designsystem.StatusTone
import com.stainedhead.paperclipmobile.android.designsystem.UpdatedLabel
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.env.rememberApprovalHolder
import com.stainedhead.paperclipmobile.android.labels.doneStringRes
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.android.shell.TabNavigator
import com.stainedhead.paperclipmobile.presentation.ApprovalIntent
import com.stainedhead.paperclipmobile.presentation.ApprovalPayloadUi
import com.stainedhead.paperclipmobile.presentation.ApprovalState
import com.stainedhead.paperclipmobile.presentation.ApprovalStatusUi
import com.stainedhead.paperclipmobile.presentation.ApprovalTypeUi
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.FeedbackKind

/**
 * Screen 3 (AND-041). It renders `ApprovalState` and forwards intents. Which actions are offered (by status, role and
 * connectivity), when a note is required and when the device owner is asked (inside the shared reject use case) are
 * the shared holder's; the screen only lays them out.
 */
@Composable
fun ApprovalScreen(
    session: GraphSession,
    route: AppRoute.Approval,
    navigator: TabNavigator,
) {
    val holder = rememberApprovalHolder(session, route.id)
    val state by holder.state.collectAsStateWithLifecycle()
    RefreshWhileVisible(session, holder.holder.refreshTarget)
    ApprovalContent(
        state,
        holder::onIntent,
        onBack = navigator.pop,
        onOpenIssue = { navigator.push(AppRoute.Issue(it)) },
    )
}

/** What the Approval shows for a state. Stateless: tests and screenshots give it any state, including a viewer's. */
@Composable
fun ApprovalContent(
    state: ApprovalState,
    send: (ApprovalIntent) -> Unit,
    onBack: () -> Unit,
    onOpenIssue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmHapticOnRise(state.isResolved)
    Column(modifier.fillMaxSize().imePadding().testTag("approval-root")) {
        ScreenTitleBar(stringResource(R.string.approval_nav_title), Modifier.padding(horizontal = Spacing.page), onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.page, vertical = Spacing.roomy)
                .testTag("approval-screen"),
            verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
        ) {
            if (state.isOffline && state.phase == DetailPhase.Content) OfflineBanner(state.updatedAtEpochMillis)
            if (state.phase == DetailPhase.Content) {
                ApprovalBody(state, send, onOpenIssue)
            } else {
                DetailUnavailable(state.phase, state.error, state.isOffline, { send(ApprovalIntent.Refresh) })
            }
        }
        ApprovalActionBar(state, send)
    }
    ApprovalNoteSheet(state, send)
}

@Composable
private fun ApprovalBody(
    state: ApprovalState,
    send: (ApprovalIntent) -> Unit,
    onOpenIssue: (String) -> Unit,
) {
    ApprovalHeader(state)
    val feedback = state.feedback
    if (feedback != null && !(state.notePrompt != null && feedback.kind != FeedbackKind.Reconciled)) {
        FeedbackBanner(feedback, { send(ApprovalIntent.MessageDismissed) })
    }
    state.error?.let { ErrorBanner(it, onRetry = { send(ApprovalIntent.Refresh) }) }
    val status = state.status
    if (status != null && state.isResolved) {
        ResultCard(
            stringResource(status.stringRes()),
            if (status == ApprovalStatusUi.Approved) {
                StatusTone.Success
            } else {
                StatusTone.Neutral
            },
            Modifier.testTag("approval-final"),
        ) {
            Text(stringResource(status.doneStringRes()), style = AppType.body, color = AppTheme.colors.textPrimary)
            state.decisionNote?.takeIf { it.isNotEmpty() }?.let {
                val note = stringResource(R.string.approval_decision_note, it)
                Text(note, style = AppType.body, color = AppTheme.colors.textPrimary)
            }
        }
    }
    ApprovalPayloadView(state.payload)
    if (state.issues.isNotEmpty()) LinkedWork(state, onOpenIssue)
    Discussion(state, send)
}

/** The title the payload names: the approval has none of its own. */
@Composable
private fun approvalTitle(state: ApprovalState): String {
    val named =
        when (val payload = state.payload) {
            is ApprovalPayloadUi.HireAgent -> {
                (payload.name ?: payload.title)?.let { stringResource(R.string.approval_hire_title, it) }
            }

            is ApprovalPayloadUi.CeoStrategy -> {
                payload.title
            }

            is ApprovalPayloadUi.BudgetOverride -> {
                payload.scopeName
            }

            is ApprovalPayloadUi.BoardRequest -> {
                payload.title
            }

            else -> {
                null
            }
        }
    return named ?: state.type?.let { stringResource(it.stringRes()) } ?: stringResource(R.string.approval_nav_title)
}

/** The kind and type, the status pill, the title the payload names, who asked and how old the read is. */
@Composable
private fun ApprovalHeader(state: ApprovalState) {
    val colors = AppTheme.colors
    val kind = stringResource(R.string.kind_approval)
    val type = state.type?.takeIf { it != ApprovalTypeUi.Unsupported }?.let { stringResource(it.stringRes()) }
    val kindLine = listOfNotNull(kind, type).joinToString(" · ").uppercase()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        AdaptiveRow(spacing = Spacing.medium) {
            Text(kindLine, Modifier.testTag("approval-kind-line"), style = AppType.label, color = colors.warn)
            state.status?.let { StatusPill(stringResource(it.stringRes()), it.tone()) }
        }
        Text(
            approvalTitle(state),
            Modifier.semantics { heading() }.testTag("approval-title"),
            style = AppType.title,
            color = colors.textPrimary,
        )
        state.requestedByAgentName?.let {
            Text(
                stringResource(R.string.detail_requested_by, it),
                Modifier.testTag("approval-requested-by"),
                style = AppType.secondary,
                color = colors.textSecondary,
            )
        }
        UpdatedLabel(state.updatedAtEpochMillis)
    }
}
