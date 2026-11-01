package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser

/** Counts how often the approval page was opened. It never keeps the URL, which is a secret. */
class FakePairingBrowser : PairingBrowser {
    var opened = 0
        private set

    override suspend fun open(approvalUrl: Secret) {
        opened += 1
    }
}
