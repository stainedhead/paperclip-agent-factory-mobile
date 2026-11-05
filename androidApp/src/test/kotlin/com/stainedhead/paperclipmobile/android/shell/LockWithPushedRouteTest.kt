package com.stainedhead.paperclipmobile.android.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import com.stainedhead.paperclipmobile.android.env.AppEnvironment
import com.stainedhead.paperclipmobile.android.env.GraphMode
import com.stainedhead.paperclipmobile.android.env.ScreenHolder
import com.stainedhead.paperclipmobile.android.env.ScriptedProvider
import com.stainedhead.paperclipmobile.android.env.demoOpen
import com.stainedhead.paperclipmobile.android.env.rememberHolderViewModel
import com.stainedhead.paperclipmobile.domain.IdlePeriod
import com.stainedhead.paperclipmobile.presentation.SettingsIntent
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val WAIT_MILLIS = 10_000L

/** R-021: locking the app with a detail screen pushed closes that screen's holder; unlocking lands on the tab root. */
@RunWith(AndroidJUnit4::class)
class LockWithPushedRouteTest {
    @get:Rule
    val rule = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var environment: AppEnvironment
    private var autoUnlock = true
    private val opened = mutableListOf<ScreenHolder<Int, Int>>()

    @After
    fun tearDown() {
        environment.shutdown()
        scope.cancel()
    }

    private fun show() {
        environment =
            AppEnvironment(ScriptedProvider(GraphMode.Demo, listOf(::demoOpen)), scope, demoEntryAvailable = true)
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                RootScreen(
                    environment = environment,
                    lock = { actions ->
                        Text("lock screen")
                        if (autoUnlock) LaunchedEffect(Unit) { actions.onUnlock() }
                    },
                    firstRun = { _, _ -> Text("first run screen") },
                    tabs = { _, badge ->
                        TabShell(
                            inboxBadge = badge,
                            tabRoot = { tab, navigator ->
                                Column {
                                    Text("root ${tab.name}")
                                    AppButton("push decision", { navigator.push(AppRoute.Decision("dec-1")) })
                                }
                            },
                            destination = { _, _ ->
                                val holder =
                                    rememberHolderViewModel("probe") {
                                        ScreenHolder<Int, Int>(MutableStateFlow(0), {}, {}).also { opened += it }
                                    }
                                Text("screen ${holder.state.value}")
                            },
                        )
                    },
                )
            }
        }
    }

    private fun lock() {
        val graph = checkNotNull(environment.session.value.graph)
        graph.createSettingsStateHolder().onIntent(SettingsIntent.IdlePeriodChosen(IdlePeriod.Immediately))
        rule.waitUntil(WAIT_MILLIS) {
            environment.setForeground(false)
            environment.setForeground(true)
            rule.hasText("lock screen")
        }
    }

    @Test
    fun lockingWithAPushedScreenClosesItsHolderAndUnlockLandsOnTheRoot() {
        show()
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("root Inbox") }
        rule.onNodeWithText("push decision").performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("screen 0") }
        opened.size shouldBe 1

        autoUnlock = false
        lock()

        rule.waitUntil(WAIT_MILLIS) { opened.single().isClosed }

        autoUnlock = true
        environment.setForeground(false)
        environment.setForeground(true)
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("root Inbox") }
        rule.hasText("screen 0") shouldBe false
    }
}

private fun ComposeTestRule.hasText(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
