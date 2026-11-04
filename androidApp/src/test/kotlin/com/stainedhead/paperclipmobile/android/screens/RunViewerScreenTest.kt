package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** AND-052: Engineer 1's heartbeat on the demo graph; events, log tail, auto-scroll, Cancel behind the device owner. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class RunViewerScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun show(id: String = DemoId.RUN_LIVE) {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent { app.Themed { RunViewerScreen(app.session, AppRoute.Run(id), app.navigator) } }
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("run-title") && rule.hasTag("run-log") }
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    @Test
    fun theHeaderNamesTheRunItsStatusHowLongItHasGoneAndTheIssue() {
        show()

        rule.onNodeWithTag("run-title").assertTextEquals("Engineer 1 heartbeat")
        rule.onNodeWithTag("run-status").assertTextEquals("Running · 4m 12s")
        rule.onNodeWithTag("run-issue").assertTextEquals("PAP-1204 · Add retries to webhook ingest")
        rule.onNodeWithTag("updated-label").assertTextEquals("Updated 0s ago")
    }

    @Test
    fun theIssueLineOpensTheIssue() {
        show()

        tap("run-issue")

        app.pushed shouldBe listOf(AppRoute.Issue(DemoId.ISSUE_RETRIES))
    }

    @Test
    fun theEventsAreListedWithWhereInTheRunTheyHappened() {
        show()

        rule.texts("run-event-1") shouldBe listOf("0:02", "Checked out PAP-1204")
        rule.texts("run-event-2") shouldBe listOf("0:40", "Opened a branch and edited 3 files")
        rule.texts("run-event-3") shouldBe listOf("2:55", "Ran tests: 2 failing")
        rule.texts("run-event-4") shouldBe listOf("3:58", "Asked you 2 questions")
    }

    @Test
    fun theLogTailShowsTheRunsOutputAndFollowsItsEnd() {
        show()

        val log = rule.texts("run-log").joinToString("\n")
        log shouldContain "Tests: 2 failed, 14 passed"
        log shouldContain "Waiting for board answer on retry policy…"
        rule.onNodeWithTag("run-autoscroll").assertTextEquals("auto-scroll on")
        rule.hasTag("run-jump-to-latest") shouldBe false
    }

    @Test
    fun turningFollowingOffOffersJumpToLatestWhichTurnsItBackOn() {
        show()

        tap("run-autoscroll")
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("run-jump-to-latest") }
        rule.onNodeWithTag("run-autoscroll").assertTextEquals("auto-scroll off")

        tap("run-jump-to-latest")

        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag("run-jump-to-latest") }
        rule.onNodeWithTag("run-autoscroll").assertTextEquals("auto-scroll on")
    }

    @Test
    fun cancelAsksFirstThenWaitsForTheDeviceOwnerAndStopsNothingWhenTheyCancel() {
        show()
        app.scriptBiometrics(BiometricResult.Cancelled)

        rule.onNodeWithTag("run-cancel").performClick()
        rule.awaitDisplayed("confirm-destructive-sheet")
        rule.onNodeWithTag("confirm-destructive-lock", useUnmergedTree = true).assertIsDisplayed()
        app.controls.biometricPromptCount shouldBe 0
        rule.onNodeWithTag("confirm-destructive-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("feedback-banner") }
        app.controls.biometricPromptCount shouldBe 1
        rule.onNodeWithTag("run-status").assertTextEquals("Running · 4m 12s")
        rule.hasTag("run-cancel") shouldBe true
    }

    @Test
    fun cancelMovesTheRunToCancelledWhenTheDeviceOwnerApprovesAndTheButtonGoes() {
        show()

        rule.onNodeWithTag("run-cancel").performClick()
        rule.onNodeWithTag("confirm-destructive-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.texts("run-status").firstOrNull()?.startsWith("Cancelled") == true }
        app.controls.biometricPromptCount shouldBe 1
        rule.hasTag("run-cancel-bar") shouldBe false
    }

    @Test
    fun aFinishedRunHasNoCancelAndSaysHowLongItTook() {
        show(DemoId.RUN_DONE)

        rule.onNodeWithTag("run-status").assertTextEquals("Succeeded · 3m 00s")
        rule.hasTag("run-cancel-bar") shouldBe false
        rule.hasTag("run-cancel") shouldBe false
    }

    @Test
    fun offlineKeepsTheRunAndDisablesCancelWithItsReason() {
        show()
        app.controls.setOnline(false)

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("offline-banner") }
        rule.onNodeWithTag("run-title").assertIsDisplayed()
        rule.onNodeWithTag("disabled-reason").assertIsDisplayed()
        rule.onNodeWithTag("run-cancel").assertIsNotEnabled()
    }
}
