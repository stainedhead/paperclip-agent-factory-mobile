package com.stainedhead.paperclipmobile.android.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.screens.AgentDetailScreen
import com.stainedhead.paperclipmobile.android.screens.AgentsScreen
import com.stainedhead.paperclipmobile.android.screens.ApprovalScreen
import com.stainedhead.paperclipmobile.android.screens.CompanyGateScreen
import com.stainedhead.paperclipmobile.android.screens.DecisionScreen
import com.stainedhead.paperclipmobile.android.screens.DiagnosticsScreen
import com.stainedhead.paperclipmobile.android.screens.EditFactoryScreen
import com.stainedhead.paperclipmobile.android.screens.FactoriesScreen
import com.stainedhead.paperclipmobile.android.screens.FactoryHomeScreen
import com.stainedhead.paperclipmobile.android.screens.InboxScreen
import com.stainedhead.paperclipmobile.android.screens.IssueScreen
import com.stainedhead.paperclipmobile.android.screens.ManageFactoriesScreen
import com.stainedhead.paperclipmobile.android.screens.PairedDevicesScreen
import com.stainedhead.paperclipmobile.android.screens.RunViewerScreen
import com.stainedhead.paperclipmobile.android.screens.SettingsScreen
import com.stainedhead.paperclipmobile.android.screens.WorkListScreen
import com.stainedhead.paperclipmobile.presentation.CompanyGate

/**
 * The tab shell with its screens: the five tab roots and every pushed route. Each `when` is exhaustive, so a new tab or
 * route does not compile until it has a screen.
 */
@Composable
fun AppTabs(
    session: GraphSession,
    inboxBadge: Int,
) {
    val app by checkNotNull(session.app) { "The tab shell needs the app holder" }.state.collectAsStateWithLifecycle()
    TabShell(
        inboxBadge = inboxBadge,
        selectionKey = "${app.selectedInstanceId}/${app.selectedCompanyId}",
        tabRoot = { tab, navigator ->
            // A company tab of a factory without a usable company says why, instead of loading for good (F-1).
            if (tab != AppTab.Factories && app.companyGate != CompanyGate.Ready) {
                CompanyGateScreen(session, navigator, app)
                return@TabShell
            }
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
                is AppRoute.Issue -> IssueScreen(session, route, navigator)
                is AppRoute.Approval -> ApprovalScreen(session, route, navigator)
                is AppRoute.Run -> RunViewerScreen(session, route, navigator)
                is AppRoute.Decision -> DecisionScreen(session, route, navigator)
                is AppRoute.EditFactory -> EditFactoryScreen(session, route.id, navigator)
                AppRoute.ManageFactories -> ManageFactoriesScreen(session, navigator)
                AppRoute.Settings -> SettingsScreen(session, navigator)
                AppRoute.PairedDevices -> PairedDevicesScreen(session, navigator)
                AppRoute.Diagnostics -> DiagnosticsScreen(session, navigator)
            }
        },
    )
}
