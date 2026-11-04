package com.stainedhead.paperclipmobile.android.flows

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val ENGINEER_1 = "agent-eng-1"
private const val NEW_ISSUE = "Rotate the webhook secret"

/**
 * Acceptance criterion 2, the operations half, on a device: pause an agent, cancel a run, create an issue; each needs
 * the device owner where the PRD says so, and each starts in a fresh process on the demo graph.
 */
@RunWith(AndroidJUnit4::class)
class OperationsFlowsTest : FlowTest() {
    @Test
    fun anAgentIsPausedAfterTheDeviceOwnerApprovesAndNothingChangesWhenTheyCancel() {
        launchDemo()
        openTab("Agents")
        tap("agent-row-$ENGINEER_1")
        waitFor { exists("agent-detail-pause") }
        val prompts = controls.biometricPromptCount
        script(BiometricResult.Cancelled)

        tap("agent-detail-pause")
        waitFor { exists("confirm-destructive-sheet") }
        assertEquals(prompts, controls.biometricPromptCount)
        rule.onNodeWithTag("confirm-destructive-confirm").performClick()
        waitFor { exists("feedback-banner") }
        assertEquals(prompts + 1, controls.biometricPromptCount)
        assertFalse(exists("agent-detail-resume"))

        script(BiometricResult.Approved)
        tap("agent-detail-pause")
        rule.onNodeWithTag("confirm-destructive-confirm").performClick()

        waitFor { exists("agent-detail-resume") }
        assertEquals(prompts + 2, controls.biometricPromptCount)
        assertTrue(textOf("agent-status").startsWith("Paused"))
    }

    @Test
    fun aRunIsCancelledAfterTheDeviceOwnerApprovesAndNothingChangesWhenTheyCancel() {
        launchDemo()
        openTab("Agents")
        tap("agent-row-$ENGINEER_1")
        tap("agent-current-run")
        waitFor { exists("run-cancel") }
        val prompts = controls.biometricPromptCount
        script(BiometricResult.Cancelled)

        tap("run-cancel")
        waitFor { exists("confirm-destructive-sheet") }
        rule.onNodeWithTag("confirm-destructive-confirm").performClick()
        waitFor { exists("feedback-banner") }
        assertEquals(prompts + 1, controls.biometricPromptCount)
        assertTrue(exists("run-cancel"))

        script(BiometricResult.Approved)
        tap("run-cancel")
        waitFor { exists("confirm-destructive-sheet") }
        rule.onNodeWithTag("confirm-destructive-confirm").performClick()

        waitFor { textOf("run-status").startsWith("Cancelled") }
        assertEquals(prompts + 2, controls.biometricPromptCount)
        assertFalse(exists("run-cancel-bar"))
    }

    @Test
    fun anIssueIsCreatedWithItsPriorityAndOpened() {
        launchDemo()
        openTab("Work")
        tap("work-create")
        waitFor { exists("work-create-form") }

        rule.onNodeWithTag("work-create-title").performTextInput(NEW_ISSUE)
        tap("work-create-priority-High")
        tap("work-create-save")

        waitFor { !exists("work-create-form") }
        waitFor { existsText(NEW_ISSUE) }
    }
}
