package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.designsystem.ErrorBanner
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import com.stainedhead.paperclipmobile.presentation.DeviceAuthIssue
import com.stainedhead.paperclipmobile.presentation.ErrorKey
import com.stainedhead.paperclipmobile.presentation.ErrorUi
import com.stainedhead.paperclipmobile.presentation.FeedbackKind
import com.stainedhead.paperclipmobile.presentation.FeedbackUi
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** R-026: what appears on its own (a refusal, the Undo, a declined prompt, an error) is announced to TalkBack. */
@RunWith(AndroidJUnit4::class)
class LiveRegionTest {
    @get:Rule
    val rule = createComposeRule()

    private fun liveRegion(tag: String) =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion)

    @Test
    fun theFeedbackBannerIsAPoliteLiveRegion() {
        rule.setContent { PaperclipTheme(darkTheme = false) { FeedbackBanner(FeedbackUi(FeedbackKind.Failed), {}) } }

        liveRegion("feedback-banner") shouldBe LiveRegionMode.Polite
    }

    @Test
    fun theInboxUndoIsAPoliteLiveRegion() {
        rule.setContent { PaperclipTheme(darkTheme = false) { UndoSnackbar({}) } }

        liveRegion("inbox-undo") shouldBe LiveRegionMode.Polite
    }

    @Test
    fun aDeclinedPromptInTheNoteSheetIsAPoliteLiveRegion() {
        val text = NoteSheetText("Reject", "Note", "Why?", "Say why", "Reject")
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                NoteContent(text, {}, {}, true, false, DeviceAuthIssue.Cancelled, null)
            }
        }

        liveRegion("note-issue") shouldBe LiveRegionMode.Polite
    }

    @Test
    fun theErrorBannerIsAPoliteLiveRegion() {
        rule.setContent {
            PaperclipTheme(darkTheme = false) { ErrorBanner(ErrorUi(ErrorKey.Unreachable), onRetry = {}) }
        }

        liveRegion("error-banner") shouldBe LiveRegionMode.Polite
    }

    @Test
    fun theCopiedNoteIsAPoliteLiveRegion() {
        val app = DemoApp()
        try {
            rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
            rule.setContent { app.Themed { DiagnosticsScreen(app.session, app.navigator) } }
            rule.onNodeWithTag("diagnostics-copy").performScrollTo().performClick()

            liveRegion("diagnostics-copied") shouldBe LiveRegionMode.Polite
        } finally {
            app.close()
        }
    }
}
