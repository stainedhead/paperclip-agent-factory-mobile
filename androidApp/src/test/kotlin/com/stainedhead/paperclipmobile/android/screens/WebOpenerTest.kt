package com.stainedhead.paperclipmobile.android.screens

import com.stainedhead.paperclipmobile.android.pairing.PageLauncher
import io.kotest.matchers.shouldBe
import org.junit.Test

/** A launcher that records what it was asked to open; [tabs] says whether a Custom Tabs provider exists. */
class RecordingLauncher(
    private val tabs: Boolean = true,
    private val browser: Boolean = true,
) : PageLauncher {
    val tabbed = mutableListOf<String>()
    val browsed = mutableListOf<String>()

    override fun openCustomTab(url: String): Boolean {
        if (tabs) tabbed += url
        return tabs
    }

    override fun openBrowser(url: String): Boolean {
        if (browser) browsed += url
        return browser
    }
}

/** "Open in web app" opens an https address only, in a Custom Tab when there is one, and says when nothing opened. */
class WebOpenerTest {
    @Test
    fun anHttpsAddressOpensInACustomTab() {
        val launcher = RecordingLauncher()

        WebOpener(launcher).open("https://paperclip.example.ts.net/issues/PAP-1204") shouldBe true

        launcher.tabbed shouldBe listOf("https://paperclip.example.ts.net/issues/PAP-1204")
        launcher.browsed shouldBe emptyList()
    }

    @Test
    fun withoutACustomTabsProviderTheBrowserOpensIt() {
        val launcher = RecordingLauncher(tabs = false)

        WebOpener(launcher).open("https://paperclip.example.ts.net/issues/PAP-1204") shouldBe true

        launcher.browsed shouldBe listOf("https://paperclip.example.ts.net/issues/PAP-1204")
    }

    @Test
    fun anythingButHttpsIsRefusedAndNothingIsLaunched() {
        val launcher = RecordingLauncher()
        val opener = WebOpener(launcher)

        for (
        refused in
        listOf(
            "http://paperclip.example.ts.net/issues/PAP-1204",
            "http://localhost:3100/issues/PAP-1204",
            "javascript:alert(1)",
            "file:///sdcard/secret",
            "intent://scan/#Intent;scheme=zxing;end",
            "https:///no-host",
            "not a url",
            "",
        )
        ) {
            opener.open(refused) shouldBe false
        }
        opener.open(null) shouldBe false

        launcher.tabbed shouldBe emptyList()
        launcher.browsed shouldBe emptyList()
    }

    @Test
    fun whenNothingCanShowThePageItSaysSo() {
        WebOpener(RecordingLauncher(tabs = false, browser = false)).open("https://paperclip.example.ts.net/x") shouldBe
            false
    }
}
