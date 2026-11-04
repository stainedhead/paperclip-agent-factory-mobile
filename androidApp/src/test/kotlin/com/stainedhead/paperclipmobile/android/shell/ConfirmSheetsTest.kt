package com.stainedhead.paperclipmobile.android.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import com.stainedhead.paperclipmobile.presentation.DeviceAuthIssue
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AND-013: a sheet only asks. The prompt is never shown from here: the confirm intent reaches the shared use
 * case, which asks the `Biometrics` port before it calls the server; a cancelled or failed prompt makes no call
 * and comes back as the issue this sheet shows.
 */
@RunWith(AndroidJUnit4::class)
class ConfirmSheetsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun aPlainConfirmationHasNoLockBecauseNoPromptFollows() {
        var confirms = 0
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                ConfirmContent(
                    "Sign out?",
                    "You will need to pair again.",
                    "Sign out",
                    onConfirm = { confirms++ },
                    onCancel = {},
                )
            }
        }

        rule.onNodeWithText("Sign out?").assertIsDisplayed()
        rule.onNodeWithTag("confirm-sheet-lock").assertDoesNotExist()
        rule.onNodeWithTag("confirm-sheet-confirm").performClick()

        confirms shouldBe 1
    }

    @Test
    fun aDestructiveConfirmationShowsTheLockAndWhatFollows() {
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                ConfirmDestructiveContent("Pause Engineer 1?", "It stops picking up work.", "Pause agent", {}, {})
            }
        }

        rule.onNodeWithTag("confirm-destructive-lock").assertIsDisplayed()
        rule.onNodeWithText("Confirm with your fingerprint, face or screen lock").assertIsDisplayed()
        rule.onNodeWithTag("confirm-destructive-confirm").assertIsEnabled()
    }

    @Test
    fun aCancelledPromptIsShownAndTheSameButtonTriesAgain() {
        var confirms = 0
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                ConfirmDestructiveContent(
                    "Pause Engineer 1?",
                    "It stops picking up work.",
                    "Pause agent",
                    onConfirm = { confirms++ },
                    onCancel = {},
                    issue = DeviceAuthIssue.Cancelled,
                )
            }
        }

        rule.onNodeWithText("Authentication was cancelled. Nothing was changed.").assertIsDisplayed()
        rule.onNodeWithTag("confirm-destructive-confirm").performClick()

        confirms shouldBe 1
    }

    @Test
    fun whileTheOutcomeIsPendingTheConfirmButtonIsOff() {
        var confirms = 0
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                ConfirmDestructiveContent("T", "M", "Go", onConfirm = { confirms++ }, onCancel = {}, isWorking = true)
            }
        }

        rule.onNodeWithTag("confirm-destructive-confirm").assertIsNotEnabled()
        rule.onNodeWithText("Waiting for you to confirm…").assertIsDisplayed()
        confirms shouldBe 0
    }

    @Test
    fun cancelLeavesWithoutConfirming() {
        var cancels = 0
        var confirms = 0
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                ConfirmDestructiveContent("T", "M", "Go", onConfirm = { confirms++ }, onCancel = { cancels++ })
            }
        }

        rule.onNodeWithTag("confirm-destructive-cancel").performClick()

        cancels shouldBe 1
        confirms shouldBe 0
    }
}
