package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.testing.fakes.FakePairingBrowser
import io.kotest.assertions.throwables.shouldThrow
import kotlin.test.Test

/** AD-033, AD-040: Android has no live platform yet, so the graph is refused instead of run on a fake secret store. */
class LiveAndroidDefaultsTest {
    @Test
    fun `the live graph is refused on Android until the Android app phase`() {
        shouldThrow<IllegalStateException> {
            PaperclipCore.createLiveGraph(allowInsecureLocal = false, FakePairingBrowser())
        }
    }
}
