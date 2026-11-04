package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** AND-021: every factory with its status, key expiry and companies; switching; the way to the rest. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h1400dp-xhdpi")
class FactoriesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun show() {
        rule.setContent { app.Themed { FactoriesScreen(app.session, app.navigator) } }
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("Home factory") }
    }

    @Test
    fun theDemoFactoriesShowTheirStatusAndKeyDaysAsInTheMock() {
        show()

        rule.onNodeWithTag("factory-status-demo-home", useUnmergedTree = true).assertTextEquals("Connected")
        rule.onNodeWithTag("factory-expiry-demo-home").assertTextEquals("Board key expires in 23 days")
        rule.onNodeWithTag("factory-status-demo-cloud", useUnmergedTree = true).assertTextEquals("Key expired")
        rule.onNodeWithTag("factory-expiry-demo-cloud").assertTextEquals("Board key expired")
        rule.onNodeWithTag("factory-status-demo-test", useUnmergedTree = true).assertTextEquals("Signed out")
        rule.hasTag("factory-expiry-demo-test") shouldBe false
        rule.onNodeWithText("https://paperclip.tail1234.ts.net").assertIsDisplayed()
        rule.onNodeWithText("http://localhost:3100").assertIsDisplayed()
    }

    @Test
    fun pairAgainIsOfferedOnlyWhereThePhoneHasNoWorkingKey() {
        show()

        rule.hasTag("pair-again-demo-home") shouldBe false
        rule.onNodeWithTag("pair-again-demo-cloud").assertIsDisplayed()
        rule.onNodeWithTag("pair-again-demo-test").assertIsDisplayed()

        rule.onNodeWithTag("pair-again-demo-cloud").performClick()

        app.pushed shouldBe listOf(AppRoute.EditFactory(DemoId.CLOUD))
    }

    @Test
    fun theCompaniesOfTheConnectedFactoryAppearAndTheSelectedOneIsMarked() {
        show()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("company-chip-demo-home-company-acme") }
        rule.onNodeWithText("Side Project Co").assertIsDisplayed()
        rule.isSelected("company-chip-demo-home-company-acme") shouldBe true
        rule.isSelected("company-chip-demo-home-company-side") shouldBe false
    }

    @Test
    fun choosingAnotherCompanySwitchesTheAppToIt() {
        show()
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("company-chip-demo-home-company-side") }

        rule.onNodeWithTag("company-chip-demo-home-company-side").performClick()

        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId == DemoId.SIDE_PROJECT }
        app.appState.selectedInstanceId shouldBe DemoId.HOME
        rule.waitUntil(WAIT_MILLIS) { rule.isSelected("company-chip-demo-home-company-side") }
    }

    @Test
    fun tappingAFactorySwitchesTheAppToIt() {
        show()
        app.appState.selectedInstanceId shouldBe DemoId.HOME

        rule.onNodeWithTag("factory-header-demo-test").performClick()

        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedInstanceId == DemoId.TEST }
    }

    @Test
    fun theDoorsOpenTheirScreens() {
        show()

        rule.onNodeWithTag("factory-details-demo-home").performClick()
        rule.onNodeWithTag("manage-factories-button").performClick()
        rule.onNodeWithTag("settings-button").performClick()
        rule.onNodeWithTag("add-factory-button").performScrollTo().performClick()

        app.pushed shouldBe
            listOf(
                AppRoute.EditFactory(DemoId.HOME),
                AppRoute.ManageFactories,
                AppRoute.Settings,
                AppRoute.EditFactory(null),
            )
    }

    @Test
    fun offlineTheListShowsTheLastLoadedFactoriesWithTheirAge() {
        show()

        app.controls.setOnline(false)

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("offline-banner") }
        rule.onNodeWithText("Home factory").assertIsDisplayed()
    }

    @Test
    fun withNoFactoryTheScreenInvitesToAddOne() {
        app.close()
        app = firstRunApp()
        rule.setContent { app.Themed { FactoriesScreen(app.session, app.navigator) } }

        rule.waitUntil(WAIT_MILLIS) { rule.hasText("No factories yet") }
        rule.onNodeWithTag("empty-state-action").performClick()

        app.pushed shouldBe listOf(AppRoute.EditFactory(null))
    }
}
