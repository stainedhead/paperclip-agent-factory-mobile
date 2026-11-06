package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.LocalReferenceMillis
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import com.stainedhead.paperclipmobile.android.env.AppEnvironment
import com.stainedhead.paperclipmobile.android.env.GraphMode
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.env.ScriptedProvider
import com.stainedhead.paperclipmobile.android.env.demoOpen
import com.stainedhead.paperclipmobile.android.env.firstRunOpen
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.android.shell.AppTab
import com.stainedhead.paperclipmobile.android.shell.TabNavigator
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.umbrella.AndroidGraphOpen
import com.stainedhead.paperclipmobile.umbrella.DemoControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.locks.LockSupport

const val WAIT_MILLIS = 10_000L

/** The ids of the demo data set (the same constants the shared demo graph is seeded with). */
object DemoId {
    const val HOME = "demo-home"
    const val CLOUD = "demo-cloud"
    const val TEST = "demo-test"
    const val ACME = "company-acme"
    const val SIDE_PROJECT = "company-side"
    const val DECISION_BILLING = "dec-1182"
    const val DECISION_KEYS = "dec-1183"
    const val APPROVAL_HIRE = "ap-hire-qa"
    const val APPROVAL_STRATEGY = "ap-q1-strategy"
    const val INCIDENT = "inc-eng-2"
    const val ISSUE_BILLING = "pap-1182"
    const val ISSUE_REFUNDS = "pap-1190"
    const val ISSUE_RETRIES = "pap-1204"
    const val AGENT_CEO = "agent-ceo"
    const val AGENT_CTO = "agent-cto"
    const val AGENT_ENGINEER_1 = "agent-eng-1"
    const val AGENT_ENGINEER_2 = "agent-eng-2"
    const val AGENT_QA = "agent-qa"
    const val AGENT_DESIGNER = "agent-design"
    const val RUN_LIVE = "run-1204-live"
    const val RUN_CTO_LIVE = "run-cto-live"
    const val RUN_FAILED = "run-1204-failed"
    const val RUN_DONE = "run-1204-done"
    const val RUN_QA_TIMEOUT = "run-qa-1190"
}

/** One demo graph and its session for a screen test; [close] releases everything the screen test owned. */
class DemoApp(
    open: () -> AndroidGraphOpen = ::demoOpen,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val environment = AppEnvironment(ScriptedProvider(GraphMode.Demo, listOf(open)), scope, demoEntryAvailable = true)
    val session: GraphSession get() = environment.session.value
    val controls: DemoControls get() = checkNotNull(session.graph?.demoControls)
    val appState get() = checkNotNull(session.app).state.value

    /** The navigation a screen is given: everything pushed lands in [pushed], pops are counted. */
    val pushed = mutableListOf<AppRoute>()
    var pops = 0
    var popsToRoot = 0

    /** The tabs a screen asked the shell to show. */
    val selected = mutableListOf<AppTab>()
    val navigator =
        TabNavigator(
            push = { pushed += it },
            pop = { pops++ },
            popToRoot = { popsToRoot++ },
            select = { selected += it },
        )

    fun scriptBiometrics(result: BiometricResult) = controls.scriptBiometrics(result)

    @Composable
    fun Themed(
        darkTheme: Boolean = false,
        content: @Composable () -> Unit,
    ) = ThemedAt(darkTheme, null, content)

    /** [Themed] at a font scale (2.0 is the largest the system offers); null keeps the host's own. */
    @Composable
    fun ThemedAt(
        darkTheme: Boolean,
        fontScale: Float?,
        content: @Composable () -> Unit,
    ) {
        val now = checkNotNull(session.graph).nowEpochMillis
        val density = LocalDensity.current
        PaperclipTheme(darkTheme) {
            // The root screen paints the page; a screen shown on its own gets the same background here.
            Box(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
                CompositionLocalProvider(
                    LocalReferenceMillis provides now,
                    LocalDensity provides Density(density.density, fontScale ?: density.fontScale),
                ) { content() }
            }
        }
    }

    override fun close() {
        environment.shutdown()
        scope.cancel()
    }
}

fun firstRunApp() = DemoApp(::firstRunOpen)

/**
 * Holders answer on other threads and Compose catches up afterwards, so what a tap or a typed character causes is not
 * on the screen when the call returns. Every wait below polls its condition for up to [timeoutMillis] and then fails,
 * so a screen that never gets there is still a red test; none of them lowers what the test goes on to assert.
 */
private const val POLL_NANOS = 5_000_000L

private fun ComposeTestRule.passes(check: () -> Unit) = runCatching(check).isSuccess

/** Waits for [condition] outside Compose (a navigator callback, a holder value); fails naming [what] on timeout. */
fun awaitThat(
    what: String,
    timeoutMillis: Long = WAIT_MILLIS,
    condition: () -> Boolean,
) {
    val deadline = System.nanoTime() + timeoutMillis * NANOS_PER_MILLI
    while (!condition()) {
        if (System.nanoTime() > deadline) throw AssertionError("Timed out after $timeoutMillis ms waiting for $what")
        LockSupport.parkNanos(POLL_NANOS)
    }
}

private const val NANOS_PER_MILLI = 1_000_000L

/** Sheets slide in while the holders answer: wait until the node is displayed before asserting on what it shows. */
fun ComposeTestRule.awaitDisplayed(
    tag: String,
    timeoutMillis: Long = WAIT_MILLIS,
) = waitUntil(timeoutMillis) { passes { onNodeWithTag(tag).assertIsDisplayed() } }

/** Waits for the text [text] to be displayed anywhere (a dialog title, a banner). */
fun ComposeTestRule.awaitTextDisplayed(
    text: String,
    timeoutMillis: Long = WAIT_MILLIS,
) = waitUntil(timeoutMillis) { passes { onNodeWithText(text).assertIsDisplayed() } }

/** Waits for the node with [tag] to say exactly [text]. */
fun ComposeTestRule.awaitText(
    tag: String,
    text: String,
    timeoutMillis: Long = WAIT_MILLIS,
    useUnmergedTree: Boolean = false,
) = waitUntil(timeoutMillis) {
    passes { onNodeWithTag(tag, useUnmergedTree).assertTextEquals(text) }
}

/** Waits until no node carries [tag]. */
fun ComposeTestRule.awaitGone(
    tag: String,
    timeoutMillis: Long = WAIT_MILLIS,
) = waitUntil(timeoutMillis) { onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }

/** Waits until exactly [count] nodes carry [tag]. */
fun ComposeTestRule.awaitCount(
    tag: String,
    count: Int,
    timeoutMillis: Long = WAIT_MILLIS,
) = waitUntil(timeoutMillis) { onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size == count }

/** Waits until the node with [tag] is on the screen and enabled. */
fun ComposeTestRule.awaitEnabled(
    tag: String,
    timeoutMillis: Long = WAIT_MILLIS,
) = waitUntil(timeoutMillis) { passes { onNodeWithTag(tag).assertIsEnabled() } }

/** Waits until the node with [tag] is on the screen and disabled. */
fun ComposeTestRule.awaitDisabled(
    tag: String,
    timeoutMillis: Long = WAIT_MILLIS,
) = waitUntil(timeoutMillis) { passes { onNodeWithTag(tag).assertIsNotEnabled() } }

/** Waits until an editable node holds [text]: typed text reaches the holder only after the field shows it. */
fun ComposeTestRule.awaitFieldText(
    tag: String,
    text: String,
    timeoutMillis: Long = WAIT_MILLIS,
) = waitUntil(timeoutMillis) {
    onAllNodesWithTag(
        tag,
    ).fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.EditableText)?.text ==
        text
}

// Unmerged: a field's help and issue are part of the field's merged node, and they are still on the screen.
fun ComposeTestRule.hasTag(tag: String) =
    onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

fun ComposeTestRule.hasText(
    text: String,
    substring: Boolean = false,
) = onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

/** True when the node with [tag] says it is selected (a tab, a chip, a radio). */
fun ComposeTestRule.isSelected(tag: String) =
    onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.config?.let {
        SemanticsProperties.Selected in it && it[SemanticsProperties.Selected]
    } == true
