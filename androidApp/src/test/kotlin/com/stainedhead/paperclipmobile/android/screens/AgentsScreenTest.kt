package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The texts a node shows, merged: what a person reads in that row. */
fun ComposeTestRule.texts(tag: String): List<String> =
    onNodeWithTag(tag)
        .fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.Text)
        .orEmpty()
        .map { it.text }

/** AND-051: the Agents tab on the demo graph; six agents in four groups, the buttons by state, pause and run now. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class AgentsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun show() {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent { app.Themed { AgentsScreen(app.session, app.navigator) } }
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("agent-row-${DemoId.AGENT_CTO}") }
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun waitForText(
        tag: String,
        first: String,
    ) = rule.waitUntil(WAIT_MILLIS) { rule.hasTag(tag) && rule.texts(tag).getOrNull(1)?.startsWith(first) == true }

    @Test
    fun sixAgentsAreGroupedAsNeedsAttentionRunningAndIdleWithTheirCount() {
        show()

        rule.onNodeWithTag("agents-count").assertTextEquals("6 agents")
        for (group in listOf("NeedsAttention", "Running", "Idle")) rule.hasTag("agents-section-$group") shouldBe true
        rule.hasTag("agents-section-Paused") shouldBe false
        rule.onNodeWithTag("agents-section-NeedsAttention").assertTextEquals("NEEDS ATTENTION")
        rule.onNodeWithTag("updated-label").assertTextEquals("Updated 0s ago")
        rule.onNodeWithTag("agents-filter-running").assertTextEquals("Running 3")
        rule.onNodeWithTag("agents-filter-attention").assertTextEquals("Attention 2")
    }

    @Test
    fun eachRowSaysWhatTheAgentIsDoing() {
        show()

        rule.texts("agent-row-${DemoId.AGENT_ENGINEER_2}") shouldBe listOf("Engineer 2", "Paused by budget hard stop")
        rule.texts("agent-row-${DemoId.AGENT_QA}") shouldBe listOf("QA agent", "Error · last heartbeat 2m ago")
        rule.texts("agent-row-${DemoId.AGENT_CTO}") shouldBe listOf("CTO", "Running · PAP-1182 · 1m")
        rule.texts("agent-row-${DemoId.AGENT_ENGINEER_1}") shouldBe listOf("Engineer 1", "Running · PAP-1204 · 4m")
        rule.texts("agent-row-${DemoId.AGENT_CEO}") shouldBe listOf("CEO", "Idle · last heartbeat 2m ago")
    }

    @Test
    fun theButtonsOfARowFollowItsStateAndTheOwnerRole() {
        show()

        // Engineer 2 is paused by its budget: only the budget, never Resume.
        rule.hasTag("agent-${DemoId.AGENT_ENGINEER_2}-budget") shouldBe true
        rule.hasTag("agent-${DemoId.AGENT_ENGINEER_2}-resume") shouldBe false
        // The QA agent is in error: Run now and Budget.
        rule.hasTag("agent-${DemoId.AGENT_QA}-run-now") shouldBe true
        rule.hasTag("agent-${DemoId.AGENT_QA}-pause") shouldBe false
        // The CTO is running: View run replaces Run now, and Pause stays.
        rule.hasTag("agent-${DemoId.AGENT_CTO}-view-run") shouldBe true
        rule.hasTag("agent-${DemoId.AGENT_CTO}-run-now") shouldBe false
        rule.hasTag("agent-${DemoId.AGENT_CTO}-pause") shouldBe true
        // The CEO is idle: Run now and Pause.
        rule.hasTag("agent-${DemoId.AGENT_CEO}-run-now") shouldBe true
        rule.hasTag("agent-${DemoId.AGENT_CEO}-pause") shouldBe true
    }

    @Test
    fun theFiltersShowOnlyTheRunningOrOnlyTheAgentsThatNeedAttention() {
        show()

        tap("agents-filter-running")
        rule.hasTag("agents-section-Running") shouldBe true
        rule.hasTag("agents-section-NeedsAttention") shouldBe false
        rule.hasTag("agent-row-${DemoId.AGENT_QA}") shouldBe false

        tap("agents-filter-attention")
        rule.hasTag("agents-section-NeedsAttention") shouldBe true
        rule.hasTag("agents-section-Running") shouldBe false

        tap("agents-filter-all")
        rule.hasTag("agents-section-Idle") shouldBe true
    }

    @Test
    fun aRowOpensTheAgentAndViewRunOpensItsRun() {
        show()

        tap("agent-row-${DemoId.AGENT_QA}")
        tap("agent-${DemoId.AGENT_CTO}-view-run")
        tap("agent-${DemoId.AGENT_ENGINEER_2}-budget")

        app.pushed shouldBe
            listOf(
                AppRoute.Agent(DemoId.AGENT_QA),
                AppRoute.Run(DemoId.RUN_CTO_LIVE),
                AppRoute.Agent(DemoId.AGENT_ENGINEER_2),
            )
    }

    @Test
    fun pauseAsksFirstThenWaitsForTheDeviceOwnerAndDoesNothingWhenTheyCancel() {
        show()
        app.scriptBiometrics(BiometricResult.Cancelled)

        tap("agent-${DemoId.AGENT_CTO}-pause")
        rule.awaitDisplayed("confirm-destructive-sheet")
        rule.onNodeWithTag("confirm-destructive-lock", useUnmergedTree = true).assertIsDisplayed()
        app.controls.biometricPromptCount shouldBe 0
        rule.awaitDisplayed("confirm-destructive-confirm")
        rule.onNodeWithTag("confirm-destructive-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("feedback-banner") }
        app.controls.biometricPromptCount shouldBe 1
        rule.texts("agent-row-${DemoId.AGENT_CTO}")[1] shouldBe "Running · PAP-1182 · 1m"
        rule.hasTag("agents-section-Paused") shouldBe false
    }

    @Test
    fun pauseGoesThroughWhenTheDeviceOwnerApprovesAndResumeNeedsNoPrompt() {
        show()

        tap("agent-${DemoId.AGENT_CTO}-pause")
        rule.awaitDisplayed("confirm-destructive-confirm")
        rule.onNodeWithTag("confirm-destructive-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("agents-section-Paused") }
        app.controls.biometricPromptCount shouldBe 1
        waitForText("agent-row-${DemoId.AGENT_CTO}", "Paused")
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("agent-${DemoId.AGENT_CTO}-resume") }

        tap("agent-${DemoId.AGENT_CTO}-resume")

        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag("agents-section-Paused") }
        app.controls.biometricPromptCount shouldBe 1
        waitForText("agent-row-${DemoId.AGENT_CTO}", "Idle")
    }

    @Test
    fun runNowStartsARunWithoutAskingTheDeviceOwner() {
        show()

        tap("agent-${DemoId.AGENT_QA}-run-now")

        waitForText("agent-row-${DemoId.AGENT_QA}", "Running")
        app.controls.biometricPromptCount shouldBe 0
        rule.hasTag("feedback-banner") shouldBe false
    }

    @Test
    fun offlineKeepsTheListSaysSoAndOffersNoButtons() {
        show()
        app.controls.setOnline(false)

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("offline-banner") }
        rule.hasTag("agent-row-${DemoId.AGENT_CTO}") shouldBe true
        rule.hasTag("agent-${DemoId.AGENT_CTO}-pause") shouldBe false
        rule.hasTag("agent-${DemoId.AGENT_QA}-run-now") shouldBe false
        rule.hasTag("agent-${DemoId.AGENT_ENGINEER_2}-budget") shouldBe false
        rule.onNodeWithTag("disabled-reason").assertIsDisplayed()
    }
}
