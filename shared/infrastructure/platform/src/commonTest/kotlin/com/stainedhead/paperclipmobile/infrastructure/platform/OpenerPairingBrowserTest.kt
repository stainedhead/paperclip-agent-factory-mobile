package com.stainedhead.paperclipmobile.infrastructure.platform

import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import com.stainedhead.paperclipmobile.testing.contract.PairingBrowserContract
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private class RecordingOpener : ApprovalPageOpener {
    val urls = mutableListOf<String>()

    override fun open(
        url: String,
        onShown: () -> Unit,
    ) {
        urls += url
        onShown()
    }
}

/** The bridge a platform shell implements (Swift can read no [com.stainedhead.paperclipmobile.domain.Secret]). */
class OpenerPairingBrowserTest : PairingBrowserContract() {
    private val opener = RecordingOpener()

    override fun create(): PairingBrowser = OpenerPairingBrowser(opener)

    override fun openedUrls(browser: PairingBrowser): List<String> = opener.urls

    @Test
    fun `the bridge passes the pairing browser contract`() = runTest { runAll() }

    @Test
    fun `a call that is shown twice still returns once`() =
        runTest {
            val twice =
                object : ApprovalPageOpener {
                    override fun open(
                        url: String,
                        onShown: () -> Unit,
                    ) {
                        onShown()
                        onShown()
                    }
                }
            OpenerPairingBrowser(twice).open(
                com.stainedhead.paperclipmobile.domain.Secret
                    .of("https://a.example/x"),
            )
        }
}
