package com.stainedhead.paperclipmobile.android

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.env.LaunchConfiguration
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val WAIT_MILLIS = 15_000L

/**
 * AND-014 on the real activity at API 28 (the minimum), 33 (where `setRecentsScreenshotEnabled` arrives) and 36 (the
 * newest Robolectric ships; the emulator check ran API 37, recorded in status.md):
 * the lock screen is never in a screenshot, and below API 33 the window is secure while the activity is paused so the
 * recents thumbnail is blank. From 33 the system flag does that, so `FLAG_SECURE` stays off outside the lock.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28, 33, 36], qualifiers = "w360dp-h800dp-xhdpi")
class RecentsPrivacyTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(biometrics: String? = null): ActivityScenario<MainActivity> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MainActivity::class.java).putExtra(LaunchConfiguration.EXTRA_DEMO, true)
        if (biometrics != null) intent.putExtra(LaunchConfiguration.EXTRA_DEMO_BIOMETRICS, biometrics)
        return ActivityScenario.launch<MainActivity>(intent).also { scenario = it }
    }

    private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun secure(): Boolean {
        var flags = 0
        scenario?.onActivity { flags = it.window.attributes.flags }
        return flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    }

    @Test
    fun theLockScreenIsSecureOnEveryApiLevel() {
        launch(biometrics = "cancelled")
        rule.waitUntil(WAIT_MILLIS) { exists("lock-screen") }

        secure() shouldBe true
    }

    @Test
    fun theInboxIsNotSecureWhileItIsOnScreenOnEveryApiLevel() {
        launch()
        rule.waitUntil(WAIT_MILLIS) { exists("tab-Inbox") }

        secure() shouldBe false
    }

    @Test
    fun theWindowIsSecureWhilePausedBelowApi33OnlyAndCleanAgainOnResume() {
        val activity = launch()
        rule.waitUntil(WAIT_MILLIS) { exists("tab-Inbox") }

        activity.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        secure() shouldBe (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)

        activity.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        secure() shouldBe false
    }
}
