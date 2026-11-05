package com.stainedhead.paperclipmobile.android.shell

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import com.stainedhead.paperclipmobile.presentation.DeviceAuthIssue
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** AND-013: the lock asks the shared app holder to unlock, once per engagement and only while resumed. */
@RunWith(AndroidJUnit4::class)
class LockScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var unlocks = 0

    @Composable
    private fun Lock(issue: DeviceAuthIssue? = null) {
        PaperclipTheme(darkTheme = false) { LockScreen(LockActions(issue) { unlocks++ }) }
    }

    @Test
    fun itAsksOnceWhenItAppearsAndOffersTheButton() {
        rule.setContent { Lock() }
        rule.waitForIdle()

        unlocks shouldBe 1
        rule.onNodeWithTag("lock-screen").assertIsDisplayed()
        rule.onNodeWithText("Paperclip is locked").assertIsDisplayed()
        rule.onNodeWithTag("unlock-button").assertHeightIsAtLeast(48.dp).performClick()
        unlocks shouldBe 2
    }

    @Test
    fun itNeverAsksWhileTheActivityIsNotResumed() {
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.setContent { Lock() }
        rule.waitForIdle()

        unlocks shouldBe 0

        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()

        unlocks shouldBe 1
    }

    @Test
    fun aCancelledAttemptSaysSoAndStaysUntilTheButtonIsTapped() {
        rule.setContent { Lock(issue = DeviceAuthIssue.Cancelled) }
        rule.waitForIdle()

        rule.onNodeWithText("Authentication was cancelled. Nothing was changed.").assertIsDisplayed()
        unlocks shouldBe 1
    }
}
