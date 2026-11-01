package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.presentation.FirstRunIntent
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class PaperclipCoreTest {
    @Test
    fun `the graph builds a first-run state holder that validates addresses`() {
        val firstRun = PaperclipCore.createGraph(allowInsecureLocal = false).createFirstRunStateHolder()

        firstRun.onIntent(FirstRunIntent.AddressChanged("https://paperclip.example.com"))

        firstRun.state.value.canContinue shouldBe true
    }

    @Test
    fun `a debug graph accepts local http and a release graph refuses it`() {
        val debug = PaperclipCore.createGraph(allowInsecureLocal = true).createFirstRunStateHolder()
        val release = PaperclipCore.createGraph(allowInsecureLocal = false).createFirstRunStateHolder()

        debug.onIntent(FirstRunIntent.AddressChanged("http://localhost:3100"))
        release.onIntent(FirstRunIntent.AddressChanged("http://localhost:3100"))

        debug.state.value.canContinue shouldBe true
        release.state.value.canContinue shouldBe false
    }

    @Test
    fun `each call creates an independent state holder`() {
        val graph = PaperclipCore.createGraph(allowInsecureLocal = false)
        val first = graph.createFirstRunStateHolder()
        val second = graph.createFirstRunStateHolder()

        first.onIntent(FirstRunIntent.AddressChanged("https://example.com"))

        second.state.value.address shouldBe ""
    }
}
