package com.stainedhead.paperclipmobile.android.shell

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import com.stainedhead.paperclipmobile.android.env.AppEnvironment
import com.stainedhead.paperclipmobile.android.env.AppGraphProvider
import com.stainedhead.paperclipmobile.android.env.GraphMode
import com.stainedhead.paperclipmobile.android.env.ScriptedProvider
import com.stainedhead.paperclipmobile.android.env.demoOpen
import com.stainedhead.paperclipmobile.android.env.firstRunOpen
import com.stainedhead.paperclipmobile.android.env.unavailable
import com.stainedhead.paperclipmobile.domain.IdlePeriod
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.presentation.AppIntent
import com.stainedhead.paperclipmobile.presentation.SettingsIntent
import com.stainedhead.paperclipmobile.umbrella.AndroidGraphOpen
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val WAIT_MILLIS = 10_000L

/** AND-010, AND-012: the root chooses the notice, the lock, first run or the tabs from the shared holders' state. */
@RunWith(AndroidJUnit4::class)
class RootScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var environment: AppEnvironment? = null
    private var autoUnlock = true

    @After
    fun tearDown() {
        environment?.shutdown()
        scope.cancel()
    }

    private fun show(
        provider: AppGraphProvider,
        secure: MutableList<Boolean> = mutableListOf(),
        unlock: Boolean = true,
        openOn: CoroutineDispatcher? = null,
    ) {
        val env =
            AppEnvironment(provider, scope, demoEntryAvailable = true, openOn = openOn).also { environment = it }
        autoUnlock = unlock
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                RootScreen(
                    environment = env,
                    lock = { actions ->
                        Text("lock screen")
                        actions.issue?.let { Text("issue $it") }
                        if (autoUnlock) LaunchedEffect(Unit) { actions.onUnlock() }
                    },
                    firstRun = { _, _ -> Text("first run screen") },
                    tabs = { session, badge -> AppTabs(session, badge) },
                    onSecureChanged = { secure += it },
                )
            }
        }
    }

    @Test
    fun loadingShowsWhileTheGraphOpensInTheBackgroundThenTheTabs() {
        val release = CountDownLatch(1)
        val slow =
            object : AppGraphProvider {
                override val mode = GraphMode.Demo

                override fun makeGraph(): AndroidGraphOpen {
                    release.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)
                    return demoOpen()
                }
            }
        show(slow, openOn = Dispatchers.Default)

        rule.onNodeWithTag("graph-opening").assertIsDisplayed()
        rule.hasTag("tab-Inbox") shouldBe false
        release.countDown()
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("tab-Inbox") }
    }

    @Test
    fun anUnavailableLiveGraphShowsTheNoticeAndTryAgainOpensTheGraph() {
        val provider = ScriptedProvider(GraphMode.Live, listOf(::unavailable, ::demoOpen))
        show(provider)

        rule.onNodeWithTag("startup-notice").assertIsDisplayed()
        rule
            .onNodeWithText("Paperclip could not open its data on this phone right now.", substring = true)
            .assertIsDisplayed()
        provider.calls shouldBe 1

        rule.onNodeWithText("Try again").performClick()

        provider.calls shouldBe 2
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("tab-Inbox") }
    }

    @Test
    fun theDemoGraphUnlocksAndShowsTheTabsWithTheServersBadge() {
        show(ScriptedProvider(GraphMode.Demo, listOf(::demoOpen)))

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("tab-Inbox") }
        rule.waitUntil(WAIT_MILLIS) { rule.inboxBadgeIs("3 waiting") }
        rule.onNodeWithTag("tab-Inbox").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 waiting"),
        )
    }

    @Test
    fun theLockEngagesAgainAfterTheIdlePeriod() {
        show(ScriptedProvider(GraphMode.Demo, listOf(::demoOpen)))
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("tab-Inbox") }
        val graph = checkNotNull(environment?.session?.value?.graph)
        graph.createSettingsStateHolder().onIntent(SettingsIntent.IdlePeriodChosen(IdlePeriod.Immediately))
        autoUnlock = false

        // The new period reaches the app holder asynchronously, so each trip to the background and back is repeated
        // until the lock engages.
        rule.waitUntil(WAIT_MILLIS) {
            environment?.setForeground(false)
            environment?.setForeground(true)
            rule.hasText("lock screen")
        }
    }

    @Test
    fun aCancelledUnlockStaysLockedAndSaysWhy() {
        show(ScriptedProvider(GraphMode.Demo, listOf(::demoOpen)), unlock = false)
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("lock screen") }
        val controls =
            checkNotNull(
                environment
                    ?.session
                    ?.value
                    ?.graph
                    ?.demoControls,
            )
        controls.scriptBiometrics(BiometricResult.Cancelled)

        environment
            ?.session
            ?.value
            ?.app
            ?.send(AppIntent.UnlockRequested)

        rule.waitUntil(WAIT_MILLIS) { rule.hasText("issue Cancelled") }
        rule.hasTag("tab-Inbox") shouldBe false
    }

    @Test
    fun aGraphWithNoFactoryShowsFirstRun() {
        show(ScriptedProvider(GraphMode.Demo, listOf(::firstRunOpen)))

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("root") }
        rule.waitUntil(WAIT_MILLIS) {
            rule.hasText("first run screen")
        }
    }

    @Test
    fun theLockIsSecureAndTheTabsAreNot() {
        val secure = mutableListOf<Boolean>()
        show(ScriptedProvider(GraphMode.Demo, listOf(::demoOpen)), secure)

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("tab-Inbox") }

        (true in secure) shouldBe true
        secure.last() shouldBe false
    }
}

private fun ComposeTestRule.hasTag(tag: String) = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

private fun ComposeTestRule.hasText(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

private fun ComposeTestRule.inboxBadgeIs(spoken: String) =
    onAllNodesWithTag("tab-Inbox").fetchSemanticsNodes().firstOrNull()?.config?.let {
        SemanticsProperties.StateDescription in it && it[SemanticsProperties.StateDescription] == spoken
    } == true
