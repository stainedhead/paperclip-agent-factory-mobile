package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import com.stainedhead.paperclipmobile.infrastructure.api.CallbackPairingBrowser

/** The ways an app can supply the [PairingBrowser] of the live graph. */
object PairingBrowsers {
    /**
     * A browser made from a callback: `show` receives the approval address (a secret: open it, do not log or keep
     * it) and a `finished` function to call when the page was presented or dismissed.
     */
    fun fromCallback(show: (approvalUrl: String, finished: () -> Unit) -> Unit): PairingBrowser =
        CallbackPairingBrowser(show)
}
