package com.stainedhead.paperclipmobile.android.env

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.shell.LocalPairingSecurity
import com.stainedhead.paperclipmobile.android.shell.ReportPairingWait
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A re-pair started from a factory's edit screen is cancelled when the approval page cannot be shown, and hides the
 * window while it waits, exactly as the first-run pairing does (AND-014, AND-020).
 */
@RunWith(AndroidJUnit4::class)
class PairingRoutingTest {
    @get:Rule
    val rule = createComposeRule()

    private val app =
        com.stainedhead.paperclipmobile.android.screens
            .DemoApp()

    @Test
    fun aPageThatCouldNotBeShownCancelsEveryPairingThatIsWaitingAndOnlyThose() {
        var first = 0
        var second = 0
        val dropFirst = app.session.onPairingCancelled { first++ }
        app.session.onPairingCancelled { second++ }

        app.session.cancelPairingAttempts()
        dropFirst()
        app.session.cancelPairingAttempts()

        first shouldBe 1
        second shouldBe 2
        app.close()
    }

    @Test
    fun theWindowIsHiddenOnlyWhilePairingWaits() {
        val calls = mutableListOf<Boolean>()
        var waiting by mutableStateOf(false)
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalPairingSecurity provides { calls += it }) {
                ReportPairingWait(waiting)
            }
        }

        waiting = true
        rule.waitForIdle()
        waiting = false
        rule.waitForIdle()

        calls.last() shouldBe false
        (true in calls) shouldBe true
        app.close()
    }
}
