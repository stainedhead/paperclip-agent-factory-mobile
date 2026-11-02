package com.stainedhead.paperclipmobile.infrastructure.platform

import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import com.stainedhead.paperclipmobile.testing.contract.PairingBrowserContract
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private class RecordingOpener : ApprovalPageOpener {
    val urls = mutableListOf<String>()

    override suspend fun open(url: String) {
        urls += url
    }
}

/** The bridge a platform shell implements (Swift can read no [com.stainedhead.paperclipmobile.domain.Secret]). */
class OpenerPairingBrowserTest : PairingBrowserContract() {
    private val opener = RecordingOpener()

    override fun create(): PairingBrowser = OpenerPairingBrowser(opener)

    override fun openedUrls(browser: PairingBrowser): List<String> = opener.urls

    @Test
    fun `the bridge passes the pairing browser contract`() = runTest { runAll() }
}
