package com.stainedhead.paperclipmobile.android.flows

import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.stainedhead.paperclipmobile.android.MainActivity
import com.stainedhead.paperclipmobile.android.PaperclipApplication
import com.stainedhead.paperclipmobile.android.env.LaunchConfiguration
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.umbrella.DemoControls
import org.junit.After
import org.junit.Rule

private const val WAIT_MILLIS = 20_000L

/**
 * Support for the connected demo-flow tests (T8.8, acceptance criterion 2): the real `MainActivity` and the real shell
 * on the demo graph, driven through the same test tags the Robolectric tests use. The orchestrator gives every test a
 * fresh process (`clearPackageData`), because the app fixes its graph when the process starts.
 */
abstract class FlowTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun closeScenario() {
        scenario?.close()
    }

    /** Starts the app with the given boolean launch extras (`demo`, `demo_first_run`) and waits for [ready]. */
    protected fun launch(
        vararg flags: String,
        ready: String = "tab-Inbox",
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MainActivity::class.java)
        flags.forEach { intent.putExtra(it, true) }
        scenario = ActivityScenario.launch(intent)
        waitFor { exists(ready) }
    }

    protected fun launchDemo() = launch(LaunchConfiguration.EXTRA_DEMO, ready = "inbox-list")

    private val application get() = ApplicationProvider.getApplicationContext<PaperclipApplication>()

    /** The demo graph's controls: scripted device-owner answers and the count of prompts shown. */
    protected val controls: DemoControls
        get() =
            checkNotNull(
                application
                    .environment(null)
                    .session.value.graph
                    ?.demoControls,
            )

    /** What the app currently has selected (factory and company). */
    protected val appState get() =
        checkNotNull(
            application
                .environment(null)
                .session.value.app,
        ).state.value

    protected fun script(result: BiometricResult) = controls.scriptBiometrics(result)

    /** Waits for [condition]; on a timeout the semantics tree is printed, which says which screen was showing. */
    protected fun waitFor(condition: () -> Boolean) {
        try {
            rule.waitUntil(WAIT_MILLIS, condition)
        } catch (timeout: ComposeTimeoutException) {
            println(rule.onRoot().printToString())
            throw timeout
        }
    }

    protected fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    protected fun existsText(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    protected fun tap(tag: String) {
        waitFor { exists(tag) }
        // Controls outside a scrolling container (tabs, bars) have nothing to scroll; they are on screen already.
        runCatching { rule.onNodeWithTag(tag).performScrollTo() }
        rule.onNodeWithTag(tag).performClick()
    }

    protected fun selected(tag: String) =
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.config?.let {
            SemanticsProperties.Selected in it && it[SemanticsProperties.Selected]
        } == true

    protected fun textOf(tag: String): String =
        rule
            .onAllNodesWithTag(tag)
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.config
            ?.getOrNull(SemanticsProperties.Text)
            ?.joinToString("") { it.text }
            .orEmpty()

    protected fun openTab(name: String) = tap("tab-$name")

    /** Scrolls the Inbox to the card titled [title] and opens it. */
    protected fun openInboxCard(title: String) {
        scrollInboxTo(title)
        rule.onAllNodesWithText(title).onFirst().performClick()
    }

    protected fun scrollInboxTo(title: String) {
        waitFor { exists("inbox-list") }
        rule.onNodeWithTag("inbox-list").performScrollToNode(hasText(title))
    }

    /** Runs the TalkBack action [label] on the Inbox card titled [title]: the swipe actions' accessible twins. */
    protected fun cardAction(
        title: String,
        label: String,
    ) {
        scrollInboxTo(title)
        val node =
            rule
                .onAllNodes(
                    hasTestTag("attention-card-open") and hasText(title) and hasAnyAncestor(hasTestTag("inbox-list")),
                ).onFirst()
                .fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].first { it.label == label }
        rule.runOnUiThread { action.action() }
    }
}
