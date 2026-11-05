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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    enabled: Boolean,
    isMutating: Boolean,
    send: (InboxIntent) -> Unit,
    open: (InboxItemUi) -> Unit,
    onSnooze: (String) -> Unit,
) {
    val latestItem = rememberUpdatedState(item)
    val latestSend = rememberUpdatedState(send)
    val latestOpen = rememberUpdatedState(open)
    val latestSnooze = rememberUpdatedState(onSnooze)
    val swipe =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        latestSend.value(
                            InboxIntent.DismissTapped(latestItem.value.id),
                        )
                    }

                    SwipeToDismissBoxValue.StartToEnd -> {
                        latestSnooze.value(latestItem.value.id)
                    }

                    SwipeToDismissBoxValue.Settled -> {
                        Unit
                    }
                }
                false
            },
        )
    // One set of actions per card, built once: the lambdas read the latest callbacks, so a new screen state does not
    // give the card new ones (and the card can skip recomposition).
    val actions =
        remember(item.id, enabled) {
            InboxCardActions(
                onOpen = { latestOpen.value(latestItem.value) },
                onApprove = { latestSend.value(InboxIntent.ApproveTapped(item.id)) },
                onReject = { latestSend.value(InboxIntent.RejectTapped(item.id)) },
                onKeepPaused = { latestSend.value(InboxIntent.KeepPausedTapped(item.id)) },
                onSnooze = if (enabled) ({ latestSnooze.value(item.id) }) else null,
                onDismiss = if (enabled) ({ latestSend.value(InboxIntent.DismissTapped(item.id)) }) else null,
            )
        }
    SwipeToDismissBox(
        state = swipe,
        // Composed only while a finger drags the card: a settled card has nothing behind it (R-008).
        backgroundContent = {
            val direction = swipe.dismissDirection
            if (direction != SwipeToDismissBoxValue.Settled) SwipeBackground(direction)
        },
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
    ) { InboxCard(item = item, isDisabled = !enabled || isMutating, actions = actions) }
}

/** What the card hides behind it while it is dragged: the action, in words and with a symbol. */
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val colors = AppTheme.colors
    val snooze = direction == SwipeToDismissBoxValue.StartToEnd
    val tone = if (snooze) colors.accent else colors.destructive
    Row(
        Modifier
            .fillMaxSize()
            .background(tone, RoundedCornerShape(Radius.card))
            .padding(horizontal = Spacing.page),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (snooze) Arrangement.Start else Arrangement.End,
    ) {
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
