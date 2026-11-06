package com.stainedhead.paperclipmobile.android.pairing

import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import com.stainedhead.paperclipmobile.umbrella.PairingBrowsers

/**
 * Shows the pairing approval page for the shared pairing use case (PRD FR-020, AND-020), in a Custom Tab; it is never a
 * web view and is never given credentials.
 *
 * - The link arrives as text and is opened once and forgotten: never logged, stored or put in an event.
 * - The shared use case starts polling once `open` returns, so [port] returns when the person comes back to the app
 *   (the activity resumes after having paused) rather than at once: a process left behind a browser may be frozen.
 *   Whether they approved is found by the use case polling the server.
 * - No Custom Tabs provider opens a plain browser; nothing that can show the page, or an address that must not open,
 *   ends the attempt at once and reports [PairingBrowserEvent.CouldNotPresent], so the pairing on screen is cancelled
 *   instead of waiting for nothing.
 * - A Custom Tab cannot be closed from the app, so [dismiss] only forgets the wait.
 */
class CustomTabsPairingBrowser(
    private val launcher: PageLauncher,
    private val allowsInsecureLocal: Boolean,
    private val onEvent: (PairingBrowserEvent) -> Unit,
) {
    private var pending: (() -> Unit)? = null
    private var leftTheApp = false

    /** The port the live graph is given; the page is shown and dismissed on the main thread. */
    val port: PairingBrowser = PairingBrowsers.onMainThread(::show, ::dismiss)

    internal fun show(
        url: String,
        finished: () -> Unit,
    ) {
        val page = acceptablePage(url, allowsInsecureLocal)
        if (page != null && (launcher.openCustomTab(page) || launcher.openBrowser(page))) {
            pending = finished
            leftTheApp = false
            onEvent(PairingBrowserEvent.Presented)
        } else {
            onEvent(PairingBrowserEvent.CouldNotPresent)
            finished()
        }
    }

    internal fun dismiss() {
        pending = null
    }

    /** The activity paused: if a page is up, the person has gone to it. */
    fun onActivityPaused() {
        if (pending != null) leftTheApp = true
    }

    /** The activity resumed: coming back from the page ends the wait, once. */
    fun onActivityResumed() {
        val done = pending
        if (done != null && leftTheApp) {
            pending = null
            leftTheApp = false
            onEvent(PairingBrowserEvent.Dismissed)
            done()
        }
    }
}
