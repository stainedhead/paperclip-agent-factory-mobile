package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import com.stainedhead.paperclipmobile.infrastructure.api.CallbackPairingBrowser
import kotlinx.coroutines.Dispatchers

/** The ways an app can supply the [PairingBrowser] of the live graph. */
object PairingBrowsers {
    /**
     * A browser made from a callback: `show` receives the approval address (a secret: open it, do not log or keep
     * it) and a `finished` function to call when the page was presented or dismissed. `show` runs on a background
     * dispatcher: prefer [onMainThread], which is safe for UIKit.
     */
    fun fromCallback(show: (approvalUrl: String, finished: () -> Unit) -> Unit): PairingBrowser =
        CallbackPairingBrowser(show)

    /**
     * Like [fromCallback] but `show` and `dismiss` run on the main thread, which is where a system browser session
     * (`ASWebAuthenticationSession`) must be presented, and `dismiss` is called when the pairing is cancelled so the
     * page does not stay on screen after the person gave up (review1b F-B-8).
     */
    fun onMainThread(
        show: (approvalUrl: String, finished: () -> Unit) -> Unit,
        dismiss: () -> Unit,
    ): PairingBrowser = CallbackPairingBrowser(show, dismiss, Dispatchers.Main)
}
