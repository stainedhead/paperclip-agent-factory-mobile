package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import com.stainedhead.paperclipmobile.presentation.CompanyGate
import com.stainedhead.paperclipmobile.presentation.ErrorKey
import com.stainedhead.paperclipmobile.presentation.ErrorUi
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** F-1 (ADR-150): what the company tabs show instead of "Loading..." when the factory has no usable company. */
@RunWith(AndroidJUnit4::class)
class CompanyGateScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var rechecks = 0
    private var opened = 0

    private fun show(
        gate: CompanyGate,
        error: ErrorUi? = null,
        checking: Boolean = false,
    ) = rule.setContent {
        PaperclipTheme(darkTheme = false) {
            CompanyGateContent(gate, checking, error, onRecheck = { rechecks++ }, onOpenFactories = { opened++ })
        }
    }

    @Test
    fun noCompanySaysSoAndOffersToCheckAgain() {
        show(CompanyGate.NoCompany)

        rule.onNodeWithText("No company yet").assertIsDisplayed()
        rule.onNodeWithText("Check again").performClick()

        rechecks shouldBe 1
    }

    @Test
    fun checkingShowsTheSearchAndNoButton() {
        show(CompanyGate.Checking)

        rule.onNodeWithText("Looking for your companies…").assertIsDisplayed()
        rule.onNodeWithTag("company-gate-recheck").assertDoesNotExist()
    }

    @Test
    fun severalCompaniesSendThePersonToFactories() {
        show(CompanyGate.ChooseCompany)

        rule.onNodeWithText("Choose a company").assertIsDisplayed()
        rule.onNodeWithText("Open Factories").performClick()

        opened shouldBe 1
    }

    @Test
    fun aFailedReadShowsItsErrorAndRetries() {
        show(CompanyGate.Failed, ErrorUi(ErrorKey.Unreachable))

        rule.onNodeWithText("Couldn't read the companies").assertIsDisplayed()
        rule.onNodeWithText("Can't reach the factory. Check your connection or VPN.").assertIsDisplayed()
        rule.onNodeWithText("Try again").performClick()

        rechecks shouldBe 1
    }
}
