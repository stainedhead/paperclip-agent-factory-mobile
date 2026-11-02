package com.stainedhead.paperclipmobile.infrastructure.platform

import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * What a platform shell implements to show the approval page (the iPhone app uses `ASWebAuthenticationSession`).
 * The shell sees the link as plain text because Swift cannot read a [Secret]; it must never log, store or attach it.
 * An implementation calls [open]'s `onShown` once the page is on screen (or could not be) and reports problems to
 * its own screen. It is a callback, not a suspend function, so Swift can implement it without throwing across the
 * boundary. Whether the person approved is found by polling, not here.
 */
interface ApprovalPageOpener {
    fun open(
        url: String,
        onShown: () -> Unit,
    )
}

/** The [PairingBrowser] built on an [ApprovalPageOpener]: the only place the approval link leaves its [Secret]. */
class OpenerPairingBrowser(
    private val opener: ApprovalPageOpener,
) : PairingBrowser {
    override suspend fun open(approvalUrl: Secret) {
        val url = approvalUrl.reveal()
        suspendCancellableCoroutine { continuation ->
            opener.open(url) { if (continuation.isActive) continuation.resume(Unit) }
        }
    }
}
