package com.stainedhead.paperclipmobile.android.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.presentation.ActionsDisabledReason
import com.stainedhead.paperclipmobile.presentation.ErrorKey
import com.stainedhead.paperclipmobile.presentation.ErrorUi
import com.stainedhead.paperclipmobile.presentation.SeverityUi
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Components that render a shared key: the words come from the key, the component decides nothing. */
@RunWith(AndroidJUnit4::class)
class KeyedComponentsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun severityIsShownAsAWordInCapitals() {
        rule.setContent { PaperclipTheme(darkTheme = false) { SeverityLabel(SeverityUi.Critical) } }

        rule.onNodeWithText("CRITICAL").assertIsDisplayed()
        rule.onNodeWithContentDescription("Critical").assertIsDisplayed()
    }

    @Test
    fun aDisabledReasonSaysWhyInWords() {
        rule.setContent { PaperclipTheme(darkTheme = false) { DisabledReason(ActionsDisabledReason.ViewerRole) } }

        rule.onNodeWithText("Your role is Viewer, so you can look but not change anything.").assertIsDisplayed()
    }

    @Test
    fun anErrorBannerShowsTheMessageOfItsKey() {
        rule.setContent { PaperclipTheme(darkTheme = false) { ErrorBanner(ErrorUi(ErrorKey.Unreachable)) } }

        rule.onNodeWithText("Can't reach the factory. Check your connection or VPN.").assertIsDisplayed()
    }

    @Test
    fun aRejectionAddsTheServersOwnReason() {
        rule.setContent {
            PaperclipTheme(darkTheme = false) { ErrorBanner(ErrorUi(ErrorKey.Rejected, "Budget must be higher.")) }
        }

        rule.onNodeWithText("The factory rejected the request. Budget must be higher.").assertIsDisplayed()
    }

    @Test
    fun signedOutOffersSignInAgainNotRetry() {
        var signIns = 0
        var retries = 0
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                ErrorBanner(ErrorUi(ErrorKey.SignedOut), onRetry = { retries++ }, onSignInAgain = { signIns++ })
            }
        }

        rule.onNodeWithText("Sign in again").performClick()

        signIns shouldBe 1
        retries shouldBe 0
        rule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun theBannerButtonIsChosenByTheRulesOfThePhone() {
        ErrorBannerButton.choose(signedOut = true, signIn = true, retry = true, dismiss = true) shouldBe
            ErrorBannerButton.SignIn
        ErrorBannerButton.choose(signedOut = false, signIn = true, retry = true, dismiss = true) shouldBe
            ErrorBannerButton.Retry
        ErrorBannerButton.choose(signedOut = false, signIn = false, retry = false, dismiss = true) shouldBe
            ErrorBannerButton.Dismiss
        ErrorBannerButton.choose(signedOut = false, signIn = false, retry = false, dismiss = false) shouldBe null
    }

    @Test
    fun anAttentionCardOpensFromItsTextBlockAndKeepsItsActionsSeparate() {
        var opens = 0
        var approvals = 0
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                AttentionCard(
                    severity = SeverityUi.High,
                    kind = "Approval",
                    age = "1h",
                    title = "Hire a QA engineer agent",
                    subtitle = "Requested by CEO",
                    combinedLabel = "Hire a QA engineer agent. Approval. 1 hour ago",
                    onOpen = { opens++ },
                ) { AppButton("Approve", { approvals++ }) }
            }
        }

        rule.onNodeWithTag("attention-card-open").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText("Approve").performClick()

        opens shouldBe 1
        approvals shouldBe 1
        rule.onNodeWithText("Hire a QA engineer agent").assertIsDisplayed()
        rule.onNodeWithText("HIGH").assertIsDisplayed()
        rule.onNodeWithText("· Approval").assertIsDisplayed()
        rule.onNodeWithText("1h").assertIsDisplayed()
    }

    @Test
    fun anAttentionCardWithoutOnOpenShowsTextOnly() {
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                AttentionCard(severity = null, kind = "Budget alert", age = "2h", title = "Engineer 2 hit its budget") {
                    Text("action slot")
                }
            }
        }

        rule.onNodeWithText("Engineer 2 hit its budget").assertIsDisplayed()
        rule.onNodeWithTag("attention-card-open").assertDoesNotExist()
    }
}
