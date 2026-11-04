package com.stainedhead.paperclipmobile.android.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import com.stainedhead.paperclipmobile.android.env.AppEnvironment
import com.stainedhead.paperclipmobile.android.env.GraphMode
import com.stainedhead.paperclipmobile.android.env.ScriptedProvider
import com.stainedhead.paperclipmobile.android.env.firstRunOpen
import com.stainedhead.paperclipmobile.android.screens.FirstRunScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AND-019: what the person did survives a recreation of the activity or of the process, and what must not survive does
 * not. The restoration tester saves the composition's saved state and builds the composition again, which is what both
 * a configuration change and a process death do to saveable state.
 */
@RunWith(AndroidJUnit4::class)
class RestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var environment: AppEnvironment? = null

    @After
    fun tearDown() {
        environment?.shutdown()
        scope.cancel()
    }

    @Test
    fun theSelectedTabAndEachTabsPushedScreenSurviveRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            PaperclipTheme(darkTheme = false) {
                TabShell(
                    inboxBadge = 0,
                    tabRoot = { tab, navigator ->
                        Column {
                            Text("root ${tab.name}")
                            AppButton("push settings", { navigator.push(AppRoute.Settings) })
                        }
                    },
                    destination = { route, _ -> Text("screen $route") },
                )
            }
        }
        rule.onNodeWithText("push settings").performClick()
        rule.onNodeWithTag("tab-Work").performClick()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("tab-Work").assertIsSelected()
        rule.onNodeWithText("root Work").assertIsDisplayed()
        rule.onNodeWithTag("tab-Inbox").performClick()
        rule.onNodeWithText("screen Settings", substring = true).assertIsDisplayed()
    }

    @Test
    fun theTypedAddressSurvivesRecreationButThePairingStepStartsOver() {
        val provider = ScriptedProvider(GraphMode.Demo, listOf(::firstRunOpen))
        val env = AppEnvironment(provider, scope, demoEntryAvailable = true).also { environment = it }
        val session = env.session.value
        val tester = StateRestorationTester(rule)
        tester.setContent { PaperclipTheme(darkTheme = false) { FirstRunScreen(session, onTryDemo = null) } }
        rule.onNodeWithTag("address-field").performTextInput("https://paperclip.example.com")

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("https://paperclip.example.com").assertIsDisplayed()
        rule.onNodeWithTag("pair-button").assertDoesNotExist()
    }
}
