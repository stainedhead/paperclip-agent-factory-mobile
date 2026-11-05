package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

private const val SMALL = "w360dp-h640dp-xhdpi"
private const val BIG = "w430dp-h932dp-xxhdpi"
private const val LARGEST_TEXT = 2.0f

private val BILLING =
    AppRoute.Decision(DemoId.DECISION_BILLING, "CTO", "today", "attention:decision:dec-1182", "PAP-1182")

/**
 * AND-070: the smallest supported screen (360 x 640 dp), the same at font scale 2.0 (the worst case: the least room and
 * the most text), and a large phone (430 x 932 dp). Baselines for the main screen of each family, plus the check that
 * the named primary actions of the attention family are displayed (not clipped off the screen) and at least 48 dp tall
 * in the worst case.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LayoutVariantsScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()
    private val hostZone = TimeZone.getDefault()

    @Before
    fun pinZone() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @After
    fun tearDown() {
        TimeZone.setDefault(hostZone)
        app.close()
    }

    private fun capture(
        name: String,
        fontScale: Float?,
        ready: () -> Boolean,
        content: @Composable () -> Unit,
    ) {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent { app.ThemedAt(false, fontScale, content) }
        rule.waitUntil(WAIT_MILLIS, ready)
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun inbox(
        suffix: String,
        fontScale: Float? = null,
    ) = capture("inbox-$suffix", fontScale, { rule.cardCount() > 0 }) { InboxScreen(app.session, app.navigator) }

    private fun decision(
        suffix: String,
        fontScale: Float? = null,
    ) = capture("decision-$suffix", fontScale, { rule.hasTag("decision-confirm") }) {
        DecisionScreen(app.session, BILLING, app.navigator)
    }

    private fun approval(
        suffix: String,
        fontScale: Float? = null,
    ) = capture("approval-$suffix", fontScale, { rule.hasTag("approval-approve") }) {
        ApprovalScreen(app.session, AppRoute.Approval(DemoId.APPROVAL_HIRE), app.navigator)
    }

    private fun agents(
        suffix: String,
        fontScale: Float? = null,
    ) = capture("agents-$suffix", fontScale, { rule.hasTag("agents-count") }) {
        AgentsScreen(app.session, app.navigator)
    }

    private fun work(
        suffix: String,
        fontScale: Float? = null,
    ) = capture("work-$suffix", fontScale, { rule.hasTag("work-count") }) {
        WorkListScreen(app.session, app.navigator)
    }

    private fun issue(
        suffix: String,
        fontScale: Float?,
    ) = capture("issue-$suffix", fontScale, { rule.hasTag("issue-comment-send") }) {
        IssueScreen(app.session, AppRoute.Issue(DemoId.ISSUE_RETRIES), app.navigator)
    }

    private fun agentDetail(
        suffix: String,
        fontScale: Float?,
    ) = capture("agent-detail-$suffix", fontScale, { rule.hasTag("agent-detail-pause") }) {
        AgentDetailScreen(app.session, AppRoute.Agent(DemoId.AGENT_ENGINEER_1), app.navigator)
    }

    private fun run(
        suffix: String,
        fontScale: Float?,
    ) = capture("run-viewer-$suffix", fontScale, { rule.hasTag("run-log") && rule.hasTag("run-cancel") }) {
        RunViewerScreen(app.session, AppRoute.Run(DemoId.RUN_LIVE), app.navigator)
    }

    private fun shows(vararg tags: String) =
        tags.forEach {
            rule.onNodeWithTag(it).assertIsDisplayed()
            rule.onNodeWithTag(it).assertHeightIsAtLeast(48.dp)
        }

    @Test
    @Config(qualifiers = SMALL)
    fun inboxSmall() = inbox("small")

    @Test
    @Config(qualifiers = SMALL)
    fun decisionSmall() = decision("small")

    @Test
    @Config(qualifiers = SMALL)
    fun approvalSmall() = approval("small")

    @Test
    @Config(qualifiers = SMALL)
    fun agentsSmall() = agents("small")

    @Test
    @Config(qualifiers = SMALL)
    fun workSmall() = work("small")

    @Test
    @Config(qualifiers = SMALL)
    fun inboxSmallLargestText() {
        inbox("small-large", LARGEST_TEXT)
        val cards =
            checkNotNull(app.session.inbox.value)
                .state.value.sections
                .flatMap { it.items }
        shows("inbox-review-${cards.first { it.title.orEmpty().startsWith("Ship the billing") }.id}")
    }

    @Test
    @Config(qualifiers = SMALL)
    fun decisionSmallLargestText() {
        decision("small-large", LARGEST_TEXT)
        shows("decision-dismiss", "decision-confirm")
    }

    @Test
    @Config(qualifiers = SMALL)
    fun approvalSmallLargestText() {
        approval("small-large", LARGEST_TEXT)
        shows("approval-reject", "approval-approve")
    }

    @Test
    @Config(qualifiers = SMALL)
    fun agentsSmallLargestText() = agents("small-large", LARGEST_TEXT)

    @Test
    @Config(qualifiers = SMALL)
    fun workSmallLargestText() = work("small-large", LARGEST_TEXT)

    @Test
    @Config(qualifiers = SMALL)
    fun issueSmallLargestText() = issue("small-large", LARGEST_TEXT)

    @Test
    @Config(qualifiers = SMALL)
    fun agentDetailSmallLargestText() {
        agentDetail("small-large", LARGEST_TEXT)
        shows("agent-detail-pause")
    }

    @Test
    @Config(qualifiers = SMALL)
    fun runViewerSmallLargestText() = run("small-large", LARGEST_TEXT)

    @Test
    @Config(qualifiers = BIG)
    fun inboxBig() = inbox("big")

    @Test
    @Config(qualifiers = BIG)
    fun decisionBig() = decision("big")

    @Test
    @Config(qualifiers = BIG)
    fun approvalBig() = approval("big")

    @Test
    @Config(qualifiers = BIG)
    fun agentsBig() = agents("big")

    @Test
    @Config(qualifiers = BIG)
    fun workBig() = work("big")
}
