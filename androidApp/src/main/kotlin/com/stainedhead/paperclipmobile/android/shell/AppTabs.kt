package com.stainedhead.paperclipmobile.android.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.EmptyState
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.screens.EditFactoryScreen
import com.stainedhead.paperclipmobile.android.screens.FactoriesScreen

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
                AppTab.Factories -> FactoriesScreen(session, navigator)
                else -> Placeholder(tab.title, "tab-root-${tab.name}", tab.icon)
            }
        },
        destination = { route, navigator ->
            when (route) {
                is AppRoute.EditFactory -> EditFactoryScreen(session, route.id, navigator)
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
