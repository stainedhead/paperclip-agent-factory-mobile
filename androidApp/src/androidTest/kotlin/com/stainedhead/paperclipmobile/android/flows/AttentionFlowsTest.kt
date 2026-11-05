package com.stainedhead.paperclipmobile.android.flows

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val BILLING = "Ship the billing migration to production?"
private const val HIRE = "Hire a QA engineer agent"
private const val BUDGET = "Engineer 2 hit its monthly budget"
private const val QUESTIONS = "Engineer 1 asked 2 questions"
private const val KEYS = "Rotate the signing keys this quarter?"
private const val QUESTIONS_CARD = "interaction-int-1204-questions"

/**
 * Acceptance criterion 2, the attention half, on a device: decide, approve and reject, the budget incident, the agent's
 * two questions, and snooze and dismiss with Undo, each in a fresh process on the demo graph through the real shell.
 */
@RunWith(AndroidJUnit4::class)
class AttentionFlowsTest : FlowTest() {
    @Test
    fun aNonDestructiveDecisionGoesThroughWithoutAskingTheDeviceOwner() {
        launchDemo()
        openInboxCard(BILLING)
        waitFor { exists("decision-option-ship") }
        val prompts = controls.biometricPromptCount

        tap("decision-option-ship")
        waitFor { selected("decision-option-ship") }
        tap("decision-confirm")

        waitFor { exists("decision-execution") }
        waitFor { existsText("All effects ran") }
        assertEquals(prompts, controls.biometricPromptCount)
    }

    @Test
    fun aDestructiveDecisionWaitsForTheDeviceOwnerAndRunsOnlyAfterTheyApprove() {
        launchDemo()
        openInboxCard(BILLING)
        waitFor { exists("decision-option-cancel") }
        val prompts = controls.biometricPromptCount
        script(BiometricResult.Cancelled)

        tap("decision-option-cancel")
        waitFor { selected("decision-option-cancel") }
        tap("decision-confirm")
        waitFor { exists("feedback-banner") }
        assertEquals(prompts + 1, controls.biometricPromptCount)
        assertFalse(exists("decision-execution"))

        script(BiometricResult.Approved)
        tap("decision-confirm")

        waitFor { exists("decision-execution") }
        assertEquals(prompts + 2, controls.biometricPromptCount)
    }

    @Test
    fun aHireIsApprovedWithoutTheDeviceOwner() {
        launchDemo()
        openInboxCard(HIRE)
        waitFor { exists("approval-approve") }
        val prompts = controls.biometricPromptCount

        tap("approval-approve")

        waitFor { exists("approval-final") }
        assertTrue(existsText("This approval was approved. Nothing more to do."))
        assertEquals(prompts, controls.biometricPromptCount)
    }

    @Test
    fun aHireIsRejectedWithANoteAndOnlyAfterTheDeviceOwnerApproves() {
        launchDemo()
        openInboxCard(HIRE)
        waitFor { exists("approval-reject") }
        val prompts = controls.biometricPromptCount
        script(BiometricResult.Cancelled)

        tap("approval-reject")
        waitFor { exists("note-sheet") }
        rule.onNodeWithTag("note-field").performTextInput("Too expensive")
        tap("note-confirm")
        waitFor { exists("note-issue") }
        assertEquals(prompts + 1, controls.biometricPromptCount)
        assertFalse(exists("approval-final"))

        script(BiometricResult.Approved)
        tap("note-confirm")

        waitFor { exists("approval-final") }
        assertTrue(existsText("This approval was rejected. Nothing more to do."))
        assertEquals(prompts + 2, controls.biometricPromptCount)
    }

    @Test
    fun theBudgetIncidentIsKeptPausedFromItsSheet() {
        launchDemo()
        openInboxCard(BUDGET)
        waitFor { exists("budget-usage") }

        tap("budget-choice-keep")
        waitFor { selected("budget-choice-keep") }
        tap("budget-submit")

        waitFor { exists("budget-outcome") }
        assertTrue(existsText("Kept paused. The agent stays paused."))
    }

    @Test
    fun theAgentsTwoQuestionsAreAnsweredAndTheCardReadsAnswered() {
        launchDemo()
        openInboxCard(QUESTIONS)
        waitFor { exists("$QUESTIONS_CARD-kind") }

        tap("$QUESTIONS_CARD-choice-q-policy-exponential")
        tap("$QUESTIONS_CARD-choice-q-attempts-3")
        waitFor { !exists("$QUESTIONS_CARD-submit-issue") }
        tap("$QUESTIONS_CARD-primary")

        waitFor { textOf("$QUESTIONS_CARD-kind").endsWith("ANSWERED") }
        assertFalse(exists("$QUESTIONS_CARD-primary"))
    }

    @Test
    fun aCardIsDismissedAndUndoBringsItBack() {
        launchDemo()

        cardAction(KEYS, "Dismiss")
        waitFor { exists("inbox-undo") }
        assertTrue(existsText("Removed from your Inbox"))
        assertFalse(existsText(KEYS))
        tap("inbox-undo-button")

        waitFor { !exists("inbox-undo") }
        scrollInboxTo(KEYS)
        assertTrue(existsText(KEYS))
    }

    @Test
    fun aCardIsSnoozedAfterChoosingAPresetAndUndoBringsItBack() {
        launchDemo()

        cardAction(KEYS, "Snooze")
        waitFor { exists("snooze-sheet") }
        tap("snooze-OneHour")
        waitFor { exists("inbox-undo") }
        waitFor { !exists("snooze-sheet") }
        assertFalse(existsText(KEYS))
        tap("inbox-undo-button")

        waitFor { !exists("inbox-undo") }
        scrollInboxTo(KEYS)
        assertTrue(existsText(KEYS))
    }
}
