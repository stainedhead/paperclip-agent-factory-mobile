package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.DisabledReason
import com.stainedhead.paperclipmobile.android.designsystem.HairlineDivider
import com.stainedhead.paperclipmobile.android.designsystem.Radius
import com.stainedhead.paperclipmobile.android.designsystem.SectionHeader
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.Token
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.RunViewerIntent
import com.stainedhead.paperclipmobile.presentation.RunViewerState

private const val LARGE_FONT_SCALE = 1.3f
private const val AT_BOTTOM_SLACK = 4
private const val NOT_AT_BOTTOM = -1
private val PanelHeight = 240.dp
private val LargePanelHeight = 360.dp

private fun ScrollState.isAtBottom() = value >= maxValue - AT_BOTTOM_SLACK

/** The dark panel of a console in both themes: the log is output, not prose, and reads best light on dark. */
private val LogBackground = Token(light = 0x15171C, dark = 0x0B0C0E)
private val LogText = Token(light = 0xE6E8EC, dark = 0xE6E8EC)
private val LogMuted = Token(light = 0xB4B9C4, dark = 0xB4B9C4)

/** The log tail: the toggle that follows its end, the panel, and Jump to latest once the person has scrolled away. */
@Composable
fun LogTail(
    state: RunViewerState,
    send: (RunViewerIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(stringResource(R.string.run_log_tail), Modifier.weight(1f))
            AutoScrollToggle(state.autoScroll) {
                send(
                    if (state.autoScroll) {
                        RunViewerIntent.UserScrolled(
                            atBottom = false,
                        )
                    } else {
                        RunViewerIntent.JumpToLatest
                    },
                )
            }
        }
        LogPanel(state, send)
        if (!state.autoScroll) {
            AppButton(
                stringResource(R.string.run_jump_to_latest),
                { send(RunViewerIntent.JumpToLatest) },
                Modifier.testTag("run-jump-to-latest"),
                kind = ButtonKind.Secondary,
            )
        }
    }
}

@Composable
private fun AutoScrollToggle(
    isOn: Boolean,
    onToggle: () -> Unit,
) {
    val label = stringResource(if (isOn) R.string.run_autoscroll_on else R.string.run_autoscroll_off)
    Box(
        Modifier
            .heightIn(min = Spacing.minimumTarget)
            .clickable(
                onClickLabel = stringResource(R.string.run_autoscroll_hint),
                role = Role.Button,
                onClick = onToggle,
            ).padding(horizontal = Spacing.medium)
            .testTag("run-autoscroll"),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = AppType.secondary, color = AppTheme.colors.accent)
    }
}

/**
 * The output, in a console panel of fixed height with its own scroll. While following is on the panel stays at the
 * end as the text grows; when the person drags it away the holder is told where it ended up, and it stops following
 * (the rule is the holder's). Scrolling the panel by code never counts as the person scrolling.
 */
@Composable
private fun LogPanel(
    state: RunViewerState,
    send: (RunViewerIntent) -> Unit,
) {
    val scroll = rememberScrollState()
    var dragged by remember { mutableStateOf(false) }
    // Where a drag began when it began at the bottom (else NOT_AT_BOTTOM): text that grows under a held finger does not
    // make the release count as scrolling away.
    var bottomAtDragStart by remember { mutableIntStateOf(NOT_AT_BOTTOM) }
    // Following is paused while a finger is down, so the content never moves under it, and resumes on release (R-005).
    LaunchedEffect(state.autoScroll) {
        if (state.autoScroll) snapshotFlow { scroll.maxValue }.collect { if (!dragged) scroll.scrollTo(it) }
    }
    LaunchedEffect(scroll) {
        scroll.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) {
                dragged = true
                bottomAtDragStart = if (scroll.isAtBottom()) scroll.value else NOT_AT_BOTTOM
            }
        }
    }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.isScrollInProgress }.collect { moving ->
            if (!moving && dragged) {
                dragged = false
                val atBottom =
                    scroll.isAtBottom() || (bottomAtDragStart != NOT_AT_BOTTOM && scroll.value >= bottomAtDragStart)
                send(RunViewerIntent.UserScrolled(atBottom))
                if (atBottom) scroll.scrollTo(scroll.maxValue)
            }
        }
    }
    val dark = AppTheme.colors.isDark
    val height = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) LargePanelHeight else PanelHeight
    Column(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(Radius.card))
            .background(LogBackground.color(dark))
            .testTag("run-log-panel")
            .verticalScroll(scroll)
            .padding(Spacing.card),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        if (state.logDroppedStart) {
            Text(
                stringResource(R.string.run_log_dropped),
                Modifier.testTag("run-log-dropped"),
                style = AppType.secondary,
                color = LogMuted.color(dark),
            )
        }
        val mono = AppType.secondary.copy(fontFamily = FontFamily.Monospace)
        if (state.logText.isEmpty()) {
            Text(
                stringResource(if (state.logUnavailable) R.string.run_log_unavailable else R.string.run_log_empty),
                Modifier.testTag("run-log-empty"),
                style = mono,
                color = LogMuted.color(dark),
            )
        } else {
            SelectionContainer {
                Text(
                    state.logText,
                    Modifier.fillMaxWidth().testTag("run-log"),
                    style = mono,
                    color = LogText.color(dark),
                )
            }
        }
    }
}

/** The bar under a live run: why Cancel is off, then Cancel (the lock: the device owner is asked after the sheet). */
@Composable
fun CancelBar(
    state: RunViewerState,
    send: (RunViewerIntent) -> Unit,
) {
    val offered = state.isLive || state.isCancelling
    if (state.phase != DetailPhase.Content || state.run == null || !offered) return
    Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface).testTag("run-cancel-bar")) {
        HairlineDivider()
        Column(
            Modifier
                .padding(horizontal = Spacing.page, vertical = Spacing.medium)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            val reason = state.actionsDisabledReason
            if (reason != null && !state.canCancel && !state.isCancelling) DisabledReason(reason)
            AppButton(
                stringResource(if (state.isCancelling) R.string.run_cancelling else R.string.run_cancel_run),
                { send(RunViewerIntent.CancelTapped) },
                Modifier.testTag("run-cancel"),
                kind = ButtonKind.Destructive,
                enabled = state.canCancel,
                icon = painterResource(R.drawable.ic_lock),
            )
        }
    }
}
