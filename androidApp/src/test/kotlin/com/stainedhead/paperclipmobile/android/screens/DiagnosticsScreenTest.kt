package com.stainedhead.paperclipmobile.android.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.diagnostics.KeyErrorClass
import com.stainedhead.paperclipmobile.android.diagnostics.PairingOutcome
import com.stainedhead.paperclipmobile.android.diagnostics.SENSITIVE_STRINGS
import com.stainedhead.paperclipmobile.android.diagnostics.leakyFailure
import com.stainedhead.paperclipmobile.presentation.DeviceAuthIssue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** AND-018: typed, redacted, local-only diagnostics, and a report to copy. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h1400dp-xhdpi")
class DiagnosticsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun show() {
        rule.setContent { app.Themed { DiagnosticsScreen(app.session, app.navigator) } }
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("diagnostics") }
    }

    private fun clipboardText(): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
        return clip
            ?.getItemAt(0)
            ?.text
            ?.toString()
            .orEmpty()
    }

    @Test
    fun theScreenShowsWhatWasSeenAndSaysNotRecordedForWhatWasNot() {
        show()

        rule.onNodeWithTag("diagnostics-app-version").assertTextEquals("App version", "0.0.0")
        rule.onNodeWithTag("diagnostics-graph-open").assertTextEquals("Data opened", "Opened")
        rule.onNodeWithTag("diagnostics-server-compatibility").assertTextEquals("Server compatibility", "Not recorded")
        rule.onNodeWithTag("diagnostics-key-error").assertTextEquals("Key storage error", "Not recorded")
        rule.onNodeWithTag("diagnostics-biometric").assertTextEquals("Last unlock or confirmation", "Not recorded")
        rule.onNodeWithTag("diagnostics-pairing").assertTextEquals("Last pairing", "Not recorded")
    }

    @Test
    fun whatTheAppRecordsAppearsAsTypedValues() {
        app.session.diagnostics.recordGraphUnavailable(leakyFailure())
        app.session.diagnostics.recordKeyError(KeyErrorClass.KeyInvalidated)
        app.session.diagnostics.recordBiometric(DeviceAuthIssue.Cancelled)
        app.session.diagnostics.recordPairing(PairingOutcome.Expired)
        show()

        rule.onNodeWithTag("diagnostics-graph-open").assertTextEquals("Data opened", "Unavailable: other cause")
        rule.onNodeWithTag("diagnostics-key-error").assertTextEquals("Key storage error", "Key no longer valid")
        rule.onNodeWithTag("diagnostics-biometric").assertTextEquals(
            "Last unlock or confirmation",
            "Authentication was cancelled. Nothing was changed.",
        )
        rule.onNodeWithTag("diagnostics-pairing").assertTextEquals("Last pairing", "Expired")
    }

    @Test
    fun copyingPutsTheRedactedReportOnTheClipboardAndNothingSensitiveIsInIt() {
        app.session.diagnostics.recordGraphUnavailable(leakyFailure())
        app.session.diagnostics.recordPairing(PairingOutcome.Approved)
        show()

        rule.onNodeWithTag("diagnostics-copy").performScrollTo().performClick()

        rule.awaitDisplayed("diagnostics-copied")
        val copied = clipboardText()
        copied shouldBe
            app.session.diagnostics.report.value
                .toReportText()
        for (sensitive in SENSITIVE_STRINGS) copied shouldNotContain sensitive
    }

    @Test
    fun theScreenItselfShowsNoAddressNameOrKeyEvenThoughTheGraphHoldsThem() {
        app.session.diagnostics.recordGraphUnavailable(leakyFailure())
        show()

        for (sensitive in SENSITIVE_STRINGS) rule.hasText(sensitive, substring = true) shouldBe false
    }
}
