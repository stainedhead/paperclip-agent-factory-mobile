package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val SETTLE_MILLIS = 500L

/** AND-051: Engineer 1 on the demo graph; the header, the work, the spend, the buttons and the owner-only budget. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class AgentDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun show(id: String = DemoId.AGENT_ENGINEER_1) {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent { app.Themed { AgentDetailScreen(app.session, AppRoute.Agent(id), app.navigator) } }
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("agent-name") }
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    @Test
    fun theHeaderNamesTheAgentItsRoleItsManagerAndWhatItIsDoing() {
        show()

        rule.onNodeWithTag("agent-name").assertTextEquals("Engineer 1")
        rule.onNodeWithTag("agent-role").assertTextEquals("Role: Engineer")
        rule.onNodeWithTag("agent-status").assertTextEquals("Running · last heartbeat 2m ago")
        rule.onNodeWithTag("agent-reports-to").assertTextEquals("Reports to: CTO")
        rule.onNodeWithTag("updated-label").assertTextEquals("Updated 0s ago")
    }

    @Test
    fun theCurrentIssueTheCurrentRunTheMonthSpendAndTheRecentRunsAreThere() {
        show()

        rule.onNodeWithTag("agent-current-issue").assertTextEquals("PAP-1204", "Add retries to webhook ingest")
        rule.onNodeWithTag("agent-current-run").assertTextEquals(
            "Engineer 1 · PAP-1204",
            "Add retries to webhook ingest",
            "4m",
            "Running",
        )
        rule.onNodeWithTag("agent-month-spend").assertTextEquals("$250")
        rule.onNodeWithTag("agent-budget").assertTextEquals("Monthly budget: $1,000 · 25% used")
        for (run in listOf(DemoId.RUN_LIVE, DemoId.RUN_FAILED, DemoId.RUN_DONE)) {
            rule.hasTag("agent-run-$run") shouldBe true
        }
        rule.onNodeWithTag("agent-run-${DemoId.RUN_FAILED}").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theIssueAndTheRunsOpenTheirScreens() {
        show()

        tap("agent-current-issue")
        tap("agent-current-run")
        tap("agent-run-${DemoId.RUN_DONE}")

        app.pushed shouldBe
            listOf(
                AppRoute.Issue(DemoId.ISSUE_RETRIES),
                AppRoute.Run(DemoId.RUN_LIVE),
                AppRoute.Run(DemoId.RUN_DONE),
            )
    }

    @Test
    fun anIdleAgentSaysItHasNoIssueAndRunNowStaysAvailable() {
        show(DemoId.AGENT_CEO)

        rule.hasTag("agent-idle-note") shouldBe true
        rule.hasTag("agent-current-run") shouldBe false
        rule.hasTag("agent-detail-run-now") shouldBe true
    }

    @Test
    fun pauseAsksFirstThenWaitsForTheDeviceOwnerAndChangesNothingWhenTheyCancel() {
        show()
        app.scriptBiometrics(BiometricResult.Cancelled)

        tap("agent-detail-pause")
        rule.awaitDisplayed("confirm-destructive-sheet")
        app.controls.biometricPromptCount shouldBe 0
        rule.onNodeWithTag("confirm-destructive-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("feedback-banner") }
        app.controls.biometricPromptCount shouldBe 1
        rule.onNodeWithTag("agent-status").assertTextEquals("Running · last heartbeat 2m ago")
        rule.hasTag("agent-detail-resume") shouldBe false

        tap("agent-detail-pause")
        rule.onNodeWithTag("confirm-destructive-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("agent-detail-resume") }
        app.controls.biometricPromptCount shouldBe 2
        rule.onNodeWithTag("agent-status").assertTextEquals("Paused · last heartbeat 2m ago")
        rule.hasTag("agent-detail-pause") shouldBe false
    }

    @Test
    fun resumeNeedsNoPromptAndRunNowStartsARunWithoutOne() {
        show()
        tap("agent-detail-pause")
        rule.onNodeWithTag("confirm-destructive-confirm").performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("agent-detail-resume") }

        tap("agent-detail-resume")

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("agent-detail-run-now") }
        app.controls.biometricPromptCount shouldBe 1
        tap("agent-detail-run-now")
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("agent-detail-run-now") }
        app.controls.biometricPromptCount shouldBe 1
        rule.hasTag("feedback-banner") shouldBe false
    }

    @Test
    fun anOwnerSetsTheMonthlyBudgetWithoutLeavingTheScreenAndABadAmountIsExplained() {
        show()

        tap("agent-set-budget")
        rule.awaitDisplayed("agent-budget-form")
        rule.onNodeWithTag("agent-budget-save").assertIsNotEnabled()
        rule.onNodeWithTag("agent-budget-amount").performTextInput("abc")
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("agent-budget-amount-issue") }
        rule.onNodeWithTag("agent-budget-save").assertIsNotEnabled()

        rule.onNodeWithTag("agent-budget-amount").performTextReplacement("1500")
        runBlocking { delay(SETTLE_MILLIS) }
        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag("agent-budget-amount-issue") }
        rule.onNodeWithTag("agent-budget-save").assertIsEnabled()
        rule.onNodeWithTag("agent-budget-save").performClick()

        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag("agent-budget-form") }
        rule.waitUntil(WAIT_MILLIS) { rule.texts("agent-budget").firstOrNull()?.contains("$1,500") == true }
        rule.onNodeWithTag("agent-budget").assertTextEquals("Monthly budget: $1,500 · 16% used")
        app.controls.biometricPromptCount shouldBe 0
    }

    @Test
    fun offlineKeepsTheAgentSaysSoAndOffersNoAction() {
        show()
        app.controls.setOnline(false)

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("offline-banner") }
        rule.hasTag("agent-detail-pause") shouldBe false
        rule.hasTag("agent-set-budget") shouldBe false
        rule.onNodeWithTag("agent-name").assertIsDisplayed()
    }
}
