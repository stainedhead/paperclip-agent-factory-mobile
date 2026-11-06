package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * A [PairingBrowser] built from a callback, for apps that show the approval page in a system browser session (iOS
 * `ASWebAuthenticationSession`, an Android Custom Tab). Swift cannot read a [Secret] (`reveal` is hidden from it) and
 * cannot cleanly implement a Kotlin suspend interface (SKIE hides its method behind a `__` name), so the app passes a
 * closure instead: [show] receives the approval address as text and a `finished` function to call when the page was
 * presented or dismissed. The address is a secret: the callback must not log or keep it. This class is the only place
 * that reveals it. Cancelling the pairing cancels the wait and calls [dismiss] so the page goes away (review1b F-B-8).
 *
 * Threads: with a [dispatcher] both [show] and [dismiss] run on it (an app passes its main dispatcher, because a
 * system browser session may only be presented from the main thread). Without one (the single argument form) they
 * run on the dispatcher of the pairing, which is a background one in the live graph, and the app must hop itself.
 */
class CallbackPairingBrowser(
    private val show: (approvalUrl: String, finished: () -> Unit) -> Unit,
    private val dismiss: () -> Unit,
    private val dispatcher: CoroutineDispatcher?,
) : PairingBrowser {
    /** [show] runs on whatever dispatcher the pairing runs on and nothing is dismissed: see the three argument form. */
    constructor(show: (approvalUrl: String, finished: () -> Unit) -> Unit) : this(show, {}, null)

    override suspend fun open(approvalUrl: Secret) {
        val target = dispatcher
        if (target == null) wait(approvalUrl) else withContext(target) { wait(approvalUrl) }
    }

    private suspend fun wait(approvalUrl: Secret) {
        suspendCancellableCoroutine { waiting ->
            // A person who abandons the pairing must not leave the page up: it could still approve a dead pairing.
            waiting.invokeOnCancellation { dismissOnTheChosenDispatcher() }
            show(approvalUrl.reveal()) { if (waiting.isActive) waiting.resume(Unit) }
        }
    }

    private fun dismissOnTheChosenDispatcher() {
        val target = dispatcher
        if (target == null) dismiss() else CoroutineScope(target).launch { dismiss() }
    }
}
