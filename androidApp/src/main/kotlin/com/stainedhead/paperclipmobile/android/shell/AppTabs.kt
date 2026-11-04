package com.stainedhead.paperclipmobile.android.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.EmptyState
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.screens.AgentDetailScreen
import com.stainedhead.paperclipmobile.android.screens.AgentsScreen
import com.stainedhead.paperclipmobile.android.screens.ApprovalScreen
import com.stainedhead.paperclipmobile.android.screens.DecisionScreen
import com.stainedhead.paperclipmobile.android.screens.DiagnosticsScreen
import com.stainedhead.paperclipmobile.android.screens.EditFactoryScreen
import com.stainedhead.paperclipmobile.android.screens.FactoriesScreen
import com.stainedhead.paperclipmobile.android.screens.FactoryHomeScreen
import com.stainedhead.paperclipmobile.android.screens.InboxScreen
import com.stainedhead.paperclipmobile.android.screens.ManageFactoriesScreen
import com.stainedhead.paperclipmobile.android.screens.PairedDevicesScreen
import com.stainedhead.paperclipmobile.android.screens.RunViewerScreen
import com.stainedhead.paperclipmobile.android.screens.SettingsScreen
import com.stainedhead.paperclipmobile.android.screens.WorkListScreen

/**
 * The tab shell with its screens. The Factories family is here (2a-4); the other tab roots and pushed screens arrive
 * with their milestones (2a-5 to 2a-7) and show their title until then, so the shell, the badge and the navigation can
 * be used and tested end to end.
 */
@Composable
fun AppTabs(
    session: GraphSession,
    inboxBadge: Int,
) {
    TabShell(
        inboxBadge = inboxBadge,
        tabRoot = { tab, navigator ->
            when (tab) {
                AppTab.Inbox -> InboxScreen(session, navigator)
                AppTab.Factory -> FactoryHomeScreen(session, navigator)
                AppTab.Work -> WorkListScreen(session, navigator)
                AppTab.Agents -> AgentsScreen(session, navigator)
                AppTab.Factories -> FactoriesScreen(session, navigator)
            }
        },
        destination = { route, navigator ->
            when (route) {
                is AppRoute.Agent -> AgentDetailScreen(session, route, navigator)
                is AppRoute.Approval -> ApprovalScreen(session, route, navigator)
                is AppRoute.Run -> RunViewerScreen(session, route, navigator)
                is AppRoute.Decision -> DecisionScreen(session, route, navigator)
                is AppRoute.EditFactory -> EditFactoryScreen(session, route.id, navigator)
                AppRoute.ManageFactories -> ManageFactoriesScreen(session, navigator)
                AppRoute.Settings -> SettingsScreen(session, navigator)
                AppRoute.PairedDevices -> PairedDevicesScreen(session, navigator)
                AppRoute.Diagnostics -> DiagnosticsScreen(session, navigator)
                else -> Placeholder(R.string.tab_placeholder, "pushed-placeholder", R.drawable.ic_warning)
            }
        },
    )
}

@Composable
private fun Placeholder(
    title: Int,
    tag: String,
    icon: Int,
) {
    EmptyState(
        icon = painterResource(icon),
        title = stringResource(title),
        message = stringResource(R.string.tab_placeholder),
        modifier = Modifier.testTag(tag),
    )
}
