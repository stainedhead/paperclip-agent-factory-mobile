package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.Secret
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
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

    /** Runs a dispatched block at once on the calling thread, and counts how many were dispatched. */
    private class CountingDispatcher : CoroutineDispatcher() {
        var dispatched = 0
            private set

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            dispatched += 1
            block.run()
        }
    }

    @Test
    fun `F-B-8 cancelling the wait dismisses the page and finishing does not`() =
        runTest {
            var dismissed = 0
            var finish: (() -> Unit)? = null
            val browser = CallbackPairingBrowser({ _, finished -> finish = finished }, { dismissed += 1 }, null)

            val abandoned =
                async(start = CoroutineStart.UNDISPATCHED) { browser.open(Secret.of("https://h/cli-auth/1")) }
            abandoned.cancelAndJoin()
            dismissed shouldBe 1

            val completed =
                async(start = CoroutineStart.UNDISPATCHED) { browser.open(Secret.of("https://h/cli-auth/2")) }
            finish?.invoke()
            completed.await()
            dismissed shouldBe 1
        }

    @Test
    fun `F-B-8 show and dismiss run on the dispatcher the app chose`() =
        runTest {
            val dispatcher = CountingDispatcher()
            var shownOnIt = false
            var dismissedOnIt = false
            val browser =
                CallbackPairingBrowser(
                    { _, _ -> shownOnIt = dispatcher.dispatched > 0 },
                    { dismissedOnIt = dispatcher.dispatched > 1 },
                    dispatcher,
                )

            val opening = async(start = CoroutineStart.UNDISPATCHED) { browser.open(Secret.of("https://h/cli-auth/1")) }
            testScheduler.runCurrent()
            shownOnIt shouldBe true
            opening.cancelAndJoin()
            testScheduler.runCurrent()

            dismissedOnIt shouldBe true
        }
}
