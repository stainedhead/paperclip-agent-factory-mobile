package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.Secret
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `F-B-8 the main thread browser shows and dismisses on the main dispatcher`() {
        val main = StandardTestDispatcher()
        Dispatchers.setMain(main)
        try {
            runTest(main) {
                var shown = 0
                var dismissed = 0
                val browser = PairingBrowsers.onMainThread({ _, _ -> shown += 1 }, { dismissed += 1 })

                val opening =
                    async(start = CoroutineStart.UNDISPATCHED) { browser.open(Secret.of("https://h/cli-auth/1")) }
                shown shouldBe 0
                runCurrent()
                shown shouldBe 1
                opening.cancelAndJoin()
                runCurrent()
                dismissed shouldBe 1
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
