package com.stainedhead.paperclipmobile.android.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.R
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** AND-015: components render what they are given, never smaller than a 48 dp target, and know no rules. */
@RunWith(AndroidJUnit4::class)
class ComponentsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun everyButtonKindIsAtLeast48DpTallEnabledOrNot() {
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                Column {
                    for (kind in ButtonKind.entries) {
                        AppButton(kind.name, {}, Modifier.testTag(kind.name), kind = kind)
                        AppButton(kind.name, {}, Modifier.testTag("${kind.name}-off"), kind = kind, enabled = false)
                    }
                }
            }
        }

        for (kind in ButtonKind.entries) {
            rule.onNodeWithTag(kind.name).assertHeightIsAtLeast(48.dp).assertIsEnabled()
            rule.onNodeWithTag("${kind.name}-off").assertHeightIsAtLeast(48.dp).assertIsNotEnabled()
        }
    }

    @Test
    fun aButtonSendsItsClickOnlyWhileEnabled() {
        var clicks = 0
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                Column {
                    AppButton("On", { clicks++ })
                    AppButton("Off", { clicks += 100 }, enabled = false)
                }
            }
        }

        rule.onNodeWithText("On").performClick()
        rule.onNodeWithText("Off").performClick()

        clicks shouldBe 1
    }

    @Test
    fun aStatusPillShowsItsWordsAsOneElement() {
        rule.setContent { PaperclipTheme(darkTheme = true) { StatusPill("Running", StatusTone.Success) } }

        rule.onNodeWithText("Running").assertIsDisplayed()
    }

    @Test
    fun aFieldShowsTheIssueInsteadOfTheHelpAndDropsNewlines() {
        var typed = ""
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                FormField("Address", typed, { typed = it }, "address", help = "Help text", issue = "Bad address")
            }
        }

        rule.onNodeWithText("Bad address").assertIsDisplayed()
        rule.onNodeWithText("Help text").assertDoesNotExist()
        rule.onNodeWithTag("address").performTextInput("a\nb")
        typed shouldBe "ab"
    }

    @Test
    fun aMultiLineFieldKeepsTheNewlinesItIsGiven() {
        var typed = ""
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                FormField("Note", typed, { typed = it }, "note", maxLines = 4)
            }
        }

        rule.onNodeWithTag("note").performTextInput("a\nb")

        typed shouldBe "a\nb"
    }

    @Test
    fun anEmptyStateOffersItsActionOnlyWhenGiven() {
        var taps = 0
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                EmptyState(
                    painterResource(
                        R.drawable.ic_error,
                    ),
                    "Nothing here",
                    message = "Try later",
                    actionTitle = "Retry",
                    onAction = {
                        taps++
                    },
                )
            }
        }

        rule.onNodeWithText("Nothing here").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()

        taps shouldBe 1
    }

    @Test
    fun theCurrentStepIsSelectedAndSpoken() {
        val steps = listOf(StepItem(1, "Address", "Step 1, Address"), StepItem(2, "Pair", "Step 2, Pair, current"))
        rule.setContent { PaperclipTheme(darkTheme = false) { StepIndicator(steps, current = 2) } }

        rule.onNodeWithContentDescription("Step 2, Pair, current").assertIsSelected()
    }

    @Test
    fun theUpdatedLabelMeasuresTheServerTimestampAgainstThePinnedClock() {
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                CompositionLocalProvider(LocalReferenceMillis provides 1_012_000L) { UpdatedLabel(1_000_000L) }
            }
        }

        rule.onNodeWithText("Updated 12s ago").assertIsDisplayed()
        rule.onNodeWithContentDescription("Updated 12 seconds ago").assertIsDisplayed()
    }

    @Test
    fun noTimestampMeansNoLabel() {
        rule.setContent { PaperclipTheme(darkTheme = false) { UpdatedLabel(null) } }

        rule.onNodeWithTag("updated-label").assertDoesNotExist()
    }

    @Test
    fun aCardRowIsAtLeast48DpTall() {
        rule.setContent {
            PaperclipTheme(darkTheme = false) { CardGroup { CardRow("Title", Modifier.testTag("row"), help = "Help") } }
        }

        rule.onNodeWithTag("row").assertHeightIsAtLeast(48.dp)
    }
}
