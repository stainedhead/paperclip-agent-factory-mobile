package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.Secret
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** AD-040: a platform app that cannot implement a suspend interface cleanly supplies a callback instead. */
class CallbackPairingBrowserTest {
    @Test
    fun `open hands the approval address to the callback and returns when the callback finishes`() =
        runTest {
            var shown: String? = null
            var finish: (() -> Unit)? = null
            val browser =
                CallbackPairingBrowser { url, finished ->
                    shown = url
                    finish = finished
                }

            val opening = async(start = CoroutineStart.UNDISPATCHED) { browser.open(Secret.of("https://h/cli-auth/1")) }

            shown shouldBe "https://h/cli-auth/1"
            opening.isCompleted shouldBe false
            finish?.invoke()
            opening.await()
            finish?.invoke() // a second call is harmless
        }

    @Test
    fun `cancelling the open ends it without waiting for the callback`() =
        runTest {
            val browser = CallbackPairingBrowser { _, _ -> }

            val opening = async(start = CoroutineStart.UNDISPATCHED) { browser.open(Secret.of("https://h/cli-auth/1")) }
            opening.cancelAndJoin()

            opening.isCancelled shouldBe true
        }
}
