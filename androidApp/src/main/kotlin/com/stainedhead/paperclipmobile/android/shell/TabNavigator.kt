package com.stainedhead.paperclipmobile.android.shell

/**
 * What a screen inside a tab may do to its tab's back stack: push a route, pop itself, or go back to the tab's root
 * (a removed factory leaves its edit screen and the Manage list behind it).
 */
class TabNavigator(
    val push: (AppRoute) -> Unit,
    val pop: () -> Unit,
    val popToRoot: () -> Unit,
)
