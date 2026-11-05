package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.android.shell.AppTab
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.FactoryHomeState
import com.stainedhead.paperclipmobile.presentation.FactoryTilesUi
import com.stainedhead.paperclipmobile.presentation.MoneyUi
import com.stainedhead.paperclipmobile.presentation.SpendUi
import com.stainedhead.paperclipmobile.presentation.SpendWindowUi
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val USD = "USD"
private const val CENTS_SPENT = 124_000L
private const val CENTS_BUDGET = 200_000L

private fun usd(cents: Long) = MoneyUi(cents, USD)

private fun tiles(hasBudget: Boolean) =
    FactoryTilesUi(
        agentsActive = 4,
        agentsRunning = 3,
        agentsPaused = 1,
        agentsError = 1,
        tasksOpen = 18,
        tasksInProgress = 5,
        tasksBlocked = 2,
        monthSpend = usd(CENTS_SPENT),
        monthBudget = usd(if (hasBudget) CENTS_BUDGET else 0),
        hasBudget = hasBudget,
        monthPercentUsed = if (hasBudget) 62 else 0,
        pendingApprovals = 2,
        budgetIncidents = 1,
    )

/** AND-050: Factory home on the demo graph; the tiles, the month spend against the budget, windows and runs. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class FactoryHomeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun show(ready: String = "factory-spend") {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent { app.Themed { FactoryHomeScreen(app.session, app.navigator) } }
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag(ready) }
    }

    @Test
    fun theMonthSpendIsAgainstTheBudgetWithThePercentAndTheWindows() {
        show()

        rule.onNodeWithTag("factory-spend").assertTextEquals("$1,240 of $2,000")
        rule.onNodeWithTag("factory-spend-percent").assertTextEquals("62% of the monthly budget used")
        rule.onNodeWithTag("factory-window-Last5Hours").assertTextEquals("5h", "$38")
        rule.onNodeWithTag("factory-window-Last24Hours").assertTextEquals("24h", "$212")
        rule.onNodeWithTag("factory-window-Last7Days").assertTextEquals("7d", "$1,015")
        rule.onNodeWithTag("updated-label").assertTextEquals("Updated 0s ago")
    }

    @Test
    fun theFourTilesCarryTheCountsOfTheMock() {
        show()

        rule.onNodeWithTag("factory-tile-agents").assertTextEquals("AGENTS", "3 running", "1 error · 1 paused")
        rule.onNodeWithTag("factory-tile-work").assertTextEquals("WORK", "18 open", "2 blocked · 5 in progress")
        rule.onNodeWithTag("factory-tile-approvals").assertTextEquals("APPROVALS", "2 pending")
        rule.onNodeWithTag("factory-tile-budget").assertTextEquals("BUDGET INCIDENTS", "1 open")
    }

    @Test
    fun theRunsInProgressNameWhoAndWhat() {
        show()
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("factory-run-run-1204-live") }

        rule.onNodeWithTag("factory-run-run-1204-live").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("factory-run-run-1204-live").assertTextEquals(
            "Engineer 1 · PAP-1204",
            "Add retries to webhook ingest",
            "4m",
            "Running",
        )
        rule.onNodeWithTag("factory-run-run-cto-live").assertTextEquals(
            "CTO · PAP-1182",
            "Billing v2",
            "1m",
            "Running",
        )
        rule.hasTag("factory-run-run-design-live") shouldBe true
        rule.hasTag("factory-run-run-1204-done") shouldBe false
    }

    @Test
    fun aRunOpensTheRunViewer() {
        show()
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("factory-run-run-cto-live") }

        rule.onNodeWithTag("factory-run-run-cto-live").performScrollTo().performClick()

        app.pushed shouldBe listOf(AppRoute.Run("run-cto-live"))
    }

    @Test
    fun eachTileShowsTheTabThatHoldsItsThing() {
        show()

        rule.onNodeWithTag("factory-tile-agents").performClick()
        rule.onNodeWithTag("factory-tile-work").performClick()
        rule.onNodeWithTag("factory-tile-approvals").performClick()
        rule.onNodeWithTag("factory-tile-budget").performScrollTo().performClick()

        app.selected shouldBe listOf(AppTab.Agents, AppTab.Work, AppTab.Inbox, AppTab.Inbox)
    }

    @Test
    fun offlineKeepsWhatWasLoadedAndSaysHowOldItIs() {
        show()
        app.controls.setOnline(false)

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("offline-banner") }
        rule.onNodeWithTag("factory-spend").assertTextEquals("$1,240 of $2,000")
        rule.onNodeWithTag("updated-label").assertIsDisplayed()
    }

    @Test
    fun aFailedFirstReadOffersTryAgainAndRecovers() {
        app.controls.failReads(true)
        show(ready = "detail-failed")

        app.controls.failReads(false)
        rule.onNodeWithText("Try again").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("factory-spend") }
        rule.onNodeWithTag("factory-spend").assertTextEquals("$1,240 of $2,000")
    }

    @Test
    fun withoutAPeriodBudgetOnlyTheAmountShowsAndAnUnreadableWindowSaysSo() {
        val state =
            FactoryHomeState(
                phase = DetailPhase.Content,
                tiles = tiles(hasBudget = false),
                spendUnavailable = true,
                updatedAtEpochMillis = 1L,
            )
        rule.setContent { app.Themed { FactoryHomeContent(state, {}, {}, {}) } }

        rule.onNodeWithTag("factory-spend").assertTextEquals("$1,240")
        rule.hasTag("factory-spend-percent") shouldBe false
        rule.onNodeWithTag("factory-spend-unavailable").assertIsDisplayed()
        rule.onNodeWithTag("factory-nothing-running").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theWindowsAppearInTheOrderTheStateGivesThem() {
        val state =
            FactoryHomeState(
                phase = DetailPhase.Content,
                tiles = tiles(hasBudget = true),
                spend = listOf(SpendUi(SpendWindowUi.Last7Days, usd(CENTS_SPENT))),
                updatedAtEpochMillis = 1L,
            )
        rule.setContent { app.Themed { FactoryHomeContent(state, {}, {}, {}) } }

        rule.onNodeWithTag("factory-window-Last7Days").assertTextEquals("7d", "$1,240")
        rule.hasTag("factory-window-Last5Hours") shouldBe false
    }
}
