package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser

/** Records each approval URL it is asked to open. Fixtures use test-only tokens; never put a real link in one. */
class FakePairingBrowser : PairingBrowser {
    private val urls = mutableListOf<String>()

    val openedUrls: List<String> get() = urls.toList()

    val opened: Int get() = urls.size

    override suspend fun open(approvalUrl: Secret) {
        urls += approvalUrl.reveal()
    }
}
