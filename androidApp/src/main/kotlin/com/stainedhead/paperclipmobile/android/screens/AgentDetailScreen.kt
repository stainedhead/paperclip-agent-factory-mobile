package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ErrorBanner
import com.stainedhead.paperclipmobile.android.designsystem.OfflineBanner
import com.stainedhead.paperclipmobile.android.designsystem.ScreenTitleBar
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.StatusPill
import com.stainedhead.paperclipmobile.android.designsystem.UpdatedLabel
import com.stainedhead.paperclipmobile.android.designsystem.rememberNowMillis
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.env.rememberAgentDetailHolder
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.android.shell.ConfirmDestructiveSheet
import com.stainedhead.paperclipmobile.android.shell.TabNavigator
import com.stainedhead.paperclipmobile.presentation.AgentDetailIntent
import com.stainedhead.paperclipmobile.presentation.AgentDetailState
import com.stainedhead.paperclipmobile.presentation.AgentRowUi
import com.stainedhead.paperclipmobile.presentation.DetailPhase

/**
 * Screen 11 (AND-051). It renders the shared holder's state and forwards intents. The offered buttons, the owner-only
 * monthly budget and the device owner on Pause are the holder's and the use case's; the screen lays them out.
 */
@Composable
fun AgentDetailScreen(
    session: GraphSession,
    route: AppRoute.Agent,
    navigator: TabNavigator,
) {
    val holder = rememberAgentDetailHolder(session, route.id)
    val state by holder.state.collectAsStateWithLifecycle()
    RefreshWhileVisible(session, holder.holder.refreshTarget)
    AgentDetailContent(
        state,
        holder::onIntent,
        onBack = navigator.pop,
        onOpenIssue = { navigator.push(AppRoute.Issue(it)) },
        onOpenRun = { navigator.push(AppRoute.Run(it)) },
    )
}

/** What Agent detail shows for a state. Stateless: tests and screenshots give it any state, including a viewer's. */
@Composable
fun AgentDetailContent(
    state: AgentDetailState,
    send: (AgentDetailIntent) -> Unit,
    onBack: () -> Unit,
    onOpenIssue: (String) -> Unit,
    onOpenRun: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showsBudget by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().imePadding().testTag("agent-detail-root")) {
        ScreenTitleBar(state.agent?.name.orEmpty(), Modifier.padding(horizontal = Spacing.page), onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.page, vertical = Spacing.roomy)
                .testTag("agent-detail-screen"),
            verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
        ) {
            val agent = state.agent
            if (state.isOffline && state.phase == DetailPhase.Content) OfflineBanner(state.updatedAtEpochMillis)
            if (state.phase == DetailPhase.Content && agent != null) {
                AgentBody(state, agent, send, onOpenIssue, onOpenRun) { showsBudget = true }
            } else {
                DetailUnavailable(state.phase, state.error, state.isOffline, { send(AgentDetailIntent.Refresh) })
            }
        }
    }
    if (state.confirmPause) {
        ConfirmDestructiveSheet(
            title = stringResource(R.string.agents_pause_title, state.agent?.name.orEmpty()),
            message = stringResource(R.string.agents_pause_message),
            confirmTitle = stringResource(R.string.agents_pause_confirm),
            onConfirm = { send(AgentDetailIntent.PauseConfirmed) },
            onCancel = { send(AgentDetailIntent.PauseCancelled) },
            isWorking = state.isWorking,
        )
    }
    if (showsBudget) BudgetSheet(state, send) { showsBudget = false }
}

@Composable
private fun AgentBody(
    state: AgentDetailState,
    agent: AgentRowUi,
    send: (AgentDetailIntent) -> Unit,
    onOpenIssue: (String) -> Unit,
    onOpenRun: (String) -> Unit,
    onSetBudget: () -> Unit,
) {
    AgentHeader(state, agent)
    state.feedback?.let { FeedbackBanner(it, { send(AgentDetailIntent.MessageDismissed) }) }
    state.error?.let { ErrorBanner(it, onRetry = { send(AgentDetailIntent.Refresh) }) }
    AgentActions(state, send)
    AgentWork(state, onOpenIssue, onOpenRun)
    AgentSpend(state, onSetBudget)
    AgentRecentRuns(state, onOpenRun)
}

/** Name and role, what it is doing in a pill, who it reports to, and how old the read is. */
@Composable
private fun AgentHeader(
    state: AgentDetailState,
    agent: AgentRowUi,
) {
    val colors = AppTheme.colors
    val now by rememberNowMillis()
    val line = agentLine(agent, now)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.hairline)) {
            Text(
                agent.name,
                Modifier.semantics { heading() }.testTag("agent-name"),
                style = AppType.title,
                color = colors.textPrimary,
            )
            Text(
                stringResource(R.string.agent_detail_role, agent.role.replaceFirstChar { it.uppercase() }),
                Modifier.testTag("agent-role"),
                style = AppType.secondary,
                color = colors.textSecondary,
            )
        }
        StatusPill(line.text, line.tone, Modifier.testTag("agent-status"))
        state.reportsToName?.let {
            Text(
                stringResource(R.string.agent_detail_reports_to, it),
                Modifier.testTag("agent-reports-to"),
                style = AppType.secondary,
                color = colors.textSecondary,
            )
        }
        UpdatedLabel(state.updatedAtEpochMillis)
    }
}
