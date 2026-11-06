package com.stainedhead.paperclipmobile.android.pairing

import com.stainedhead.paperclipmobile.domain.Secret
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private class FakeLauncher(
    var customTabs: Boolean = true,
    var browser: Boolean = true,
) : PageLauncher {
    val customTabUrls = mutableListOf<String>()
    val browserUrls = mutableListOf<String>()

    override fun openCustomTab(url: String): Boolean {
        if (customTabs) customTabUrls += url
        return customTabs
    }

    override fun openBrowser(url: String): Boolean {
        if (browser) browserUrls += url
        return browser
    }
}

private const val APPROVAL = "https://factory.example.com/approve/abc"

/** AND-020: the approval page opens in a Custom Tab, falls back to a plain browser, and never leaves a wait stuck. */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomTabsPairingBrowserTest {
    private val events = mutableListOf<PairingBrowserEvent>()
    private var finishes = 0

    @BeforeEach
    fun setMain() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterEach
    fun resetMain() = Dispatchers.resetMain()

    private fun browser(
        launcher: PageLauncher,
        allowsInsecureLocal: Boolean = false,
    ) = CustomTabsPairingBrowser(launcher, allowsInsecureLocal) { events += it }

    @Test
    fun `the approval page opens in a Custom Tab and the wait ends when the person comes back`() {
        val launcher = FakeLauncher()
        val browser = browser(launcher)

        browser.show(APPROVAL) { finishes++ }

        launcher.customTabUrls shouldBe listOf(APPROVAL)
        events shouldBe listOf(PairingBrowserEvent.Presented)
        finishes shouldBe 0
        browser.onActivityPaused()
        browser.onActivityResumed()
        finishes shouldBe 1
        events shouldBe listOf(PairingBrowserEvent.Presented, PairingBrowserEvent.Dismissed)
    }

    @Test
    fun `a resume that did not follow leaving the app does not end the wait`() {
        val browser = browser(FakeLauncher())
        browser.show(APPROVAL) { finishes++ }

        browser.onActivityResumed()

        finishes shouldBe 0
    }

    @Test
    fun `coming back twice ends the wait once`() {
        val browser = browser(FakeLauncher())
        browser.show(APPROVAL) { finishes++ }

        repeat(2) {
            browser.onActivityPaused()
            browser.onActivityResumed()
        }

        finishes shouldBe 1
    }

    @Test
    fun `without a Custom Tabs provider the page opens in a plain browser`() {
        val launcher = FakeLauncher(customTabs = false)

        browser(launcher).show(APPROVAL) { finishes++ }

        launcher.browserUrls shouldBe listOf(APPROVAL)
        events shouldBe listOf(PairingBrowserEvent.Presented)
    }

    @Test
    fun `without any browser the attempt ends at once and says so`() {
        val launcher = FakeLauncher(customTabs = false, browser = false)

        browser(launcher).show(APPROVAL) { finishes++ }

        finishes shouldBe 1
        events shouldBe listOf(PairingBrowserEvent.CouldNotPresent)
    }

    @Test
    fun `an address that is not https is refused before any browser is asked`() {
        val launcher = FakeLauncher()

        browser(launcher).show("http://factory.example.com/approve") { finishes++ }

        launcher.customTabUrls shouldBe emptyList()
        launcher.browserUrls shouldBe emptyList()
        finishes shouldBe 1
        events shouldBe listOf(PairingBrowserEvent.CouldNotPresent)
    }

    @Test
    fun `plain http is allowed to the emulator host only where debug builds allow it`() {
        val local = "http://10.0.2.2:3100/approve/abc"

        acceptablePage(local, allowsInsecureLocal = true) shouldBe local
        acceptablePage(local, allowsInsecureLocal = false) shouldBe null
        acceptablePage("http://evil.example.com/x", allowsInsecureLocal = true) shouldBe null
        acceptablePage("javascript:alert(1)", allowsInsecureLocal = true) shouldBe null
        acceptablePage("https:///no-host", allowsInsecureLocal = false) shouldBe null
        acceptablePage("not a url", allowsInsecureLocal = false) shouldBe null
    }

    @Test
    fun `dismissing forgets the pending wait so a later resume ends nothing`() {
        val browser = browser(FakeLauncher())
        browser.show(APPROVAL) { finishes++ }

        browser.dismiss()
        browser.onActivityPaused()
        browser.onActivityResumed()

        finishes shouldBe 0
    }

    @Test
    fun `the port opens the page from a secret and a cancelled pairing dismisses it`() =
        runTest {
            val launcher = FakeLauncher()
            val browser = browser(launcher)

            val opening = async(start = CoroutineStart.UNDISPATCHED) { browser.port.open(Secret.of(APPROVAL)) }
            launcher.customTabUrls shouldBe listOf(APPROVAL)
            browser.onActivityPaused()
            browser.onActivityResumed()
            opening.await()

            val second = async(start = CoroutineStart.UNDISPATCHED) { browser.port.open(Secret.of(APPROVAL)) }
            second.cancelAndJoin()
            browser.onActivityPaused()
            browser.onActivityResumed()
            events.count { it == PairingBrowserEvent.Dismissed } shouldBe 1
        }
}
