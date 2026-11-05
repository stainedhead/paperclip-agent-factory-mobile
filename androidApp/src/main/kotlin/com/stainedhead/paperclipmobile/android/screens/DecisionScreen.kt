package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.BarIconButton
import com.stainedhead.paperclipmobile.android.designsystem.ConfirmHapticOnRise
import com.stainedhead.paperclipmobile.android.designsystem.ErrorBanner
import com.stainedhead.paperclipmobile.android.designsystem.OfflineBanner
import com.stainedhead.paperclipmobile.android.designsystem.ScreenTitleBar
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.env.rememberDecisionHolder
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.android.shell.TabNavigator
import com.stainedhead.paperclipmobile.presentation.DecisionIntent
import com.stainedhead.paperclipmobile.presentation.DecisionState
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.umbrella.DecisionHints

/**
 * Screen 2 (AND-040). It renders `DecisionState` and forwards intents. Which options exist, what is enabled, when the
 * device owner is asked (inside the shared use case, so a cancelled prompt arrives as feedback) and what the final
 * state is are the shared holder's. The route carries what the Inbox card knew; Snooze is offered only with its key.
 */
@Composable
fun DecisionScreen(
    session: GraphSession,
    route: AppRoute.Decision,
    navigator: TabNavigator,
) {
    val holder =
        rememberDecisionHolder(
            session,
            route.id,
            DecisionHints(route.originAgentName, route.decideBy, route.dismissalKey),
        )
    val state by holder.state.collectAsStateWithLifecycle()
    RefreshWhileVisible(session, holder.holder.refreshTarget)
    LaunchedEffect(state.isSnoozed) { if (state.isSnoozed) navigator.pop() }
    DecisionContent(state, holder::onIntent, route.identifier, navigator.pop)
}

/** What the Decision shows for a state. Stateless: tests and screenshots give it any state. */
@Composable
fun DecisionContent(
    state: DecisionState,
    send: (DecisionIntent) -> Unit,
    identifier: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var snoozeOpen by rememberSaveable { mutableStateOf(false) }
    ConfirmHapticOnRise(state.finalState != null)
    Column(modifier.fillMaxSize().imePadding().testTag("decision-root")) {
        ScreenTitleBar(
            stringResource(R.string.decision_nav_title),
            Modifier.padding(horizontal = Spacing.page),
            onBack = onBack,
        ) {
            if (state.canSnooze) {
                BarIconButton(
                    painterResource(R.drawable.ic_snooze),
                    stringResource(R.string.decision_snooze),
                    { snoozeOpen = true },
                    "decision-snooze",
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.page, vertical = Spacing.roomy)
                .testTag("decision-screen"),
            verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
        ) {
            if (state.isOffline && state.phase == DetailPhase.Content) OfflineBanner(state.updatedAtEpochMillis)
            if (state.phase == DetailPhase.Content) {
                DecisionBody(state, send, identifier)
            } else {
                DetailUnavailable(state.phase, state.error, state.isOffline, { send(DecisionIntent.Refresh) })
            }
        }
        DecisionActionBar(state, send)
    }
    if (snoozeOpen) {
        SnoozeSheet(
            options = state.snoozeOptions,
            onPick = {
                send(DecisionIntent.SnoozeRequested(it))
                snoozeOpen = false
            },
            onCancel = { snoozeOpen = false },
        )
    }
}

@Composable
private fun DecisionBody(
    state: DecisionState,
    send: (DecisionIntent) -> Unit,
    identifier: String?,
) {
    DecisionHeader(state, identifier)
    state.feedback?.takeIf { it.inputId == null }?.let { FeedbackBanner(it, { send(DecisionIntent.MessageDismissed) }) }
    state.error?.let { ErrorBanner(it, onRetry = { send(DecisionIntent.Refresh) }) }
    val final = state.finalState
    if (final != null) {
        DecisionFinal(final)
    } else {
        DecisionOptions(state, send)
        DecisionInputs(state, send)
    }
    state.execution?.let { DecisionExecution(it) }
}
