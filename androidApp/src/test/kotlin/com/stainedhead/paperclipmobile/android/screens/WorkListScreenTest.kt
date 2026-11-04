package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val ALL_OPEN_ROWS = 9

/** AND-060: the Work tab on the demo graph; saved filters, search and creating an issue. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class WorkListScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun show() {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent { app.Themed { WorkListScreen(app.session, app.navigator) } }
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("work-row-${DemoId.ISSUE_RETRIES}") }
    }

    private fun rows(): List<String> =
        listOf(
            "pap-1182",
            "pap-1183",
            "pap-1184",
            "pap-1185",
            "pap-1186",
            "pap-1190",
            "pap-1199",
            "pap-1204",
            "pap-1210",
        ).filter { rule.hasTag("work-row-$it") }

    /** The sheet slides in: wait until its form is on the screen before touching it. */
    private fun openCreateSheet() {
        rule.onNodeWithTag("work-create").performScrollTo().performClick()
        rule.waitUntil(
            WAIT_MILLIS,
        ) { runCatching { rule.onNodeWithTag("work-create-form").assertIsDisplayed() }.isSuccess }
    }

    private fun choose(filter: String) {
        rule.onNodeWithTag("work-filter-$filter").performScrollTo().performClick()
    }

    @Test
    fun allOpenIsTheFirstFilterAndListsEveryOpenIssueWithItsPills() {
        show()

        rule.isSelected("work-filter-AllOpen") shouldBe true
        rows().size shouldBe ALL_OPEN_ROWS
        rule.onNodeWithTag("work-count").assertTextEquals("9 issues")
        rule.onNodeWithTag("work-row-${DemoId.ISSUE_RETRIES}").assertContentDescriptionEquals(
            "PAP-1204. Add retries to webhook ingest. Blocked · High · Engineer 1",
        )
        rule.onNodeWithTag("updated-label").assertTextEquals("Updated 0s ago")
    }

    @Test
    fun eachSavedFilterShowsTheRowsTheServerRulesGiveIt() {
        show()

        choose("Blocked")
        rule.waitUntil(WAIT_MILLIS) { rows() == listOf(DemoId.ISSUE_RETRIES) }
        rule.isSelected("work-filter-Blocked") shouldBe true
        rule.onNodeWithTag("work-count").assertTextEquals("1 issue")

        choose("InReview")
        rule.waitUntil(WAIT_MILLIS) { rows() == listOf("pap-1210") }

        choose("CriticalAndHigh")
        rule.waitUntil(WAIT_MILLIS) {
            rows() == listOf("pap-1182", "pap-1183", DemoId.ISSUE_REFUNDS, DemoId.ISSUE_RETRIES)
        }

        choose("AssignedToMe")
        rule.waitUntil(WAIT_MILLIS) { rows().isEmpty() && rule.hasTag("work-empty") }
        rule.onNodeWithTag("work-empty").assertIsDisplayed()

        choose("AllOpen")
        rule.waitUntil(WAIT_MILLIS) { rows().size == ALL_OPEN_ROWS }
    }

    @Test
    fun searchIsSentOnSubmitAndClearingTheBoxShowsTheFilterAgainAtOnce() {
        show()

        rule.onNodeWithTag("work-search").performTextInput("retries")
        rows().size shouldBe ALL_OPEN_ROWS
        rule.onNodeWithTag("work-search").performImeAction()
        rule.waitUntil(WAIT_MILLIS) { rows() == listOf(DemoId.ISSUE_RETRIES) }

        rule.onNodeWithTag("work-search").performTextReplacement("")
        rule.waitUntil(WAIT_MILLIS) { rows().size == ALL_OPEN_ROWS }
    }

    @Test
    fun aSearchThatMatchesNothingSaysSo() {
        show()

        rule.onNodeWithTag("work-search").performTextInput("zzz")
        rule.onNodeWithTag("work-search").performImeAction()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("work-empty") }
        rule.onNodeWithTag("work-empty").assertTextContains("Nothing matches that search.", substring = true)
    }

    @Test
    fun aRowOpensItsIssue() {
        show()

        rule.onNodeWithTag("work-row-${DemoId.ISSUE_RETRIES}").performScrollTo().performClick()

        app.pushed shouldBe listOf(AppRoute.Issue(DemoId.ISSUE_RETRIES))
    }

    @Test
    fun aBlankTitleIsRefusedAndTheSheetStaysOpen() {
        show()

        openCreateSheet()
        rule.onNodeWithTag("work-create-save").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("feedback-banner") }
        rule.onNodeWithTag("work-create-form").assertIsDisplayed()
        app.pushed shouldBe emptyList()
    }

    @Test
    fun aNewIssueIsCreatedWithItsPriorityAndOpened() {
        show()

        openCreateSheet()
        rule.onNodeWithTag("work-create-title").performTextInput("Rotate the webhook secret")
        rule.onNodeWithTag("work-create-priority-High").performClick()
        rule.onNodeWithTag("work-create-save").performClick()

        rule.waitUntil(WAIT_MILLIS) { app.pushed.isNotEmpty() }
        val route = app.pushed.single().shouldBeInstanceOf<AppRoute.Issue>()
        (route.id in rows()) shouldBe false
        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag("work-create-form") }
        rule.waitUntil(WAIT_MILLIS) { rule.texts("work-count") == listOf("10 issues") }
    }

    @Test
    fun offlineKeepsTheRowsSaysSoAndOffersNoNewIssue() {
        show()
        app.controls.setOnline(false)

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("offline-banner") }
        rule.hasTag("work-create") shouldBe false
        rule.hasTag("work-row-${DemoId.ISSUE_RETRIES}") shouldBe true
    }
}
