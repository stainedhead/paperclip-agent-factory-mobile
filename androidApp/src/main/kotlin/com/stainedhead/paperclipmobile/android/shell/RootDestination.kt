package com.stainedhead.paperclipmobile.android.shell

import com.stainedhead.paperclipmobile.presentation.AppState
import com.stainedhead.paperclipmobile.presentation.LockState
import com.stainedhead.paperclipmobile.presentation.StartupNotice

/** What the app shows (the Android twin of the iPhone `RootView.content`). */
enum class RootDestination {
    StartupNotice,
    Loading,
    Lock,
    FirstRun,
    Tabs,
}

/**
 * Chooses between the startup notice, loading, the lock, first run when there is no factory yet (or while the flow that
 * adds one is still on its pairing and secure steps), and the tab shell. The order is the rule: nothing shows behind a
 * notice or the lock.
 */
fun rootDestination(
    notice: StartupNotice?,
    app: AppState,
    firstRunInProgress: Boolean,
): RootDestination =
    when {
        notice != null -> RootDestination.StartupNotice
        !app.isLoaded -> RootDestination.Loading
        app.lock == LockState.Locked -> RootDestination.Lock
        !app.hasFactories || firstRunInProgress -> RootDestination.FirstRun
        else -> RootDestination.Tabs
    }

/**
 * The lock screen and the pairing steps keep the window out of screenshots and the recents thumbnail (AND-014): the
 * first-run pairing step ([pairing]) and a re-pair waiting on a factory's edit screen ([pairingOnTabs]).
 */
fun screenIsSecure(
    destination: RootDestination,
    pairing: Boolean,
    pairingOnTabs: Boolean = false,
): Boolean =
    destination == RootDestination.Lock ||
        (destination == RootDestination.FirstRun && pairing) ||
        (destination == RootDestination.Tabs && pairingOnTabs)
