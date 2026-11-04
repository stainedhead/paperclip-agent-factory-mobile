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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.CardSurface
import com.stainedhead.paperclipmobile.android.designsystem.RelativeAge
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.StatusTone
import com.stainedhead.paperclipmobile.android.designsystem.compactText
import com.stainedhead.paperclipmobile.android.designsystem.foreground
import com.stainedhead.paperclipmobile.android.designsystem.rememberNowMillis
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.AgentActionUi
import com.stainedhead.paperclipmobile.presentation.AgentRowUi
import com.stainedhead.paperclipmobile.presentation.AgentStatusUi

private val SpinnerSize = 20.dp
private val ChevronSize = 16.dp
private val LockSize = 14.dp
private val HairlineWidth = 1.dp
private const val MILLIS_PER_SECOND = 1_000L

/** What an agent is doing in words, and the colour that backs the words up (never the colour alone). */
data class AgentLine(
    val text: String,
    val tone: StatusTone,
)

/**
 * "Running · PAP-1204 · 4m", "Error · last heartbeat 2m ago", "Paused by budget hard stop": the status, then what it
 * is on or how long since it last reported, then the server's lifecycle word when it is not the usual one.
 */
@Composable
fun agentLine(
    row: AgentRowUi,
    nowMillis: Long,
): AgentLine {
    if (row.isBudgetPaused) return AgentLine(stringResource(R.string.agents_paused_by_budget), StatusTone.Critical)
    val parts = mutableListOf(stringResource(row.status.stringRes()))
    val run = row.currentRun?.takeIf { it.status.isLive() }
    val heartbeat = row.lastHeartbeatAtEpochMillis
    if (row.status == AgentStatusUi.Running && run != null) {
        run.issueIdentifier?.let { parts += it }
        runAge(run, nowMillis)?.let { parts += it.compactText() }
    } else if (heartbeat != null && row.lifecycleState == null) {
        val age = RelativeAge.of((nowMillis - heartbeat) / MILLIS_PER_SECOND)
        parts += stringResource(R.string.agents_last_heartbeat, age.compactText())
    }
    row.lifecycleState?.let { parts += it }
    return AgentLine(parts.joinToString(" · "), row.status.tone())
}

/** The words of an agent's tone: neutral reads as ordinary secondary text, the rest in their colour. */
@Composable
fun AgentLine.color(): Color =
    if (tone ==
        StatusTone.Neutral
    ) {
        AppTheme.colors.textSecondary
    } else {
        tone.foreground(AppTheme.colors)
    }

/** A compact pill button of a row, at least 48 dp tall. The lock marks the one that asks the device owner. */
@Composable
fun RowButton(
    text: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
) {
    val colors = AppTheme.colors
    Row(
        modifier
            .heightIn(min = Spacing.minimumTarget)
            .clip(CircleShape)
            .background(colors.surface)
            .border(HairlineWidth, colors.border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.roomy)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Text(text, style = AppType.button, color = colors.textPrimary)
        if (locked) {
            Icon(painterResource(R.drawable.ic_lock), null, Modifier.size(LockSize), tint = colors.textPrimary)
        }
    }
}

/** What one agent card sends back: a button of the holder's, the card itself, its run or its budget. */
class AgentCardActions(
    val onAction: (AgentActionUi) -> Unit,
    val onOpen: () -> Unit,
    val onViewRun: (String) -> Unit,
)

/**
 * One agent: who, in what state, what it is doing, and the buttons this person may use right now. The buttons are the
 * holder's (by status, role and connection); Budget is offered to an owner for an agent that has one.
 */
@Composable
fun AgentCard(
    row: AgentRowUi,
    isBusy: Boolean,
    canSetBudget: Boolean,
    actions: AgentCardActions,
) {
    CardSurface(Modifier.testTag("agent-card-${row.id}")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
            AgentRowHeader(row, isBusy, actions.onOpen)
            AgentButtons(row, canSetBudget, actions)
        }
    }
}

@Composable
private fun AgentRowHeader(
    row: AgentRowUi,
    isBusy: Boolean,
    onOpen: () -> Unit,
) {
    val colors = AppTheme.colors
    val now by rememberNowMillis()
    val line = agentLine(row, now)
    val working = stringResource(R.string.agents_working)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minimumTarget)
            .clickable(onClickLabel = stringResource(R.string.agents_open_hint), onClick = onOpen)
            .testTag("agent-row-${row.id}"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
    ) {
        AgentAvatar(row.name)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.hairline)) {
            Text(row.name, style = AppType.cardTitle, color = colors.textPrimary)
            Text(line.text, style = AppType.secondary, color = line.color())
        }
        if (isBusy) {
            CircularProgressIndicator(Modifier.size(SpinnerSize).semantics { contentDescription = working })
        } else {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                null,
                Modifier.size(ChevronSize),
                tint = colors.textSecondary,
            )
        }
    }
}

/** Resume and Run now, View run while it runs, Budget for an owner, then Pause (the lock) last. */
@Composable
private fun AgentButtons(
    row: AgentRowUi,
    canSetBudget: Boolean,
    actions: AgentCardActions,
) {
    val live = row.currentRun?.takeIf { it.status.isLive() }
    val visible = row.visibleActions()
    val showsBudget = canSetBudget && row.hasBudget
    if (visible.isEmpty() && live == null && !showsBudget) return
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        for (action in visible.filter { it != AgentActionUi.Pause }) {
            RowButton(
                stringResource(action.stringRes()),
                "agent-${row.id}-${action.tag()}",
                { actions.onAction(action) },
            )
        }
        if (live != null) {
            RowButton(
                stringResource(R.string.agents_view_run),
                "agent-${row.id}-view-run",
                { actions.onViewRun(live.runId) },
            )
        }
        if (showsBudget) RowButton(stringResource(R.string.agents_budget), "agent-${row.id}-budget", actions.onOpen)
        if (AgentActionUi.Pause in visible) {
            RowButton(
                stringResource(AgentActionUi.Pause.stringRes()),
                "agent-${row.id}-pause",
                { actions.onAction(AgentActionUi.Pause) },
                locked = true,
            )
        }
    }
}

/** The tag fragment of an action: `pause`, `resume`, `run-now`. */
fun AgentActionUi.tag(): String =
    when (this) {
        AgentActionUi.Pause -> "pause"
        AgentActionUi.Resume -> "resume"
        AgentActionUi.RunNow -> "run-now"
    }
