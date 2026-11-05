package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.presentation.ActionsDisabledReason
import com.stainedhead.paperclipmobile.presentation.DecideByKindUi
import com.stainedhead.paperclipmobile.presentation.DecideByUi
import com.stainedhead.paperclipmobile.presentation.DecisionFinalKind
import com.stainedhead.paperclipmobile.presentation.DecisionFinalUi
import com.stainedhead.paperclipmobile.presentation.DecisionIntent
import com.stainedhead.paperclipmobile.presentation.DecisionState
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.EffectKindUi
import com.stainedhead.paperclipmobile.presentation.EffectResultUi
import com.stainedhead.paperclipmobile.presentation.EffectStatusUi
import com.stainedhead.paperclipmobile.presentation.EffectSummaryUi
import com.stainedhead.paperclipmobile.presentation.ExecutionStatusUi
import com.stainedhead.paperclipmobile.presentation.ExecutionUi
import com.stainedhead.paperclipmobile.presentation.OptionUi
import com.stainedhead.paperclipmobile.presentation.RoleUi
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val NOW_MILLIS = 1_800_000_000_000L

private val ship =
    OptionUi(
        "ship",
        "Ship tonight",
        null,
        isDestructive = false,
        needsDeviceAuth = false,
        effects = listOf(EffectSummaryUi(EffectKindUi.AssignIssue, "i", "DevOps", isStrict = true)),
    )

private fun open(
    role: RoleUi,
    disabled: ActionsDisabledReason? = null,
) = DecisionState(
    phase = DetailPhase.Content,
    title = "Ship it?",
    body = "Pick one.",
    originAgentName = "CTO",
    decideBy = DecideByUi(DecideByKindUi.OnDate, "2026-11-20"),
    options = listOf(ship),
    selectedOptionId = "ship",
    canConfirm = disabled == null,
    canDismiss = disabled == null,
    actionsDisabledReason = disabled,
    role = role,
    updatedAtEpochMillis = NOW_MILLIS,
)

/** AND-040 variants the demo graph cannot produce: a viewer, the effect results of a partial run, final kinds. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h2000dp-xhdpi")
class DecisionVariantsTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()
    private val intents = mutableListOf<DecisionIntent>()

    @After
    fun tearDown() = app.close()

    private fun show(state: DecisionState) {
        rule.setContent { app.Themed { DecisionContent(state, { intents += it }, "PAP-1", onBack = {}) } }
        rule.waitForIdle()
    }

    @Test
    fun aViewerCanReadTheOptionsButNotConfirmOrDismissAndSeesWhy() {
        show(open(RoleUi.Viewer, ActionsDisabledReason.ViewerRole))

        rule.onNodeWithTag("decision-option-ship").assertIsDisplayed()
        rule.onNodeWithTag("decision-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("decision-dismiss").assertIsNotEnabled()
        rule.onNodeWithText("Your role is Viewer, so you can look but not change anything.").assertIsDisplayed()
        rule.hasTag("decision-snooze") shouldBe false
    }

    @Test
    fun aStrictEffectSaysItIsSkippedIfTheIssueChangedAndADateDeadlineIsAWrittenDate() {
        show(open(RoleUi.Owner))

        rule.onNodeWithText("Skipped if the issue changed since it was proposed").assertIsDisplayed()
        rule.onNodeWithTag("decision-kind-line").assertIsDisplayed()
        rule.hasText("DECIDE BY NOV 20, 2026", substring = true) shouldBe true
    }

    @Test
    fun aPartialRunShowsEachEffectWithItsOwnStatusAndWhyOneWasSkipped() {
        val results =
            listOf(
                EffectResultUi(0, "update_issue_status", EffectStatusUi.Executed, null),
                EffectResultUi(1, "comment_on_issue", EffectStatusUi.Skipped, "target_changed"),
                EffectResultUi(2, "assign_issue", EffectStatusUi.Failed, "timeout_error"),
            )
        show(
            open(RoleUi.Owner).copy(
                finalState = DecisionFinalUi(DecisionFinalKind.Decided, "Ship tonight"),
                execution = ExecutionUi(ExecutionStatusUi.Partial, results, isPolling = false),
            ),
        )

        rule.onNodeWithText("Some effects did not run").assertIsDisplayed()
        rule.onNodeWithText("Update issue status").assertIsDisplayed()
        rule.onNodeWithText("The issue changed after this was proposed.").assertIsDisplayed()
        rule.onNodeWithText("timeout error").assertIsDisplayed()
        rule.onNodeWithText("Already decided: Ship tonight").assertIsDisplayed()
    }

    @Test
    fun everyFinalKindHasItsOwnSentence() {
        val sentences =
            mapOf(
                DecisionFinalKind.Dismissed to "This decision was dismissed.",
                DecisionFinalKind.Expired to "This decision expired.",
                DecisionFinalKind.Cancelled to "This decision was cancelled.",
                DecisionFinalKind.Unknown to "This decision is no longer open.",
            )
        val shown = mutableStateOf(open(RoleUi.Owner))
        rule.setContent { app.Themed { DecisionContent(shown.value, { intents += it }, "PAP-1", onBack = {}) } }
        for ((kind, sentence) in sentences) {
            shown.value = open(RoleUi.Owner).copy(finalState = DecisionFinalUi(kind, null))
            rule.waitForIdle()
            rule.onNodeWithText(sentence).assertIsDisplayed()
        }
    }
}
