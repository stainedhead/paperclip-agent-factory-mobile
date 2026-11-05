package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.ActionRow
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.CardGroup
import com.stainedhead.paperclipmobile.android.designsystem.CardSurface
import com.stainedhead.paperclipmobile.android.designsystem.DisabledReason
import com.stainedhead.paperclipmobile.android.designsystem.SectionHeader
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.asksDeviceOwner
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.AgentActionUi
import com.stainedhead.paperclipmobile.presentation.AgentDetailIntent
import com.stainedhead.paperclipmobile.presentation.AgentDetailState

private val SpinnerSize = 20.dp
private val ChevronSize = 16.dp

private fun AgentActionUi.intent(): AgentDetailIntent =
    when (this) {
        AgentActionUi.Pause -> AgentDetailIntent.PauseTapped
        AgentActionUi.Resume -> AgentDetailIntent.ResumeTapped
        AgentActionUi.RunNow -> AgentDetailIntent.RunNowTapped
    }

/** The buttons the holder offers now; while one runs, a note; otherwise why there are none. */
@Composable
fun AgentActions(
    state: AgentDetailState,
    send: (AgentDetailIntent) -> Unit,
) {
    if (state.actions.isNotEmpty()) {
        ActionRow(
            state.actions.map { action ->
                { modifier: Modifier ->
                    AppButton(
                        stringResource(action.stringRes()),
                        { send(action.intent()) },
                        modifier
                            .asksDeviceOwner(stringResource(action.stringRes()), action == AgentActionUi.Pause)
                            .testTag("agent-detail-${action.tag()}"),
                        kind = if (action == AgentActionUi.Pause) ButtonKind.Destructive else ButtonKind.Secondary,
                        icon = if (action == AgentActionUi.Pause) painterResource(R.drawable.ic_lock) else null,
                    )
                }
            },
        )
    } else if (state.isBusy) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Spacing.minimumTarget).testTag("agent-detail-working"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            CircularProgressIndicator(Modifier.size(SpinnerSize))
            Text(stringResource(R.string.agents_working), style = AppType.body, color = AppTheme.colors.textSecondary)
        }
    } else {
        state.actionsDisabledReason?.let { DisabledReason(it) }
    }
}

/** The issue the agent is on and its run in progress, or a note that it is idle. */
@Composable
fun AgentWork(
    state: AgentDetailState,
    onOpenIssue: (String) -> Unit,
    onOpenRun: (String) -> Unit,
) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        SectionHeader(stringResource(R.string.agent_detail_current_issue))
        val issue = state.currentIssue
        if (issue != null) {
            CardSurface(
                Modifier.clickable(role = Role.Button) { onOpenIssue(issue.id) }.testTag("agent-current-issue"),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Spacing.minimumTarget),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.hairline)) {
                        Text(issue.identifier, style = AppType.label, color = colors.textSecondary)
                        Text(issue.title, style = AppType.body, color = colors.textPrimary)
                    }
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        null,
                        Modifier.size(ChevronSize),
                        tint = colors.textSecondary,
                    )
                }
            }
        } else {
            CardSurface(Modifier.testTag("agent-idle-note")) {
                Text(stringResource(R.string.agent_detail_idle_now), style = AppType.body, color = colors.textSecondary)
            }
        }
        state.currentRun?.let { run ->
            CardGroup { RunListRow(run, "agent-current-run", { onOpenRun(run.runId) }) }
        }
    }
}

/** The month's spend, the bar against the monthly budget, and Set budget for an owner (or why not). */
@Composable
fun AgentSpend(
    state: AgentDetailState,
    onSetBudget: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        SectionHeader(stringResource(R.string.agent_detail_month_spend))
        CardSurface {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
                state.monthSpend?.let {
                    Text(
                        it.text(),
                        Modifier.testTag("agent-month-spend"),
                        style = AppType.title,
                        color = colors.textPrimary,
                    )
                }
                val budget = state.monthlyBudget
                if (budget != null && budget.minorUnits > 0) {
                    SpendMeter(state.budgetPercentUsed)
                    Text(
                        stringResource(R.string.agent_detail_budget_line, budget.text(), state.budgetPercentUsed),
                        Modifier.testTag("agent-budget"),
                        style = AppType.secondary,
                        color = colors.textSecondary,
                    )
                } else {
                    Text(
                        stringResource(R.string.agent_detail_no_budget),
                        Modifier.testTag("agent-budget"),
                        style = AppType.secondary,
                        color = colors.textSecondary,
                    )
                }
                if (state.canSetBudget) {
                    AppButton(
                        stringResource(R.string.agent_detail_set_budget),
                        onSetBudget,
                        Modifier.testTag("agent-set-budget"),
                        kind = ButtonKind.Secondary,
                    )
                } else {
                    state.budgetDisabledReason?.let { DisabledReason(it) }
                }
            }
        }
    }
}

/** The agent's latest runs, each a way into the Run viewer. */
@Composable
fun AgentRecentRuns(
    state: AgentDetailState,
    onOpenRun: (String) -> Unit,
) {
    Column(Modifier.testTag("agent-recent-runs"), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        SectionHeader(stringResource(R.string.agent_detail_recent_runs))
        if (state.recentRuns.isEmpty()) {
            CardSurface {
                Text(
                    stringResource(R.string.agent_detail_no_runs),
                    style = AppType.body,
                    color = AppTheme.colors.textSecondary,
                )
            }
        } else {
            RunRowsCard(state.recentRuns, "agent-run", onOpenRun)
        }
    }
}
