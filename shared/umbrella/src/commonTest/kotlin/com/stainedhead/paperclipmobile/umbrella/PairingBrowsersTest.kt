package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.Secret
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** AD-040: the app-facing way to supply the pairing browser opens the address it is given. */
class PairingBrowsersTest {
    @Test
    fun `a browser made from a callback passes the approval address on and returns when finished`() =
        runTest {
            var shown: String? = null
            val browser =
                PairingBrowsers.fromCallback { url, finished ->
                    shown = url
                    finished()
                }

            browser.open(Secret.of("https://h/cli-auth/1?token=pcp_cli_auth_TESTONLY"))

            shown shouldBe "https://h/cli-auth/1?token=pcp_cli_auth_TESTONLY"
        }
}
