package com.stainedhead.paperclipmobile.android.screens

import androidx.activity.FullyDrawnReporter
import androidx.activity.FullyDrawnReporterOwner
import androidx.activity.compose.LocalFullyDrawnReporterOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import com.stainedhead.paperclipmobile.presentation.InboxPhase
import com.stainedhead.paperclipmobile.presentation.InboxState
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Inbox reports the activity fully drawn when it shows its content, and not while it is loading, so a startup
 * benchmark's time to full display is the time to the cached Inbox (T8.6, ADR-135).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class InboxFullyDrawnTest {
    @get:Rule
    val rule = createComposeRule()

    private var reports = 0

    private val owner =
        object : FullyDrawnReporterOwner {
            override val fullyDrawnReporter = FullyDrawnReporter(Runnable::run) { reports++ }
        }

    private fun show(state: InboxState) {
        rule.setContent {
            CompositionLocalProvider(LocalFullyDrawnReporterOwner provides owner) {
                PaperclipTheme { InboxContent(state, send = {}, open = {}) }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun theContentPhaseReportsFullyDrawnOnce() {
        show(InboxState(phase = InboxPhase.Content))

        reports shouldBe 1
    }

    @Test
    fun theLoadingPhaseDoesNotReport() {
        show(InboxState(phase = InboxPhase.Loading))

        reports shouldBe 0
    }
}
