package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.presentation.InteractionKindUi
import com.stainedhead.paperclipmobile.presentation.InteractionPrimaryUi
import com.stainedhead.paperclipmobile.presentation.InteractionReadOnlyUi
import com.stainedhead.paperclipmobile.presentation.VerdictItemUi
import com.stainedhead.paperclipmobile.presentation.VerdictUi
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val LARGEST = 2.0f
private val MINIMUM_TARGET = 48.dp

/**
 * The Work list and the Issue screen at the largest font scale the system offers: the primary actions are displayed
 * (not clipped) and at least 48 dp tall. The baselines of the same screens are in `WorkScreenshotTest`.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class WorkLargeTextTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    /** A control inside the page scroll. */
    private fun ComposeTestRule.assertTarget(tag: String) {
        onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        onNodeWithTag(tag).assertHeightIsAtLeast(MINIMUM_TARGET)
    }

    /** A control outside the page scroll (the comment bar). */
    private fun ComposeTestRule.assertFixedTarget(tag: String) {
        onNodeWithTag(tag).assertIsDisplayed()
        onNodeWithTag(tag).assertHeightIsAtLeast(MINIMUM_TARGET)
    }

    private fun showAtLargest(
        ready: () -> Boolean,
        content: @Composable () -> Unit,
    ) {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent { app.ThemedAt(false, LARGEST, content) }
        rule.waitUntil(WAIT_MILLIS, ready)
        rule.waitForIdle()
    }

    @Test
    fun theWorkListCreateButtonTheFilterChipsAndARowFitAndAreAtLeast48dpTall() {
        showAtLargest(
            { rule.hasTag("work-row-${DemoId.ISSUE_RETRIES}") },
        ) { WorkListScreen(app.session, app.navigator) }

        rule.assertTarget("work-create")
        for (filter in IssueFilterOrder.chips) rule.assertTarget("work-filter-${filter.name}")
        rule.assertTarget("work-row-${DemoId.ISSUE_RETRIES}")
    }

    @Test
    fun theNewIssueSheetActionsFitAndAreAtLeast48dpTall() {
        showAtLargest({ rule.hasTag("work-create") }) { WorkListScreen(app.session, app.navigator) }

        rule.onNodeWithTag("work-create").performScrollTo().performClick()
        rule.awaitDisplayed("work-create-form")

        rule.assertTarget("work-create-title")
        rule.assertTarget("work-create-priority-High")
        rule.assertTarget("work-create-save")
        rule.assertTarget("work-create-cancel")
    }

    @Test
    fun theIssueTabsTheCommentBarAndTheQuestionButtonsFitAndAreAtLeast48dpTall() {
        showAtLargest({ rule.hasTag("interaction-int-1204-questions-primary") }) {
            IssueScreen(app.session, AppRoute.Issue(DemoId.ISSUE_RETRIES), app.navigator)
        }

        for (tab in listOf("Thread", "Plan", "Runs", "Files")) rule.assertTarget("issue-tab-$tab")
        rule.assertTarget("issue-assignee-menu")
        rule.assertTarget("issue-add-subtask")
        rule.assertFixedTarget("issue-comment-send")
        rule.assertFixedTarget("issue-status-menu")
        rule.assertFixedTarget("issue-comment-field")
        rule.assertTarget("interaction-int-1204-questions-primary")
        rule.assertTarget("interaction-int-1204-questions-choice-q-policy-exponential")
        rule.assertTarget("interaction-int-1204-questions-text-q-attempts")
    }

    @Test
    fun theWebAppButtonOfARequestTheAppCannotAnswerFitsAndIsAtLeast48dpTall() {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent {
            app.ThemedAt(false, LARGEST) {
                CompositionLocalProvider(LocalWebOpener provides WebOpener(RecordingLauncher())) {
                    InteractionCard(
                        interactionCard(
                            CardSpec(
                                id = "int-x",
                                kind = InteractionKindUi.Unsupported,
                                readOnly = InteractionReadOnlyUi.Unsupported,
                                primary = null,
                                openInWebUrl = "https://paperclip.example.ts.net/issues/PAP-9",
                            ),
                        ),
                        false,
                        {},
                    )
                }
            }
        }

        rule.assertFixedTarget("interaction-int-x-open-web")
    }

    @Test
    fun theVerdictButtonsOfARequestForVerdictsFitAndAreAtLeast48dpTall() {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent {
            app.ThemedAt(false, LARGEST) {
                InteractionCard(
                    interactionCard(
                        CardSpec(
                            id = "int-v",
                            kind = InteractionKindUi.RequestItemVerdicts,
                            primary = InteractionPrimaryUi.SubmitVerdicts,
                            items =
                                listOf(
                                    VerdictItemUi("i1", "Rename the table", null, null, ""),
                                ),
                            enabledVerdicts = VerdictUi.entries,
                        ),
                    ),
                    false,
                    {},
                )
            }
        }

        for (verdict in listOf(
            "Approve",
            "Reject",
            "Defer",
        )) {
            rule.assertFixedTarget("interaction-int-v-verdict-i1-$verdict")
        }
        rule.assertFixedTarget("interaction-int-v-primary")
    }
}
