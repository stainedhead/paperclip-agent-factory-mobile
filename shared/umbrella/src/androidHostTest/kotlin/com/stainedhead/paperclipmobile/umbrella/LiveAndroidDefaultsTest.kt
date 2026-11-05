package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.testing.fakes.FakePairingBrowser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

/**
 * AD-033, AD-040: the shared `createLiveGraph(allowInsecureLocal, pairingBrowser)` has no `Context`, so on Android it
 * is refused (never run on a fake secret store) and the message points at the entry point that has one.
 */
class LiveAndroidDefaultsTest {
    @Test
    fun `the context free live graph is refused on Android and names AndroidLiveGraph`() {
        val failure =
            shouldThrow<IllegalStateException> {
                PaperclipCore.createLiveGraph(allowInsecureLocal = false, FakePairingBrowser())
            }

        failure.message.orEmpty() shouldContain "AndroidLiveGraph.openLiveGraph"
    }
}
