package com.stainedhead.paperclipmobile.android.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** AND-012: five tabs, each with its own back stack kept across switches, and the badge as the server counted it. */
@RunWith(AndroidJUnit4::class)
class TabShellTest {
    @get:Rule
    val rule = createComposeRule()

    @Composable
    private fun Shell(badge: Int) {
        PaperclipTheme(darkTheme = false) {
            TabShell(
                inboxBadge = badge,
                tabRoot = { tab, nav ->
                    Column {
                        Text("root ${tab.name}")
                        AppButton("push settings", { nav.push(AppRoute.Settings) })
                        AppButton("push decision", { nav.push(AppRoute.Decision("dec-1")) })
                        AppButton("show agents", { nav.select(AppTab.Agents) })
                    }
                },
                destination = { route, _ -> Text("screen $route") },
            )
        }
    }

    @Test
    fun theInboxIsSelectedFirstAndAllFiveTabsAreThere() {
        rule.setContent { Shell(badge = 0) }

        rule.onNodeWithTag("tab-Inbox").assertIsSelected()
        for (tab in AppTab.entries) rule.onNodeWithTag("tab-${tab.name}").assertIsDisplayed()
        rule.onNodeWithText("root Inbox").assertIsDisplayed()
    }

    @Test
    fun theInboxBadgeIsTheServersCountAndSpoken() {
        rule.setContent { Shell(badge = 3) }

        rule.onNodeWithTag("tab-Inbox").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 waiting"),
        )
        rule.onNodeWithTag("tab-Work").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun everyTabLabelStaysOnOneLineAtTheLargestFontScale() {
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f),
            ) { Shell(badge = 3) }
        }

        for (title in listOf("Inbox", "Factory", "Work", "Agents", "Factories")) {
            val layouts = mutableListOf<TextLayoutResult>()
            val node = rule.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            layouts.single().lineCount shouldBe 1
        }
    }

    @Test
    fun noBadgeWhenNothingWaits() {
        rule.setContent { Shell(badge = 0) }

        rule.onNodeWithTag("tab-Inbox").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun eachTabKeepsItsOwnPushedScreenAcrossSwitches() {
        rule.setContent { Shell(badge = 0) }

        rule.onNodeWithText("push settings").performClick()
        rule.onNodeWithText("screen Settings", substring = true).assertIsDisplayed()

        rule.onNodeWithTag("tab-Work").performClick()
        rule.onNodeWithText("root Work").assertIsDisplayed()
        rule.onNodeWithText("push decision").performClick()
        rule.onNodeWithText("screen Decision", substring = true).assertIsDisplayed()

        rule.onNodeWithTag("tab-Inbox").performClick()
        rule.onNodeWithText("screen Settings", substring = true).assertIsDisplayed()

        rule.onNodeWithTag("tab-Work").performClick()
        rule.onNodeWithText("screen Decision", substring = true).assertIsDisplayed()
    }

    @Test
    fun selectingTheSelectedTabAgainReturnsToItsRoot() {
        rule.setContent { Shell(badge = 0) }

        rule.onNodeWithText("push settings").performClick()
        rule.onNodeWithText("screen Settings", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("tab-Inbox").performClick()

        rule.onNodeWithText("root Inbox").assertIsDisplayed()
    }

    @Test
    fun aScreenCanAskTheShellToShowAnotherTab() {
        rule.setContent { Shell(badge = 0) }

        rule.onNodeWithText("show agents").performClick()

        rule.onNodeWithTag("tab-Agents").assertIsSelected()
        rule.onNodeWithText("root Agents").assertIsDisplayed()
    }
}
