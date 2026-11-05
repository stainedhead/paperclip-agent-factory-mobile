package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.presentation.AgentGroupUi
import com.stainedhead.paperclipmobile.presentation.AgentRowUi
import com.stainedhead.paperclipmobile.presentation.AgentSectionUi
import com.stainedhead.paperclipmobile.presentation.AgentStatusUi
import com.stainedhead.paperclipmobile.presentation.AgentsState
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.IssuePriorityUi
import com.stainedhead.paperclipmobile.presentation.IssueRowUi
import com.stainedhead.paperclipmobile.presentation.IssueStatusUi
import com.stainedhead.paperclipmobile.presentation.WorkListState
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val NOW = 1_800_000_000_000L
private const val ROWS = 500

private fun agent(index: Int) =
    AgentRowUi(
        "agent-$index",
        "Agent $index",
        "engineer",
        AgentStatusUi.Idle,
        null,
        false,
        false,
        0,
        NOW,
        emptyList(),
        null,
    )

private fun issue(index: Int) =
    IssueRowUi(
        "pap-$index",
        "PAP-$index",
        "Title $index",
        IssueStatusUi.InProgress,
        IssuePriorityUi.Medium,
        "Engineer 2",
        false,
        NOW,
    )

/** R-032: the Agents and Work lists compose the rows on the screen, not all of them. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class LazyListsTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    @Test
    fun agentRowsOffTheScreenAreNotComposedAndScrollingFindsTheLastOne() {
        val state =
            AgentsState(
                phase = DetailPhase.Content,
                sections = listOf(AgentSectionUi(AgentGroupUi.Running, (0 until ROWS).map(::agent))),
                updatedAtEpochMillis = NOW,
            )
        rule.setContent { app.Themed { AgentsContent(state, {}, {}, {}, {}) } }
        rule.waitForIdle()

        rule.hasTag("agent-card-agent-0") shouldBe true
        rule.hasTag("agent-card-agent-${ROWS - 1}") shouldBe false

        rule.onNodeWithTag("agents-list").performScrollToNode(hasTestTag("agent-card-agent-${ROWS - 1}"))

        rule.hasTag("agent-card-agent-${ROWS - 1}") shouldBe true
    }

    @Test
    fun workRowsOffTheScreenAreNotComposedAndScrollingFindsTheLastOne() {
        val state =
            WorkListState(
                phase = DetailPhase.Content,
                rows = (0 until ROWS).map(::issue),
                updatedAtEpochMillis = NOW,
            )
        rule.setContent { app.Themed { WorkListContent(state, {}, {}, {}) } }
        rule.waitForIdle()

        rule.hasTag("work-row-pap-0") shouldBe true
        rule.hasTag("work-row-pap-${ROWS - 1}") shouldBe false

        rule.onNodeWithTag("work-screen").performScrollToNode(hasTestTag("work-row-pap-${ROWS - 1}"))

        rule.hasTag("work-row-pap-${ROWS - 1}") shouldBe true
    }
}
