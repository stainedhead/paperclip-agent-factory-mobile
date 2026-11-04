package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.ErrorBanner
import com.stainedhead.paperclipmobile.android.designsystem.LoadingState
import com.stainedhead.paperclipmobile.android.designsystem.OfflineBanner
import com.stainedhead.paperclipmobile.android.designsystem.ScreenTitleBar
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.UpdatedLabel
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.env.rememberFactoryHomeHolder
import com.stainedhead.paperclipmobile.android.env.rememberSelection
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.android.shell.AppTab
import com.stainedhead.paperclipmobile.android.shell.TabNavigator
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.FactoryHomeIntent
import com.stainedhead.paperclipmobile.presentation.FactoryHomeState
import com.stainedhead.paperclipmobile.presentation.FactoryTilesUi

/**
 * Screen 5, the root of the Factory tab (AND-050). It renders the shared holder's state: the tiles, the month spend
 * against the budget, the spend windows and the runs in progress. It refreshes while on screen through the shared
 * policy (`RefreshWhileVisible`) and never keeps a timer of its own.
 */
@Composable
fun FactoryHomeScreen(
    session: GraphSession,
    navigator: TabNavigator,
) {
    val selection = rememberSelection(session)
    if (selection == null) {
        LoadingState(stringResource(R.string.common_loading), Modifier.testTag("factory-loading"))
        return
    }
    val holder = rememberFactoryHomeHolder(session, selection)
    val state by holder.state.collectAsStateWithLifecycle()
    RefreshWhileVisible(session, holder.holder.refreshTarget)
    FactoryHomeContent(
        state,
        onRefresh = { holder.onIntent(FactoryHomeIntent.Refresh) },
        onOpenTab = navigator.select,
        onOpenRun = { navigator.push(AppRoute.Run(it)) },
    )
}

/** What Factory home shows for a state. Stateless: tests and screenshots give it any state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FactoryHomeContent(
    state: FactoryHomeState,
    onRefresh: () -> Unit,
    onOpenTab: (AppTab) -> Unit,
    onOpenRun: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().testTag("factory-root")) {
        ScreenTitleBar(
            stringResource(R.string.tab_factory),
            Modifier.padding(horizontal = Spacing.page),
            isLarge = true,
        )
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.page, vertical = Spacing.roomy),
                verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
            ) {
                val tiles = state.tiles
                if (tiles != null && state.phase == DetailPhase.Content) {
                    FactoryBody(state, tiles, onRefresh, onOpenTab, onOpenRun)
                } else {
                    DetailUnavailable(
                        state.phase,
                        state.error,
                        state.isOffline,
                        onRetry = onRefresh,
                        onSignInAgain = { onOpenTab(AppTab.Factories) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FactoryBody(
    state: FactoryHomeState,
    tiles: FactoryTilesUi,
    onRefresh: () -> Unit,
    onOpenTab: (AppTab) -> Unit,
    onOpenRun: (String) -> Unit,
) {
    UpdatedLabel(state.updatedAtEpochMillis)
    if (state.isOffline) OfflineBanner(state.updatedAtEpochMillis)
    state.error?.let { ErrorBanner(it, onRetry = onRefresh, onSignInAgain = { onOpenTab(AppTab.Factories) }) }
    SpendCard(state, tiles)
    TileGrid(tiles, onOpenTab)
    RunningNow(state, onOpenRun)
}
