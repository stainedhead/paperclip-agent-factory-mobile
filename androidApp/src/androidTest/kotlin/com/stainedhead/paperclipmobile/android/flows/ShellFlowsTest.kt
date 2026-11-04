package com.stainedhead.paperclipmobile.android.flows

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.env.LaunchConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Acceptance criterion 2, the first run and the factory and company switches, on a device through the real shell.
 */
@RunWith(AndroidJUnit4::class)
class ShellFlowsTest : FlowTest() {
    @Test
    fun theFirstRunFlowIsWalkedFromTheAddressToTheInbox() {
        launch(LaunchConfiguration.EXTRA_DEMO_FIRST_RUN, ready = "first-run")

        rule.onNodeWithTag("address-field").performTextInput("https://paperclip.example.com")
        waitFor { rule.onNodeWithTag("check-address-button").isEnabledNow() }
        tap("check-address-button")
        waitFor { exists("probe-result") }
        tap("continue-button")
        waitFor { exists("pair-button") }
        tap("pair-button")
        waitFor { exists("finish-button") }
        rule.onNodeWithTag("finish-button").performScrollTo().performClick()

        waitFor { exists("tab-Inbox") }
        assertFalse(existsText("Add your first factory"))
    }

    @Test
    fun theDemoFactoryCanBeTriedFromTheFirstRunScreen() {
        launch(LaunchConfiguration.EXTRA_DEMO_FIRST_RUN, ready = "first-run")

        tap("try-demo-button")

        waitFor { exists("tab-Inbox") }
        waitFor { textOf("inbox-due-count").isNotEmpty() }
        assertFalse(exists("first-run"))
    }

    @Test
    fun anotherCompanyAndAnotherFactoryAreSwitchedTo() {
        launchDemo()
        openTab("Factories")
        waitFor { existsText("Home factory") }
        waitFor { exists("company-chip-demo-home-company-side") }
        assertTrue(selected("company-chip-demo-home-company-acme"))

        tap("company-chip-demo-home-company-side")
        waitFor { selected("company-chip-demo-home-company-side") }
        assertFalse(selected("company-chip-demo-home-company-acme"))
        assertEquals("company-side", appState.selectedCompanyId)
        assertEquals("demo-home", appState.selectedInstanceId)

        tap("factory-header-demo-test")
        waitFor { appState.selectedInstanceId == "demo-test" }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.isEnabledNow(): Boolean =
    androidx.compose.ui.semantics.SemanticsProperties.Disabled !in fetchSemanticsNode().config
