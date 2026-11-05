package com.stainedhead.paperclipmobile.android

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
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
 * The acceptance path of milestone 2a-3 on the demo graph: a launch reaches the first-run flow, "Try the demo factory"
 * reaches the Inbox shell, and the first-run flow can be walked to its end. Holders run on background dispatchers, so
 * every step waits for the state it expects.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class MainActivityTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(
        vararg flags: String,
        biometrics: String? = null,
    ): ActivityScenario<MainActivity> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MainActivity::class.java)
        for (flag in flags) intent.putExtra(flag, true)
        if (biometrics != null) intent.putExtra(LaunchConfiguration.EXTRA_DEMO_BIOMETRICS, biometrics)
        return ActivityScenario.launch<MainActivity>(intent).also { scenario = it }
    }

    /** Waits for [condition]; on a timeout the semantics tree is printed, which says which screen was showing. */
    private fun waitFor(condition: () -> Boolean) {
        try {
            rule.waitUntil(WAIT_MILLIS, condition)
        } catch (timeout: ComposeTimeoutException) {
            println(rule.onRoot().printToString())
            throw timeout
        }
    }

    private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun existsText(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun secure(): Boolean {
        var flags = 0
        scenario?.onActivity { flags = it.window.attributes.flags }
        return flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    }

    @Test
    fun aDemoLaunchWithNoFactoryReachesFirstRunAndTryTheDemoFactoryReachesTheInboxShell() {
        launch(LaunchConfiguration.EXTRA_DEMO_FIRST_RUN)

        waitFor { exists("first-run") }
        rule.onNodeWithTag("try-demo-button").performScrollTo().performClick()

        waitFor { exists("tab-Inbox") }
        waitFor {
            rule
                .onAllNodesWithTag("tab-Inbox")
                .fetchSemanticsNodes()
                .firstOrNull()
                ?.config
                ?.getOrNull(SemanticsProperties.StateDescription) == "3 waiting"
        }
    }

    @Test
    fun aDemoLaunchWithAFactoryReachesTheTabsDirectly() {
        launch(LaunchConfiguration.EXTRA_DEMO)

        waitFor { exists("tab-Inbox") }

        exists("first-run") shouldBe false
        secure() shouldBe false
    }

    @Test
    fun theFirstRunFlowCanBeWalkedToItsEnd() {
        launch(LaunchConfiguration.EXTRA_DEMO_FIRST_RUN)
        waitFor { exists("first-run") }

        rule.onNodeWithTag("address-field").performTextInput("https://paperclip.example.com")
        waitFor { rule.onNodeWithTag("check-address-button").isEnabledNow() }
        rule.onNodeWithTag("check-address-button").performScrollTo().performClick()
        waitFor { exists("probe-result") }
        rule.onNodeWithTag("continue-button").performScrollTo().performClick()

        waitFor { exists("pair-button") }
        waitFor { secure() }
        rule.onNodeWithTag("pair-button").performScrollTo().performClick()

        waitFor { exists("finish-button") }
        secure() shouldBe false
        rule.onNodeWithTag("finish-button").performScrollTo().performClick()

        waitFor { exists("tab-Inbox") }
        existsText("Add your first factory") shouldBe false
    }

    @Test
    fun aCancelledUnlockKeepsTheLockUpAndTheWindowSecure() {
        launch(LaunchConfiguration.EXTRA_DEMO, biometrics = "cancelled")

        waitFor { exists("lock-screen") }
        waitFor { exists("unlock-issue") }

        secure() shouldBe true
        exists("tab-Inbox") shouldBe false
    }
}

private fun SemanticsNodeInteraction.isEnabledNow(): Boolean =
    SemanticsProperties.Disabled !in fetchSemanticsNode().config
