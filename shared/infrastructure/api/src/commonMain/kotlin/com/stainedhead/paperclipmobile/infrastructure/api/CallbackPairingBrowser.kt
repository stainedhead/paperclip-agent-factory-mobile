package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * A [PairingBrowser] built from a callback, for apps that show the approval page in a system browser session (iOS
 * `ASWebAuthenticationSession`, an Android Custom Tab). Swift cannot read a [Secret] (`reveal` is hidden from it) and
 * cannot cleanly implement a Kotlin suspend interface (SKIE hides its method behind a `__` name), so the app passes a
 * closure instead: [show] receives the approval address as text and a `finished` function to call when the page was
 * presented or dismissed. The address is a secret: the callback must not log or keep it. This class is the only place
 * that reveals it. Cancelling the pairing cancels the wait.
 */
class CallbackPairingBrowser(
    private val show: (approvalUrl: String, finished: () -> Unit) -> Unit,
) : PairingBrowser {
    override suspend fun open(approvalUrl: Secret) {
        suspendCancellableCoroutine { waiting ->
            show(approvalUrl.reveal()) { if (waiting.isActive) waiting.resume(Unit) }
        }
    }
}
