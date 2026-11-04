package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
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
import com.stainedhead.paperclipmobile.android.env.rememberAgentsHolder
import com.stainedhead.paperclipmobile.android.env.rememberSelection
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.android.shell.AppTab
import com.stainedhead.paperclipmobile.android.shell.ConfirmDestructiveSheet
import com.stainedhead.paperclipmobile.android.shell.TabNavigator
import com.stainedhead.paperclipmobile.presentation.AgentActionUi
import com.stainedhead.paperclipmobile.presentation.AgentGroupUi
import com.stainedhead.paperclipmobile.presentation.AgentRowUi
import com.stainedhead.paperclipmobile.presentation.AgentSectionUi
import com.stainedhead.paperclipmobile.presentation.AgentsIntent
import com.stainedhead.paperclipmobile.presentation.AgentsState
import com.stainedhead.paperclipmobile.presentation.DetailPhase

private val EmptyHeight = 280.dp

/** Which groups the chips show. A view of the list on this phone, not a rule: every row the holder gave is kept. */
private enum class AgentFilter {
    All,
    Running,
    Attention,
    ;

    fun includes(group: AgentGroupUi) =
        when (this) {
            All -> true
            Running -> group == AgentGroupUi.Running
            Attention -> group == AgentGroupUi.NeedsAttention
        }
}

/**
 * Screen 6, the root of the Agents tab (AND-051). It renders the shared holder's groups and buttons and forwards
 * intents. Which buttons a row offers (by status, role and connection), that Pause asks first and then the device
 * owner (inside the shared use case), and that a budget-paused agent cannot simply be resumed are the holder's.
 */
@Composable
fun AgentsScreen(
    session: GraphSession,
    navigator: TabNavigator,
) {
    val selection = rememberSelection(session)
    if (selection == null) {
        LoadingState(stringResource(R.string.common_loading), Modifier.testTag("agents-loading"))
        return
    }
    val holder = rememberAgentsHolder(session, selection)
    val state by holder.state.collectAsStateWithLifecycle()
    RefreshWhileVisible(session, holder.holder.refreshTarget)
    AgentsContent(
        state,
        holder::onIntent,
        onOpenAgent = { navigator.push(AppRoute.Agent(it)) },
        onOpenRun = { navigator.push(AppRoute.Run(it)) },
        onSignInAgain = { navigator.select(AppTab.Factories) },
    )
}

/** What the Agents tab shows for a state. Stateless: tests and screenshots give it any state, including a viewer's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsContent(
    state: AgentsState,
    send: (AgentsIntent) -> Unit,
    onOpenAgent: (String) -> Unit,
    onOpenRun: (String) -> Unit,
    onSignInAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf(AgentFilter.All) }
    Column(modifier.fillMaxSize().testTag("agents-root")) {
        ScreenTitleBar(stringResource(R.string.tab_agents), Modifier.padding(horizontal = Spacing.page), isLarge = true)
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = { send(AgentsIntent.Refresh) },
            modifier = Modifier.weight(1f),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.page, vertical = Spacing.roomy),
                verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
            ) {
                if (state.phase == DetailPhase.Content) {
                    AgentsHeader(state, send, onSignInAgain, filter) { filter = it }
                    AgentsBody(state, send, filter, onOpenAgent, onOpenRun)
                } else {
                    DetailUnavailable(
                        state.phase,
                        state.error,
                        state.isOffline,
                        onRetry = { send(AgentsIntent.Refresh) },
                        onSignInAgain = onSignInAgain,
                    )
                }
            }
        }
    }
    PauseConfirmation(state, send)
}

@Composable
private fun AgentsHeader(
    state: AgentsState,
    send: (AgentsIntent) -> Unit,
    onSignInAgain: () -> Unit,
    filter: AgentFilter,
    onFilter: (AgentFilter) -> Unit,
) {
    val total = state.sections.sumOf { it.rows.size }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.hairline)) {
        Text(
            pluralStringResource(R.plurals.agents_count, total, total),
            Modifier.testTag("agents-count"),
            style = AppType.secondary,
            color = AppTheme.colors.textSecondary,
        )
        UpdatedLabel(state.updatedAtEpochMillis)
    }
    if (state.isOffline) OfflineBanner(state.updatedAtEpochMillis)
    state.actionsDisabledReason?.let { DisabledReason(it) }
    state.feedback?.let { FeedbackBanner(it, { send(AgentsIntent.MessageDismissed) }) }
    state.error?.let { ErrorBanner(it, onRetry = { send(AgentsIntent.Refresh) }, onSignInAgain = onSignInAgain) }
    FilterChips(state, filter, onFilter)
}

@Composable
private fun FilterChips(
    state: AgentsState,
    filter: AgentFilter,
    onFilter: (AgentFilter) -> Unit,
) {
    fun count(group: AgentGroupUi) =
        state.sections
            .firstOrNull { it.group == group }
            ?.rows
            ?.size ?: 0
    val label = stringResource(R.string.agents_filters_label)
    Row(
        Modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        ChoiceChip(stringResource(R.string.agents_filter_all), filter == AgentFilter.All, "agents-filter-all") {
            onFilter(AgentFilter.All)
        }
        ChoiceChip(
            stringResource(R.string.agents_filter_running, count(AgentGroupUi.Running)),
            filter == AgentFilter.Running,
            "agents-filter-running",
        ) { onFilter(AgentFilter.Running) }
        ChoiceChip(
            stringResource(R.string.agents_filter_attention, count(AgentGroupUi.NeedsAttention)),
            filter == AgentFilter.Attention,
            "agents-filter-attention",
        ) { onFilter(AgentFilter.Attention) }
    }
}

@Composable
private fun AgentsBody(
    state: AgentsState,
    send: (AgentsIntent) -> Unit,
    filter: AgentFilter,
    onOpenAgent: (String) -> Unit,
    onOpenRun: (String) -> Unit,
) {
    val shown = state.sections.filter { filter.includes(it.group) }
    if (state.sections.isEmpty()) {
        EmptyState(
            icon = painterResource(R.drawable.ic_tab_agents),
            title = stringResource(R.string.agents_empty_title),
            message = stringResource(R.string.agents_empty_message),
            modifier = Modifier.heightIn(min = EmptyHeight).testTag("agents-empty"),
        )
    } else if (shown.isEmpty()) {
        Text(
            stringResource(R.string.agents_filter_empty),
            Modifier.testTag("agents-filter-empty"),
            style = AppType.body,
            color = AppTheme.colors.textSecondary,
        )
    }
    for (section in shown) AgentSection(section, state, send, onOpenAgent, onOpenRun)
}

@Composable
private fun AgentSection(
    section: AgentSectionUi,
    state: AgentsState,
    send: (AgentsIntent) -> Unit,
    onOpenAgent: (String) -> Unit,
    onOpenRun: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        SectionHeader(
            stringResource(section.group.stringRes()),
            Modifier.testTag("agents-section-${section.group.name}"),
        )
        for (row in section.rows) {
            AgentCard(
                row,
                isBusy = state.busyAgentId == row.id,
                canSetBudget = state.canSetBudget,
                actions =
                    AgentCardActions(
                        onAction = { send(intentFor(it, row)) },
                        onOpen = { onOpenAgent(row.id) },
                        onViewRun = onOpenRun,
                    ),
            )
        }
    }
}

private fun intentFor(
    action: AgentActionUi,
    row: AgentRowUi,
): AgentsIntent =
    when (action) {
        AgentActionUi.Pause -> AgentsIntent.PauseTapped(row.id)
        AgentActionUi.Resume -> AgentsIntent.ResumeTapped(row.id)
        AgentActionUi.RunNow -> AgentsIntent.RunNowTapped(row.id)
    }

/** The confirmation before Pause: the lock shows that the device owner is asked next, inside the shared use case. */
@Composable
private fun PauseConfirmation(
    state: AgentsState,
    send: (AgentsIntent) -> Unit,
) {
    val id = state.confirmPauseAgentId ?: return
    val name =
        state.sections
            .flatMap { it.rows }
            .firstOrNull { it.id == id }
            ?.name
            .orEmpty()
    ConfirmDestructiveSheet(
        title = stringResource(R.string.agents_pause_title, name),
        message = stringResource(R.string.agents_pause_message),
        confirmTitle = stringResource(R.string.agents_pause_confirm),
        onConfirm = { send(AgentsIntent.PauseConfirmed) },
        onCancel = { send(AgentsIntent.PauseCancelled) },
        isWorking = state.isWorking,
    )
}
