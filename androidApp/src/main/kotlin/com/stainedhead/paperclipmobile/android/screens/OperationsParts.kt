package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.CardGroup
import com.stainedhead.paperclipmobile.android.designsystem.HairlineDivider
import com.stainedhead.paperclipmobile.android.designsystem.RelativeAge
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.compactText
import com.stainedhead.paperclipmobile.android.designsystem.foreground
import com.stainedhead.paperclipmobile.android.designsystem.rememberNowMillis
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.RunRowUi

private val AvatarSize = 40.dp
private val MeterHeight = 8.dp
private val Chevron = 16.dp
private const val MILLIS_PER_SECOND = 1_000L
private const val STACK_FONT_SCALE = 1.3f

/** The circle with an agent's letters. Decoration only: the name is always written next to it. */
@Composable
fun AgentAvatar(
    name: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .defaultMinSize(AvatarSize, AvatarSize)
            .clip(CircleShape)
            .background(AppTheme.colors.muted)
            .padding(Spacing.large)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(AgentInitials.of(name), maxLines = 1, style = AppType.label, color = AppTheme.colors.textPrimary)
    }
}

/** The bar under a spend figure. The figures next to it say the same thing in words, so it is hidden from TalkBack. */
@Composable
fun SpendMeter(
    percent: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    LinearProgressIndicator(
        progress = { gauge(percent) },
        modifier = modifier.fillMaxWidth().heightIn(min = MeterHeight).clearAndSetSemantics {},
        color = colors.accent,
        trackColor = colors.muted,
        drawStopIndicator = {},
    )
}

/** "Engineer 1 · PAP-1204": who ran it and for which issue, whichever of the two is known. */
@Composable
fun runTitle(row: RunRowUi): String {
    val agent = row.agentName
    val issue = row.issueIdentifier
    return when {
        agent != null && issue != null -> stringResource(R.string.run_title_with_issue, agent, issue)
        agent != null -> agent
        issue != null -> issue
        else -> stringResource(row.status.stringRes())
    }
}

/** How long a run has gone (to now while it runs, to its end once finished), or null when it never started. */
fun runAge(
    row: RunRowUi,
    nowMillis: Long,
) = row.startedAtEpochMillis?.let {
    RelativeAge.of(((row.finishedAtEpochMillis ?: nowMillis) - it) / MILLIS_PER_SECOND)
}

/**
 * One run in a list (Running now, an agent's recent runs): who and what, how long, and a way into it. The whole row is
 * one 48 dp target that reads as a sentence to TalkBack.
 */
@Composable
fun RunListRow(
    row: RunRowUi,
    tag: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val now by rememberNowMillis()
    val stacked = LocalDensity.current.fontScale >= STACK_FONT_SCALE
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minimumTarget)
            .clickable(onClickLabel = stringResource(R.string.run_open_hint), onClick = onOpen)
            .padding(vertical = Spacing.medium)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.hairline)) {
            Text(runTitle(row), style = AppType.body, color = colors.textPrimary)
            row.issueTitle?.let { Text(it, style = AppType.secondary, color = colors.textSecondary) }
            if (stacked) RunTrail(row, now, stacked = true)
        }
        if (!stacked) RunTrail(row, now, stacked = false)
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            Modifier.size(Chevron),
            tint = colors.textSecondary,
        )
    }
}

/** How long and how it went: beside the title, or under it when the font is large. */
@Composable
private fun RunTrail(
    row: RunRowUi,
    nowMillis: Long,
    stacked: Boolean,
) {
    val colors = AppTheme.colors
    val age: @Composable () -> Unit = {
        runAge(row, nowMillis)?.let { Text(it.compactText(), style = AppType.body, color = colors.textPrimary) }
    }
    val status: @Composable () -> Unit = {
        Text(
            stringResource(row.status.stringRes()),
            style = AppType.secondary,
            color = row.status.tone().foreground(colors),
        )
    }
    if (stacked) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            age()
            status()
        }
    } else {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.hairline)) {
            age()
            status()
        }
    }
}

/** Rows of one card, divided by hairlines. */
@Composable
fun RunRowsCard(
    rows: List<RunRowUi>,
    tagPrefix: String,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CardGroup(modifier) {
        rows.forEachIndexed { index, run ->
            if (index > 0) HairlineDivider()
            RunListRow(run, "$tagPrefix-${run.runId}", { onOpen(run.runId) })
        }
    }
}
