package com.stainedhead.paperclipmobile.android.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf

/** How a screen inside the tabs tells the root that a pairing is waiting, so the window is kept out of screenshots. */
val LocalPairingSecurity = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

/**
 * Reports [waiting] to the root for as long as this is in the composition, and false when it leaves, so a screen
 * that is closed or recreated never leaves the window hidden (AND-014: the pairing screen is never captured).
 */
@Composable
fun ReportPairingWait(waiting: Boolean) {
    val report = LocalPairingSecurity.current
    DisposableEffect(waiting) {
        report(waiting)
        onDispose { report(false) }
    }
}
