package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.presentation.ActionsDisabledReason
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.ErrorKey
import com.stainedhead.paperclipmobile.presentation.ErrorUi
import com.stainedhead.paperclipmobile.presentation.FeedbackKind
import com.stainedhead.paperclipmobile.presentation.FeedbackUi
import com.stainedhead.paperclipmobile.presentation.RoleUi
import com.stainedhead.paperclipmobile.presentation.RunRowUi
import com.stainedhead.paperclipmobile.presentation.RunStatusUi
import com.stainedhead.paperclipmobile.presentation.RunViewerIntent
import com.stainedhead.paperclipmobile.presentation.RunViewerState
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val NOW = 1_800_000_000_000L
private const val LOG_LINES = 120

private val LONG_LOG = (1..LOG_LINES).joinToString("\n") { "output line $it" }

private fun run(status: RunStatusUi) =
    RunRowUi("run-1", "Engineer 1", "pap-1204", status, NOW, NOW, null, "PAP-1204", "Add retries")

private fun state(
    status: RunStatusUi = RunStatusUi.Running,
    log: String = LONG_LOG,
    autoScroll: Boolean = true,
) = RunViewerState(
    phase = DetailPhase.Content,
    run = run(status),
    logText = log,
    autoScroll = autoScroll,
    isLive = true,
    canCancel = true,
    role = RoleUi.Owner,
    updatedAtEpochMillis = NOW,
    refreshIntervalSeconds = 5,
)

/** AND-052 variants the demo graph cannot produce: a long log, a viewer, an unknown outcome, a dropped log start. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class RunViewerVariantsTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun show(
        state: RunViewerState,
        send: (RunViewerIntent) -> Unit = {},
    ) = rule.setContent { app.Themed { RunViewerContent(state, send, {}, {}) } }

    private fun scroll(): Pair<Float, Float> {
        val range =
            rule
                .onNodeWithTag(
                    "run-log-panel",
                ).fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
        return range.value() to range.maxValue()
    }

    @Test
    fun aLongLogFollowsItsEndWhileFollowingIsOn() {
        show(state(autoScroll = true))

        rule.waitUntil(WAIT_MILLIS) { scroll().let { (value, max) -> max > 0f && value == max } }
    }

    @Test
    fun aLongLogStaysWhereItIsWhileFollowingIsOff() {
        show(state(autoScroll = false))
        rule.waitForIdle()

        val (value, max) = scroll()
        max shouldNotBe 0f
        value shouldBe 0f
        rule.onNodeWithTag("run-jump-to-latest").assertIsDisplayed()
    }

    @Test
    fun theUserScrollingUpIsReportedSoFollowingTurnsOff() {
        val sent = mutableListOf<RunViewerIntent>()
        show(state(autoScroll = true), sent::add)
        rule.waitUntil(WAIT_MILLIS) { scroll().let { (value, max) -> max > 0f && value == max } }

        rule.onNodeWithTag("run-log-panel").performTouchInput { swipeDown() }
        rule.waitUntil(WAIT_MILLIS) { sent.contains(RunViewerIntent.UserScrolled(atBottom = false)) }

        sent shouldContain RunViewerIntent.UserScrolled(atBottom = false)
    }

    @Test
    fun jumpToLatestIsForwardedAsAnIntent() {
        val sent = mutableListOf<RunViewerIntent>()
        show(state(autoScroll = false), sent::add)

        rule.onNodeWithTag("run-jump-to-latest").performClick()

        sent shouldContain RunViewerIntent.JumpToLatest
    }

    @Test
    fun aViewerSeesTheRunButCancelIsDisabledWithItsReason() {
        show(
            state().copy(
                role = RoleUi.Viewer,
                canCancel = false,
                actionsDisabledReason = ActionsDisabledReason.ViewerRole,
            ),
        )

        rule.onNodeWithTag("run-cancel").assertIsNotEnabled()
        rule.onNodeWithTag("disabled-reason").assertTextContains("Viewer", substring = true)
    }

    @Test
    fun anOutcomeThatTimedOutSaysToRefreshBeforeTryingAgain() {
        show(
            state().copy(feedback = FeedbackUi(FeedbackKind.Unknown, error = ErrorUi(ErrorKey.Timeout))),
        )

        rule.onNodeWithTag("feedback-message").assertTextContains("unclear whether that took effect", substring = true)
        rule.onNodeWithTag("feedback-message").assertTextContains("Refresh before trying again", substring = true)
    }

    @Test
    fun aDroppedLogStartAndAnUnreadableLogAreSaidInWords() {
        show(state(log = "tail").copy(logDroppedStart = true))

        rule.onNodeWithTag("run-log-dropped").assertIsDisplayed()
    }

    @Test
    fun anEmptyLogAndNoEventsSaySoAndAnUnreadableLogSaysThat() {
        show(state(log = "").copy(logUnavailable = true))

        rule.onNodeWithTag("run-no-events").assertIsDisplayed()
        rule.onNodeWithTag("run-log-empty").assertTextContains("could not be read", substring = true)
    }

    @Test
    fun aFailedFirstReadOffersTryAgain() {
        val failed = RunViewerState(phase = DetailPhase.Failed, error = ErrorUi(ErrorKey.Unreachable))
        show(failed)

        rule.onNodeWithTag("detail-failed").assertIsDisplayed()
        rule.onNodeWithTag("error-banner").assertIsDisplayed()
    }
}
