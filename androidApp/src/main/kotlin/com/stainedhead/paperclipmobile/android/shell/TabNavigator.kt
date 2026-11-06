package com.stainedhead.paperclipmobile.android.shell

/**
 * What a screen inside a tab may do to its tab's back stack: push a route, pop itself, or go back to the tab's root
 * (a removed factory leaves its edit screen and the Manage list behind it), or ask the shell to show another tab (a
 * Factory home tile goes to the tab that holds the thing it counts).
 */
class TabNavigator(
    val push: (AppRoute) -> Unit,
    val pop: () -> Unit,
    val popToRoot: () -> Unit,
    val select: (AppTab) -> Unit = {},
)
