package com.stainedhead.paperclipmobile.infrastructure.platform

import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser

/**
 * What a platform shell implements to show the approval page (the iPhone app uses `ASWebAuthenticationSession`).
 * The shell sees the link as plain text because Swift cannot read a [Secret]; it must never log, store or attach it.
 * An implementation completes normally once the page is on screen and reports problems to its own screen: an error
 * thrown across this boundary would end the app. Whether the person approved is found by polling, not here.
 */
interface ApprovalPageOpener {
    suspend fun open(url: String)
}

/** The [PairingBrowser] built on an [ApprovalPageOpener]: the only place the approval link leaves its [Secret]. */
class OpenerPairingBrowser(
    private val opener: ApprovalPageOpener,
) : PairingBrowser {
    override suspend fun open(approvalUrl: Secret) = opener.open(approvalUrl.reveal())
}
