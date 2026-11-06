package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.captureBaseline
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.presentation.BudgetChoice
import com.stainedhead.paperclipmobile.presentation.RoleUi
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

private const val LARGE_TEXT = 2.0f

private val BILLING =
    AppRoute.Decision(DemoId.DECISION_BILLING, "CTO", "today", "attention:decision:dec-1182", "PAP-1182")

/**
 * Roborazzi baselines for the attention family (T5.1 to T5.4) in light and dark, and at font scale 2.0 (PRD acceptance
 * criterion 8), on the demo graph: nine Acme Labs cards with the badge at 3, the billing decision with three options,
 * the QA hire approval and the Engineer 2 budget incident. Nothing is captured while a spinner runs. The pairing step
 * is never captured. Dates are read in UTC, so a baseline does not move with the host. `recordRoborazziDebug` writes
 * them under `src/test/screenshots`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1000dp-xhdpi")
class AttentionScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()
    private val hostZone = TimeZone.getDefault()

    @Before
    fun pinZone() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @After
    fun tearDown() {
        TimeZone.setDefault(hostZone)
        app.close()
    }

    private fun capture(
        name: String,
        darkTheme: Boolean,
        fontScale: Float? = null,
        ready: () -> Boolean,
        content: @Composable () -> Unit,
    ) {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent { app.ThemedAt(darkTheme, fontScale, content) }
        rule.waitUntil(WAIT_MILLIS, ready)
        rule.waitForIdle()
        val suffix =
            if (fontScale != null) {
                "large"
            } else if (darkTheme) {
                "dark"
            } else {
                "light"
            }
        rule.onRoot().captureBaseline("src/test/screenshots/$name-$suffix.png")
    }

    private fun inbox(
        darkTheme: Boolean,
        fontScale: Float? = null,
    ) = capture("inbox", darkTheme, fontScale, { rule.cardCount() > 0 }) { InboxScreen(app.session, app.navigator) }

    private fun inboxOffline(darkTheme: Boolean) {
        app.controls.setOnline(false)
        capture("inbox-offline", darkTheme, ready = { rule.hasTag("offline-banner") && rule.cardCount() > 0 }) {
            InboxScreen(app.session, app.navigator)
        }
    }

    private fun decision(
        darkTheme: Boolean,
        fontScale: Float? = null,
    ) = capture("decision", darkTheme, fontScale, { rule.hasTag("decision-option-ship") }) {
        DecisionScreen(app.session, BILLING, app.navigator)
    }

    @Test
    fun decisionLight() = decision(darkTheme = false)

    @Test
    fun decisionDark() = decision(darkTheme = true)

    @Test
    fun decisionLargeText() = decision(darkTheme = false, fontScale = LARGE_TEXT)

    private fun approval(
        darkTheme: Boolean,
        fontScale: Float? = null,
    ) = capture(
        "approval",
        darkTheme,
        fontScale,
        { rule.hasTag("approval-row-name") && rule.hasTag("approval-approve") },
    ) {
        ApprovalScreen(app.session, AppRoute.Approval(DemoId.APPROVAL_HIRE), app.navigator)
    }

    @Test
    fun approvalLight() = approval(darkTheme = false)

    @Test
    fun approvalDark() = approval(darkTheme = true)

    @Test
    fun approvalLargeText() = approval(darkTheme = false, fontScale = LARGE_TEXT)

    private fun budget(
        darkTheme: Boolean,
        fontScale: Float? = null,
    ) = capture("budget", darkTheme, fontScale, { true }) {
        BudgetContent(budgetState(RoleUi.Owner, BudgetChoice.RaiseAndResume), {}, onClose = {})
    }

    @Test
    fun budgetLight() = budget(darkTheme = false)

    @Test
    fun budgetDark() = budget(darkTheme = true)

    @Test
    fun budgetLargeText() = budget(darkTheme = false, fontScale = LARGE_TEXT)

    @Test
    fun inboxLight() = inbox(darkTheme = false)

    @Test
    fun inboxDark() = inbox(darkTheme = true)

    @Test
    fun inboxLargeText() = inbox(darkTheme = false, fontScale = LARGE_TEXT)

    @Test
    fun inboxOfflineLight() = inboxOffline(darkTheme = false)

    @Test
    fun inboxOfflineDark() = inboxOffline(darkTheme = true)
}
