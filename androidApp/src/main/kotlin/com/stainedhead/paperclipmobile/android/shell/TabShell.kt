package com.stainedhead.paperclipmobile.android.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.enterTransition
import com.stainedhead.paperclipmobile.android.designsystem.exitTransition
import com.stainedhead.paperclipmobile.android.designsystem.rememberReducedMotion

/**
 * Five tabs, one back stack each (AND-012). A tab keeps its pushed screens while another tab is shown; selecting the
 * selected tab again returns to its root, as the platform does. The Inbox badge is [inboxBadge], the server's count;
 * the shell never computes it.
 *
 * [selectionKey] names the selected factory and company: when it changes every tab goes back to its root, so no pushed
 * screen outlives the selection it was opened for.
 *
 * [tabRoot] draws a tab's first screen and [destination] a pushed route; both get the tab's [TabNavigator].
 */
@Composable
fun TabShell(
    inboxBadge: Int,
    tabRoot: @Composable (tab: AppTab, navigator: TabNavigator) -> Unit,
    destination: @Composable (route: AppRoute, navigator: TabNavigator) -> Unit,
    modifier: Modifier = Modifier,
    selectionKey: String? = null,
) {
    var selected by rememberSaveable { mutableStateOf(AppTab.Inbox) }
    val controllers = AppTab.entries.associateWith { rememberNavController() }
    val stateHolder = rememberSaveableStateHolder()
    // A pushed detail screen addresses the factory and company it was opened in; a new selection starts every tab over
    // (R-006). The key a recreation restores is the one the stack was built for, so rotation keeps the stack.
    var openedFor by rememberSaveable { mutableStateOf(selectionKey) }
    LaunchedEffect(selectionKey) {
        if (openedFor != selectionKey) {
            openedFor = selectionKey
            // A tab never shown has no graph yet and nothing pushed.
            controllers.values.filter { it.currentBackStackEntry != null }.forEach {
                it.popBackStack(TabRoot, inclusive = false)
            }
        }
    }
    Scaffold(
        modifier = modifier,
        containerColor = AppTheme.colors.background,
        bottomBar = {
            TabBar(selected, inboxBadge) { tab ->
                if (tab == selected) {
                    controllers.getValue(tab).popBackStack(TabRoot, inclusive = false)
                } else {
                    selected = tab
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            stateHolder.SaveableStateProvider(selected.name) {
                TabHost(selected, controllers.getValue(selected), tabRoot, destination) { selected = it }
            }
        }
    }
}

@Composable
private fun TabHost(
    tab: AppTab,
    controller: NavHostController,
    tabRoot: @Composable (AppTab, TabNavigator) -> Unit,
    destination: @Composable (AppRoute, TabNavigator) -> Unit,
    onSelect: (AppTab) -> Unit,
) {
    val select by rememberUpdatedState(onSelect)
    val navigator =
        remember(controller) {
            TabNavigator(
                push = { route -> controller.navigate(route) },
                pop = { controller.popBackStack() },
                popToRoot = { controller.popBackStack(TabRoot, inclusive = false) },
                select = { tab -> select(tab) },
            )
        }
    val reduced = rememberReducedMotion()
    NavHost(
        controller,
        startDestination = TabRoot,
        enterTransition = { enterTransition(reduced) },
        exitTransition = { exitTransition(reduced) },
        popEnterTransition = { enterTransition(reduced) },
        popExitTransition = { exitTransition(reduced) },
    ) {
        composable<TabRoot> { tabRoot(tab, navigator) }
        screen<AppRoute.Decision>(navigator, destination)
        screen<AppRoute.Approval>(navigator, destination)
        screen<AppRoute.Issue>(navigator, destination)
        screen<AppRoute.Agent>(navigator, destination)
        screen<AppRoute.Run>(navigator, destination)
        screen<AppRoute.ManageFactories>(navigator, destination)
        screen<AppRoute.EditFactory>(navigator, destination)
        screen<AppRoute.Settings>(navigator, destination)
        screen<AppRoute.PairedDevices>(navigator, destination)
        screen<AppRoute.Diagnostics>(navigator, destination)
    }
}

private inline fun <reified R : AppRoute> NavGraphBuilder.screen(
    navigator: TabNavigator,
    noinline destination: @Composable (AppRoute, TabNavigator) -> Unit,
) {
    composable<R> { entry -> destination(entry.toRoute<R>(), navigator) }
}

private const val TAB_FONT_SCALE_CAP = 1.3f

/**
 * Five labels share the width, so the bar's text stops growing at 1.3 (Material's guidance for navigation bars); the
 * icons, the badge and every screen keep the person's full setting. A word is never broken across lines.
 */
@Composable
private fun TabLabel(title: String) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, minOf(density.fontScale, TAB_FONT_SCALE_CAP)),
    ) {
        Text(title, style = AppType.micro, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun TabBar(
    selected: AppTab,
    inboxBadge: Int,
    onSelect: (AppTab) -> Unit,
) {
    val colors = AppTheme.colors
    NavigationBar(containerColor = colors.surface) {
        for (tab in AppTab.entries) {
            val badge = tab.badge(inboxBadge)
            val spoken = badge?.let { pluralStringResource(R.plurals.tab_badge_value, it, it) }
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier =
                    Modifier
                        .testTag("tab-${tab.name}")
                        .semantics { if (spoken != null) stateDescription = spoken },
                icon = {
                    BadgedBox(
                        badge = {
                            if (badge != null) {
                                Badge(containerColor = colors.destructive, contentColor = colors.onAccent) {
                                    Text(badge.toString(), style = AppType.micro)
                                }
                            }
                        },
                    ) { Icon(painterResource(tab.icon), contentDescription = null) }
                },
                label = { TabLabel(stringResource(tab.title)) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.accent,
                        selectedTextColor = colors.accent,
                        indicatorColor = colors.accentSoft,
                        unselectedIconColor = colors.textSecondary,
                        unselectedTextColor = colors.textSecondary,
                    ),
            )
        }
    }
}
