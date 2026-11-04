package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.ActionRow
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.CardSurface
import com.stainedhead.paperclipmobile.android.designsystem.Radius
import com.stainedhead.paperclipmobile.android.designsystem.SectionHeader
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.android.shell.AppTab
import com.stainedhead.paperclipmobile.presentation.FactoryHomeState
import com.stainedhead.paperclipmobile.presentation.FactoryTilesUi
import com.stainedhead.paperclipmobile.presentation.SpendUi

private val WindowHeight = 32.dp
private val HairlineWidth = 1.dp

/** The month's spend against its budget with the bar and the percent, then the spend of the rolling windows. */
@Composable
fun SpendCard(
    state: FactoryHomeState,
    tiles: FactoryTilesUi,
) {
    val colors = AppTheme.colors
    CardSurface(Modifier.testTag("factory-spend-card")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.hairline),
            ) {
                Text(
                    stringResource(R.string.factory_month_spend).uppercase(),
                    style = AppType.label,
                    color = colors.textSecondary,
                )
                Text(
                    stringResource(R.string.factory_utc_month),
                    style = AppType.secondary,
                    color = colors.textSecondary,
                )
            }
            Text(
                if (tiles.hasBudget) {
                    stringResource(R.string.factory_spend_of, tiles.monthSpend.text(), tiles.monthBudget.text())
                } else {
                    tiles.monthSpend.text()
                },
                Modifier.testTag("factory-spend"),
                style = AppType.largeTitle,
                color = colors.textPrimary,
            )
            if (tiles.hasBudget) {
                SpendMeter(tiles.monthPercentUsed)
                Text(
                    stringResource(R.string.factory_spend_percent, tiles.monthPercentUsed),
                    Modifier.testTag("factory-spend-percent"),
                    style = AppType.secondary,
                    color = colors.textSecondary,
                )
            }
            SpendWindows(state)
        }
    }
}

@Composable
private fun SpendWindows(state: FactoryHomeState) {
    if (state.spend.isEmpty()) {
        if (state.spendUnavailable) {
            Text(
                stringResource(R.string.factory_spend_unavailable),
                Modifier.testTag("factory-spend-unavailable"),
                style = AppType.secondary,
                color = AppTheme.colors.textSecondary,
            )
        }
        return
    }
    val description = stringResource(R.string.factory_spend_windows_label)
    FlowRow(
        Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) { for (spend in state.spend) SpendWindowPill(spend) }
}

@Composable
private fun SpendWindowPill(spend: SpendUi) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .heightIn(min = WindowHeight)
            .clip(CircleShape)
            .background(colors.muted)
            .padding(horizontal = Spacing.large)
            .semantics(mergeDescendants = true) {}
            .testTag("factory-window-${spend.window.name}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Text(stringResource(spend.window.stringRes()), style = AppType.secondary, color = colors.textSecondary)
        Text(spend.cost.text(), style = AppType.button, color = colors.textPrimary)
    }
}

/** Two rows of two tiles (a column at large font sizes); each opens the tab that holds what it counts. */
@Composable
fun TileGrid(
    tiles: FactoryTilesUi,
    onOpenTab: (AppTab) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        ActionRow(
            listOf({ AgentsTile(tiles, it, onOpenTab) }, { WorkTile(tiles, it, onOpenTab) }),
            spacing = Spacing.large,
        )
        ActionRow(
            listOf({ ApprovalsTile(tiles, it, onOpenTab) }, { BudgetTile(tiles, it, onOpenTab) }),
            spacing = Spacing.large,
        )
    }
}

@Composable
private fun AgentsTile(
    tiles: FactoryTilesUi,
    modifier: Modifier,
    onOpenTab: (AppTab) -> Unit,
) = Tile(
    "agents",
    stringResource(R.string.factory_tile_agents),
    pluralStringResource(R.plurals.factory_agents_running, tiles.agentsRunning, tiles.agentsRunning),
    pluralStringResource(
        R.plurals.factory_agents_error_paused,
        tiles.agentsError,
        tiles.agentsError,
        tiles.agentsPaused,
    ),
    modifier,
) { onOpenTab(AppTab.Agents) }

@Composable
private fun WorkTile(
    tiles: FactoryTilesUi,
    modifier: Modifier,
    onOpenTab: (AppTab) -> Unit,
) = Tile(
    "work",
    stringResource(R.string.factory_tile_work),
    pluralStringResource(R.plurals.factory_work_open, tiles.tasksOpen, tiles.tasksOpen),
    pluralStringResource(
        R.plurals.factory_work_blocked_in_progress,
        tiles.tasksBlocked,
        tiles.tasksBlocked,
        tiles.tasksInProgress,
    ),
    modifier,
) { onOpenTab(AppTab.Work) }

@Composable
private fun ApprovalsTile(
    tiles: FactoryTilesUi,
    modifier: Modifier,
    onOpenTab: (AppTab) -> Unit,
) = Tile(
    "approvals",
    stringResource(R.string.factory_tile_approvals),
    pluralStringResource(R.plurals.factory_approvals_pending, tiles.pendingApprovals, tiles.pendingApprovals),
    null,
    modifier,
) { onOpenTab(AppTab.Inbox) }

@Composable
private fun BudgetTile(
    tiles: FactoryTilesUi,
    modifier: Modifier,
    onOpenTab: (AppTab) -> Unit,
) = Tile(
    "budget",
    stringResource(R.string.factory_tile_budget),
    pluralStringResource(R.plurals.factory_incidents_open, tiles.budgetIncidents, tiles.budgetIncidents),
    null,
    modifier,
) { onOpenTab(AppTab.Inbox) }

@Composable
private fun Tile(
    id: String,
    title: String,
    value: String,
    detail: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(Radius.card)
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minimumTarget)
            .clip(shape)
            .background(colors.surface)
            .border(HairlineWidth, colors.hairline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.card)
            .testTag("factory-tile-$id"),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Text(title.uppercase(), style = AppType.label, color = colors.textSecondary)
        Text(value, style = AppType.title, color = colors.textPrimary)
        detail?.let { Text(it, style = AppType.secondary, color = colors.textSecondary) }
    }
}

/** The runs in progress, each a way into the Run viewer. */
@Composable
fun RunningNow(
    state: FactoryHomeState,
    onOpenRun: (String) -> Unit,
) {
    Column(Modifier.testTag("factory-running-now"), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        SectionHeader(stringResource(R.string.factory_running_now))
        if (state.liveRuns.isEmpty()) {
            CardSurface(Modifier.testTag("factory-nothing-running")) {
                Text(
                    stringResource(R.string.factory_nothing_running),
                    style = AppType.body,
                    color = AppTheme.colors.textSecondary,
                )
            }
        } else {
            RunRowsCard(state.liveRuns, "factory-run", onOpenRun)
        }
    }
}
