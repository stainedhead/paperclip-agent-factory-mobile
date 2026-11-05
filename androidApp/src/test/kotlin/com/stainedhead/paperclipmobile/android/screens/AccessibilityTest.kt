package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.designsystem.FormField
import com.stainedhead.paperclipmobile.android.designsystem.StatusPill
import com.stainedhead.paperclipmobile.android.designsystem.StatusTone
import com.stainedhead.paperclipmobile.android.designsystem.symbol
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.android.shell.ConfirmContent
import com.stainedhead.paperclipmobile.android.shell.ConfirmDestructiveContent
import com.stainedhead.paperclipmobile.android.shell.LockActions
import com.stainedhead.paperclipmobile.android.shell.LockScreen
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val BILLING =
    AppRoute.Decision(DemoId.DECISION_BILLING, "CTO", "today", "attention:decision:dec-1182", "PAP-1182")

/**
 * T8.2, what TalkBack gets from each screen family on the demo graph: a name, a role and a 48 dp target on every
 * control, and a reading order that follows the layout. Failures are fixed, not listed; the checks Robolectric cannot
 * make are in ADR-134. The audit itself is proven by a screen with planted violations.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2000dp-xxhdpi")
class AccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private var findings = emptyList<String>()

    private fun audited(
        ready: () -> Boolean,
        content: @Composable () -> Unit,
    ): List<String> = auditedAfter(ready, {}, content)

    /** [act] runs once the screen is up (it opens a sheet); a sheet is its own window, so every root is audited. */
    private fun auditedAfter(
        ready: () -> Boolean,
        act: () -> Unit,
        content: @Composable () -> Unit,
    ): List<String> {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        var density =
            androidx.compose.ui.unit
                .Density(1f)
        rule.setContent {
            density = LocalDensity.current
            app.Themed { content() }
        }
        act()
        rule.waitUntil(WAIT_MILLIS, ready)
        rule.waitForIdle()
        findings = rule.onAllNodes(isRoot()).fetchSemanticsNodes().flatMap { audit(it, density) }
        return findings
    }

    @Test
    fun inbox() {
        audited({ rule.cardCount() > 0 }) { InboxScreen(app.session, app.navigator) } shouldBe emptyList()
    }

    @Test
    fun inboxRightToLeft() {
        audited({ rule.cardCount() > 0 }) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                InboxScreen(app.session, app.navigator)
            }
        } shouldBe emptyList()
    }

    @Test
    fun decision() {
        audited({ rule.hasTag("decision-confirm") }) { DecisionScreen(app.session, BILLING, app.navigator) } shouldBe
            emptyList()
    }

    @Test
    fun approval() {
        audited({ rule.hasTag("approval-approve") }) {
            ApprovalScreen(app.session, AppRoute.Approval(DemoId.APPROVAL_HIRE), app.navigator)
        } shouldBe emptyList()
    }

    @Test
    fun factoryHome() {
        audited({ rule.hasTag("factory-run-run-cto-live") }) { FactoryHomeScreen(app.session, app.navigator) } shouldBe
            emptyList()
    }

    @Test
    fun agents() {
        audited(
            { rule.hasTag("agent-${DemoId.AGENT_CTO}-pause") },
        ) { AgentsScreen(app.session, app.navigator) } shouldBe
            emptyList()
    }

    @Test
    fun agentDetail() {
        audited({ rule.hasTag("agent-detail-pause") }) {
            AgentDetailScreen(app.session, AppRoute.Agent(DemoId.AGENT_ENGINEER_1), app.navigator)
        } shouldBe emptyList()
    }

    @Test
    fun runViewer() {
        audited({ rule.hasTag("run-log") && rule.hasTag("run-cancel") }) {
            RunViewerScreen(app.session, AppRoute.Run(DemoId.RUN_LIVE), app.navigator)
        } shouldBe emptyList()
    }

    @Test
    fun work() {
        audited(
            { rule.hasTag("work-row-${DemoId.ISSUE_RETRIES}") },
        ) { WorkListScreen(app.session, app.navigator) } shouldBe
            emptyList()
    }

    @Test
    fun issue() {
        audited({ rule.hasTag("issue-comment-send") }) {
            IssueScreen(app.session, AppRoute.Issue(DemoId.ISSUE_RETRIES), app.navigator)
        } shouldBe emptyList()
    }

    @Test
    fun factories() {
        audited({ rule.hasTag("add-factory-button") }) { FactoriesScreen(app.session, app.navigator) } shouldBe
            emptyList()
    }

    @Test
    fun editFactory() {
        audited(
            { rule.hasTag("edit-save-button") },
        ) { EditFactoryScreen(app.session, DemoId.HOME, app.navigator) } shouldBe
            emptyList()
    }

    @Test
    fun settings() {
        audited({ rule.hasTag("settings") }) { SettingsScreen(app.session, app.navigator) } shouldBe emptyList()
    }

    @Test
    fun lock() {
        audited({ true }) { LockScreen(LockActions(null) {}) } shouldBe emptyList()
    }

    @Test
    fun manageFactories() {
        audited({ rule.hasText("Home factory") }) { ManageFactoriesScreen(app.session, app.navigator) } shouldBe
            emptyList()
    }

    @Test
    fun pairedDevices() {
        audited(
            { rule.hasTag("device-key-bk-demo-a91f") },
        ) { PairedDevicesScreen(app.session, app.navigator) } shouldBe emptyList()
    }

    @Test
    fun diagnostics() {
        audited({ rule.hasTag("diagnostics") }) { DiagnosticsScreen(app.session, app.navigator) } shouldBe emptyList()
    }

    @Test
    fun firstRun() {
        val first = firstRunApp()
        try {
            var density =
                androidx.compose.ui.unit
                    .Density(1f)
            rule.setContent {
                density = LocalDensity.current
                first.Themed { FirstRunScreen(first.session, onTryDemo = {}) }
            }
            rule.waitUntil(WAIT_MILLIS) { rule.hasTag("address-field") }
            rule.waitForIdle()
            rule.onAllNodes(isRoot()).fetchSemanticsNodes().flatMap { audit(it, density) } shouldBe emptyList()
        } finally {
            first.close()
        }
    }

    @Test
    fun budgetSheetOfAnAgent() {
        auditedAfter(
            { rule.hasTag("agent-budget-form") },
            {
                rule.waitUntil(WAIT_MILLIS) { rule.hasTag("agent-set-budget") }
                rule.onNodeWithTag("agent-set-budget").performClick()
            },
        ) { AgentDetailScreen(app.session, AppRoute.Agent(DemoId.AGENT_ENGINEER_1), app.navigator) } shouldBe
            emptyList()
    }

    @Test
    fun budgetIncidentSheet() {
        auditedAfter(
            { rule.hasTag("budget-usage") },
            {
                rule.waitUntil(WAIT_MILLIS) { rule.cardCount() > 0 }
                val raise =
                    checkNotNull(app.session.inbox.value)
                        .state.value.sections
                        .flatMap { it.items }
                        .first { it.title == "Engineer 2 hit its monthly budget" }
                rule.onNodeWithTag("inbox-raise-${raise.id}").performClick()
            },
        ) { InboxScreen(app.session, app.navigator) } shouldBe emptyList()
    }

    @Test
    fun noteSheet() {
        auditedAfter(
            { rule.hasTag("note-sheet") },
            {
                rule.waitUntil(WAIT_MILLIS) { rule.hasTag("approval-reject") }
                rule.onNodeWithTag("approval-reject").performClick()
            },
        ) { ApprovalScreen(app.session, AppRoute.Approval(DemoId.APPROVAL_HIRE), app.navigator) } shouldBe
            emptyList()
    }

    @Test
    fun snoozeSheet() {
        auditedAfter(
            { rule.hasTag("snooze-sheet") },
            {
                rule.waitUntil(WAIT_MILLIS) { rule.cardCount() > 0 }
                val keys =
                    checkNotNull(app.session.inbox.value)
                        .state.value.sections
                        .flatMap { it.items }
                        .first { it.title == "Rotate the signing keys this quarter?" }
                rule.performCardAction(keys.id, "Snooze")
            },
        ) { InboxScreen(app.session, app.navigator) } shouldBe emptyList()
    }

    @Test
    fun confirmSheet() {
        audited({ rule.hasTag("confirm-sheet-confirm") }) {
            ConfirmContent("Sign out?", "You will need to pair again.", "Sign out", {}, {})
        } shouldBe emptyList()
    }

    @Test
    fun confirmDestructiveSheet() {
        audited({ rule.hasTag("confirm-destructive-confirm") }) {
            ConfirmDestructiveContent("Pause Engineer 1?", "It stops picking up work.", "Pause agent", {}, {})
        } shouldBe emptyList()
    }

    @Test
    fun theAuditReportsAnUnnamedRolelessControl() {
        audited({ true }) {
            Box {
                Box(Modifier.size(20.dp).background(Color.Red).clickable {})
            }
        }.joinToString("\n").let {
            it shouldContain "no name"
            it shouldContain "no role"
        }
    }

    @Test
    fun theAuditReportsAShownClickableControlWithNoWidthOrNoHeight() {
        audited({ true }) {
            Column {
                Box(
                    Modifier
                        .width(0.dp)
                        .height(48.dp)
                        .clickable {}
                        .semantics { contentDescription = "New issue" },
                )
                Box(
                    Modifier
                        .width(48.dp)
                        .height(0.dp)
                        .clickable {}
                        .semantics { contentDescription = "Flat" },
                )
            }
        }.joinToString("\n").let {
            it shouldContain "zero size"
            it shouldContain "New issue"
            it shouldContain "Flat"
        }
    }

    @Test
    fun theAuditReportsATextFieldNamedOnlyByAContentDescription() {
        audited({ true }) {
            OutlinedTextField("typed", {}, Modifier.semantics { contentDescription = "Name" })
        }.joinToString("\n") shouldContain "text field named only by a description"
    }

    @Test
    fun aFormFieldIsAcceptedByTheAudit() {
        audited({ true }) {
            FormField("Name", "typed", {}, "name", issue = "Too short")
        } shouldBe emptyList()
    }

    @Test
    fun noToneIsToldByColourAloneEachHasItsOwnSymbolAndTheWordsAreShown() {
        StatusTone.entries
            .map { it.symbol() }
            .toSet()
            .size shouldBe StatusTone.entries.size
        audited({ true }) {
            Column {
                StatusTone.entries.forEach { StatusPill("state ${it.name}", it) }
            }
        }
        StatusTone.entries.forEach { rule.onNodeWithText("state ${it.name}").assertIsDisplayed() }
    }
}
