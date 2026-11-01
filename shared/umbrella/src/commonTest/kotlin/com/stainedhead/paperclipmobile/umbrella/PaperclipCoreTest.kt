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

class PaperclipGraphWiringTest {
    @Test
    fun `the graph builds every slice 1a-1 state holder and they share one set of fakes`() {
        val graph = PaperclipCore.createGraph(allowInsecureLocal = false)

        val app = graph.createAppStateHolder()
        val factories = graph.createFactoriesStateHolder()
        val manage = graph.createManageFactoriesStateHolder()
        val settings = graph.createSettingsStateHolder()
        val add = graph.createEditInstanceStateHolder(null)
        val edit = graph.createEditInstanceStateHolder("missing")

        app.state.value.hasFactories shouldBe false
        factories.state.value.isEmpty shouldBe true
        manage.state.value.rows shouldBe emptyList()
        settings.state.value.destructiveActionAuthAlwaysOn shouldBe true
        add.state.value.canSave shouldBe false
        edit.state.value.instanceId shouldBe "missing"
        listOf(app::close, factories::close, manage::close, settings::close, add::close, edit::close).forEach { it() }
    }
}
