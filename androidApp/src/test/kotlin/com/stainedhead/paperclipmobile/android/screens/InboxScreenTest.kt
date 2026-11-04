package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.presentation.InboxItemUi
import com.stainedhead.paperclipmobile.presentation.InboxPhase
import com.stainedhead.paperclipmobile.presentation.InboxState
import com.stainedhead.paperclipmobile.presentation.SourceKindUi
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val DEMO_CARDS = 9
private const val DEMO_DECIDE_NOW = 3
private const val DEMO_BADGE = 3

/** The number of Inbox cards on screen (the wrapper tag of each card is `inbox-card-<id>`). */
fun ComposeTestRule.cardCount(): Int =
    onAllNodes(
        SemanticsMatcher("an inbox card") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("inbox-card-") == true
        },
    ).fetchSemanticsNodes().size

/** Runs the TalkBack action named [label] on the card whose item has [id]. */
fun ComposeTestRule.performCardAction(
    id: String,
    label: String,
) {
    val node =
        onNode(hasTestTag("attention-card-open") and hasAnyAncestor(hasTestTag("inbox-card-$id")))
            .fetchSemanticsNode()
    val action = node.config[SemanticsActions.CustomActions].first { it.label == label }
    runOnUiThread { action.action() }
}

/** AND-030: the Inbox on the demo graph; the nine Acme Labs cards, badge, filters, inline actions, swipe actions. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class InboxScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()
    private val openedBudget = mutableListOf<String>()

    @After
    fun tearDown() = app.close()

    private val state: InboxState get() = checkNotNull(app.session.inbox.value).state.value

    private fun item(title: String): InboxItemUi = state.sections.flatMap { it.items }.first { it.title == title }

    private fun show() {
        rule.setContent { app.Themed { InboxScreen(app.session, app.navigator) { openedBudget += it } } }
        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == DEMO_CARDS }
    }

    @Test
    fun theDemoInboxShowsNineCardsThreeInDecideNowAndTheServersBadge() {
        show()

        app.session.inboxBadge.value shouldBe DEMO_BADGE
        state.sections
            .first()
            .items.size shouldBe DEMO_DECIDE_NOW
        for (section in listOf("DecideNow", "ThisWeek", "Whenever")) {
            rule.onNodeWithTag("inbox-section-$section").assertIsDisplayed()
        }
        for (card in state.sections.first().items) rule.onNodeWithTag("inbox-card-${card.id}").assertIsDisplayed()
        rule.onNodeWithTag("inbox-due-count").assertIsDisplayed()
        rule.onNodeWithText("3 new or due today").assertIsDisplayed()
        rule.hasTag("updated-label") shouldBe true
    }

    @Test
    fun theFilterChipsCarryTheServersCountsAndNarrowTheList() {
        show()

        rule.onNodeWithText("All 9").assertIsDisplayed()
        rule.onNodeWithText("Decisions 3").assertIsDisplayed()
        rule.onNodeWithText("Approvals 2").assertIsDisplayed()
        rule.isSelected("inbox-filter-all") shouldBe true

        rule.onNodeWithTag("inbox-filter-Approval").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == 2 }
        rule.isSelected("inbox-filter-Approval") shouldBe true
        rule.onNodeWithTag("inbox-filter-all").performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == DEMO_CARDS }
    }

    @Test
    fun approvingAnApprovalInlineRemovesItWithoutAskingTheDeviceOwner() {
        show()
        val hire = item("Hire a QA engineer agent")

        rule.onNodeWithTag("inbox-approve-${hire.id}").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == DEMO_CARDS - 1 }
        app.controls.biometricPromptCount shouldBe 0
    }

    @Test
    fun rejectingAsksForANoteThenTheDeviceOwnerAndNothingGoesBeforeTheyAgree() {
        show()
        val hire = item("Hire a QA engineer agent")
        app.scriptBiometrics(BiometricResult.Cancelled)

        rule.onNodeWithTag("inbox-reject-${hire.id}").performClick()
        rule.onNodeWithTag("note-sheet").assertIsDisplayed()
        rule.onNodeWithTag("note-field").performTextInput("Too expensive")
        rule.onNodeWithTag("note-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("note-issue") }
        app.controls.biometricPromptCount shouldBe 1
        rule.cardCount() shouldBe DEMO_CARDS
        rule.onNodeWithTag("note-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == DEMO_CARDS - 1 }
        app.controls.biometricPromptCount shouldBe 2
        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag("note-sheet") }
    }

    @Test
    fun cancellingTheRejectNoteLeavesTheCardAndAsksNoOne() {
        show()
        val hire = item("Hire a QA engineer agent")

        rule.onNodeWithTag("inbox-reject-${hire.id}").performClick()
        rule.onNodeWithTag("note-cancel").performClick()

        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag("note-sheet") }
        app.controls.biometricPromptCount shouldBe 0
        rule.cardCount() shouldBe DEMO_CARDS
    }

    @Test
    fun theCardsOpenTheirScreensAndTheDecisionCarriesWhatTheCardKnew() {
        show()
        val decision = item("Ship the billing migration to production?")
        val hire = item("Hire a QA engineer agent")

        rule.onNodeWithTag("inbox-review-${decision.id}").performClick()
        rule.onNodeWithTag("inbox-details-${hire.id}").performClick()

        app.pushed shouldBe
            listOf(
                AppRoute.Decision(DemoId.DECISION_BILLING, "CTO", "today", decision.dismissalKey, "PAP-1182"),
                AppRoute.Approval(DemoId.APPROVAL_HIRE),
            )
    }

    @Test
    fun theBudgetCardKeepsPausedInlineAndRaiseOpensTheSheet() {
        show()
        val budget = item("Engineer 2 hit its monthly budget")

        rule.onNodeWithTag("inbox-raise-${budget.id}").performClick()

        openedBudget shouldBe listOf(DemoId.INCIDENT)
        rule.onNodeWithTag("inbox-keep-paused-${budget.id}").performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == DEMO_CARDS - 1 }
    }

    @Test
    fun dismissingFromTheCardsActionRemovesItAndUndoBringsItBack() {
        show()
        val keys = item("Rotate the signing keys this quarter?")

        rule.performCardAction(keys.id, "Dismiss")

        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == DEMO_CARDS - 1 }
        rule.onNodeWithTag("inbox-undo").assertIsDisplayed()
        rule.onNodeWithText("Removed from your Inbox").assertIsDisplayed()
        rule.onNodeWithTag("inbox-undo-button").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == DEMO_CARDS }
        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag("inbox-undo") }
    }

    @Test
    fun snoozingAsksForAPresetFirstThenRemovesTheCardWithAnUndo() {
        show()
        val keys = item("Rotate the signing keys this quarter?")

        rule.performCardAction(keys.id, "Snooze")

        rule.onNodeWithTag("snooze-sheet").assertIsDisplayed()
        rule.cardCount() shouldBe DEMO_CARDS
        rule.onNodeWithTag("snooze-OneHour").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == DEMO_CARDS - 1 }
        rule.onNodeWithTag("inbox-undo").assertIsDisplayed()
        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag("snooze-sheet") }
    }

    @Test
    fun offlineTheActionsAreDisabledWithTheirReasonAndOpeningStillWorks() {
        show()
        val hire = item("Hire a QA engineer agent")

        app.controls.setOnline(false)

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("offline-banner") }
        rule.onNodeWithText("You're offline. Actions are unavailable.").assertIsDisplayed()
        rule.onNodeWithTag("inbox-approve-${hire.id}").assertIsNotEnabled()
        rule.onNodeWithTag("inbox-reject-${hire.id}").assertIsNotEnabled()
        rule.onNodeWithTag("inbox-details-${hire.id}").performScrollTo().performClick()
        app.pushed shouldContain AppRoute.Approval(DemoId.APPROVAL_HIRE)
        state.phase shouldBe InboxPhase.Content
        state.sections.flatMap { it.items }.any { it.kind == SourceKindUi.Approval } shouldBe true
    }
}
