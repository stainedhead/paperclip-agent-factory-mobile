package com.stainedhead.paperclipmobile.android.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.EmptyState
import com.stainedhead.paperclipmobile.android.designsystem.LoadingState
import com.stainedhead.paperclipmobile.android.designsystem.LocalReferenceMillis
import com.stainedhead.paperclipmobile.android.env.AppEnvironment
import com.stainedhead.paperclipmobile.android.env.GraphMode
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.AppIntent
import com.stainedhead.paperclipmobile.presentation.AppState
import com.stainedhead.paperclipmobile.presentation.FirstRunState
import com.stainedhead.paperclipmobile.presentation.FirstRunStep
import com.stainedhead.paperclipmobile.presentation.StartupNotice

/**
 * Chooses what the app shows (AND-010, AND-012): the startup notice, loading, the lock, first run when there is no
 * factory yet (or while its flow is on), or the tab shell. It holds no rule: [rootDestination] decides and the screens
 * render the shared holders' state. [onSecureChanged] tells the window when it must hide from screenshots.
 */
@Composable
fun RootScreen(
    environment: AppEnvironment,
    lock: @Composable (LockActions) -> Unit,
    firstRun: @Composable (environment: AppEnvironment, session: GraphSession) -> Unit,
    tabs: @Composable (session: GraphSession, inboxBadge: Int) -> Unit,
    onSecureChanged: (Boolean) -> Unit = {},
) {
    val session by environment.session.collectAsStateWithLifecycle()
    var pairingOnTabs by remember { mutableStateOf(false) }
    val reportPairing = remember { { waiting: Boolean -> pairingOnTabs = waiting } }
    val pinnedClock = if (session.mode == GraphMode.Demo) session.graph?.nowEpochMillis else null
    Box(Modifier.fillMaxSize().background(AppTheme.colors.background).testTag("root")) {
        CompositionLocalProvider(
            LocalReferenceMillis provides pinnedClock,
            LocalPairingSecurity provides reportPairing,
        ) {
            val app = session.app
            val owned = session.firstRun
            if (session.isOpening) {
                SideEffect { onSecureChanged(false) }
                LoadingState(stringResource(R.string.common_loading), Modifier.testTag("graph-opening"))
            } else if (session.notice != null || app == null || owned == null) {
                SideEffect { onSecureChanged(false) }
                StartupNoticeScreen(session.notice ?: StartupNotice.LiveGraphUnavailable, environment::retryLaunch)
            } else {
                val appState by app.state.collectAsStateWithLifecycle()
                val firstRunState by owned.state.collectAsStateWithLifecycle()
                val inProgress by session.firstRunInProgress.collectAsStateWithLifecycle()
                val badge by session.inboxBadge.collectAsStateWithLifecycle()
                Destination(
                    environment,
                    session,
                    appState,
                    firstRunState,
                    inProgress,
                    badge,
                    LockActions(appState.unlockIssue) { app.send(AppIntent.UnlockRequested) },
                    Slots(lock, firstRun, tabs),
                    pairingOnTabs,
                    onSecureChanged,
                )
            }
        }
    }
}

private class Slots(
    val lock: @Composable (LockActions) -> Unit,
    val firstRun: @Composable (AppEnvironment, GraphSession) -> Unit,
    val tabs: @Composable (GraphSession, Int) -> Unit,
)

@Composable
private fun Destination(
    environment: AppEnvironment,
    session: GraphSession,
    appState: AppState,
    firstRunState: FirstRunState,
    firstRunInProgress: Boolean,
    badge: Int,
    lockActions: LockActions,
    slots: Slots,
    pairingOnTabs: Boolean,
    onSecureChanged: (Boolean) -> Unit,
) {
    val destination = rootDestination(null, appState, firstRunInProgress)
    val secure =
        screenIsSecure(destination, pairing = firstRunState.step == FirstRunStep.Pair, pairingOnTabs = pairingOnTabs)
    SideEffect { onSecureChanged(secure) }
    when (destination) {
        RootDestination.StartupNotice, RootDestination.Loading -> {
            LoadingState(stringResource(R.string.common_loading))
        }

        RootDestination.Lock -> {
            slots.lock(lockActions)
        }

        RootDestination.FirstRun -> {
            slots.firstRun(environment, session)
        }

        RootDestination.Tabs -> {
            slots.tabs(session, badge)
        }
    }
}

/** What the shell says instead of running when the real graph could not be opened (nothing is faked, ADR-084). */
@Composable
fun StartupNoticeScreen(
    notice: StartupNotice,
    onRetry: () -> Unit,
) {
    EmptyState(
        icon = painterResource(R.drawable.ic_warning),
        title = stringResource(R.string.startup_title),
        message = stringResource(notice.stringRes()),
        actionTitle = stringResource(R.string.common_retry),
        onAction = onRetry,
        modifier = Modifier.testTag("startup-notice"),
    )
}
