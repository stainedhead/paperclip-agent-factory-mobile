package com.stainedhead.paperclipmobile.android.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.EmptyState

/**
 * The tab shell. The tab roots and the pushed screens arrive with their milestones (2a-4 to 2a-7); until then each
 * shows its title, so the shell, the badge and the navigation can be used and tested end to end.
 */
@Composable
fun AppTabs(inboxBadge: Int) {
    TabShell(
        inboxBadge = inboxBadge,
        tabRoot = { tab, _ ->
            EmptyState(
                icon = painterResource(tab.icon),
                title = stringResource(tab.title),
                message = stringResource(R.string.tab_placeholder),
                modifier = Modifier.testTag("tab-root-${tab.name}"),
            )
        },
        destination = { _, _ ->
            EmptyState(
                icon = painterResource(R.drawable.ic_warning),
                title = stringResource(R.string.tab_placeholder),
            )
        },
    )
}
