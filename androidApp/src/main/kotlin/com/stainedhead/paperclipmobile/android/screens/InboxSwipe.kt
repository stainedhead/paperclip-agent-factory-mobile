package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.Radius
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.presentation.InboxIntent
import com.stainedhead.paperclipmobile.presentation.InboxItemUi
import com.stainedhead.paperclipmobile.presentation.InboxState

private val SwipeGlyph = 20.dp

/**
 * A card that can be swiped: toward the end to snooze (the presets open first), toward the start to dismiss. Nothing
 * stays swiped away: the gesture only sends an intent and the card goes (or does not) with the shared state, so an
 * Undo brings it back in place. Swipes are off while actions are unavailable (offline, viewer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableCard(
    item: InboxItemUi,
    state: InboxState,
    send: (InboxIntent) -> Unit,
    open: (InboxItemUi) -> Unit,
    onSnooze: (String) -> Unit,
) {
    val enabled = state.actionsDisabledReason == null
    val latestSend = rememberUpdatedState(send)
    val latestSnooze = rememberUpdatedState(onSnooze)
    val swipe =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.EndToStart -> latestSend.value(InboxIntent.DismissTapped(item.id))
                    SwipeToDismissBoxValue.StartToEnd -> latestSnooze.value(item.id)
                    SwipeToDismissBoxValue.Settled -> Unit
                }
                false
            },
        )
    SwipeToDismissBox(
        state = swipe,
        backgroundContent = { SwipeBackground(swipe.dismissDirection) },
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
    ) {
        InboxCard(
            item = item,
            isDisabled = !enabled || state.isMutating,
            actions =
                InboxCardActions(
                    onOpen = { open(item) },
                    onApprove = { send(InboxIntent.ApproveTapped(item.id)) },
                    onReject = { send(InboxIntent.RejectTapped(item.id)) },
                    onKeepPaused = { send(InboxIntent.KeepPausedTapped(item.id)) },
                    onSnooze = if (enabled) ({ onSnooze(item.id) }) else null,
                    onDismiss = if (enabled) ({ send(InboxIntent.DismissTapped(item.id)) }) else null,
                ),
        )
    }
}

/** What the card hides behind it while it is dragged: the action, in words and with a symbol. */
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val colors = AppTheme.colors
    val snooze = direction == SwipeToDismissBoxValue.StartToEnd
    val shown = direction != SwipeToDismissBoxValue.Settled
    val tone = if (snooze) colors.accent else colors.destructive
    Row(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(Radius.card))
            .background(if (shown) tone else colors.muted)
            .padding(horizontal = Spacing.page),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (snooze) Arrangement.Start else Arrangement.End,
    ) {
        if (shown) {
            Icon(
                painterResource(if (snooze) R.drawable.ic_snooze else R.drawable.ic_dismiss),
                contentDescription = null,
                Modifier.size(SwipeGlyph),
                tint = colors.onAccent,
            )
            Text(
                stringResource(if (snooze) R.string.inbox_swipe_snooze else R.string.inbox_swipe_dismiss),
                Modifier.padding(start = Spacing.medium),
                style = AppType.button,
                color = colors.onAccent,
            )
        }
    }
}
