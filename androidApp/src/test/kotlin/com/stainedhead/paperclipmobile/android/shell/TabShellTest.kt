package com.stainedhead.paperclipmobile.android.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
}
