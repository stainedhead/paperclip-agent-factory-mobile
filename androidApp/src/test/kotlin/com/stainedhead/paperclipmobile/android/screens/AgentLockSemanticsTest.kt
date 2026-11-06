package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import com.stainedhead.paperclipmobile.presentation.AgentActionUi
import com.stainedhead.paperclipmobile.presentation.AgentDetailState
import com.stainedhead.paperclipmobile.presentation.AgentRowUi
import com.stainedhead.paperclipmobile.presentation.AgentStatusUi
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun agent(actions: List<AgentActionUi>) =
    AgentRowUi("a1", "Engineer 1", "engineer", AgentStatusUi.Idle, null, false, true, 25, null, actions, null)

/** R-036: a button that asks the device owner says so to TalkBack, and a busy row cannot be tapped twice. */
@RunWith(AndroidJUnit4::class)
class AgentLockSemanticsTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = listOf(AgentActionUi.Pause, AgentActionUi.RunNow)

    private fun spoken(tag: String) =
        rule
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString(" ")
            .orEmpty()

    private fun showRow(isBusy: Boolean) =
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                AgentCard(agent(actions), isBusy, canSetBudget = false, AgentCardActions({}, {}, {}))
            }
        }

    @Test
    fun thePauseButtonOnARowSaysItAsksTheDeviceOwner() {
        showRow(isBusy = false)

        spoken("agent-a1-pause") shouldContain "Pause"
        spoken("agent-a1-pause") shouldContain "fingerprint"
        spoken("agent-a1-run-now") shouldBe ""
    }

    @Test
    fun theRowButtonsAreDisabledWhileAnActionRuns() {
        showRow(isBusy = true)

        rule.onNodeWithTag("agent-a1-pause").assertIsNotEnabled()
        rule.onNodeWithTag("agent-a1-run-now").assertIsNotEnabled()
    }

    @Test
    fun theRowButtonsAreEnabledWhenNothingRuns() {
        showRow(isBusy = false)

        rule.onNodeWithTag("agent-a1-pause").assertIsEnabled()
        rule.onNodeWithTag("agent-a1-run-now").assertIsEnabled()
    }

    @Test
    fun thePauseButtonOnTheDetailScreenSaysItAsksTheDeviceOwner() {
        val state = AgentDetailState(phase = DetailPhase.Content, agent = agent(actions), actions = actions)
        rule.setContent { PaperclipTheme(darkTheme = false) { AgentActions(state) {} } }

        spoken("agent-detail-pause") shouldContain "fingerprint"
        spoken("agent-detail-run-now") shouldBe ""
    }
}
