package com.stainedhead.paperclipmobile.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import com.stainedhead.paperclipmobile.android.env.AppEnvironment
import com.stainedhead.paperclipmobile.android.env.GraphMode
import com.stainedhead.paperclipmobile.android.env.ScriptedProvider
import com.stainedhead.paperclipmobile.android.env.firstRunOpen
import com.stainedhead.paperclipmobile.android.screens.FirstRunScreen
import com.stainedhead.paperclipmobile.android.shell.AppTabs
import com.stainedhead.paperclipmobile.android.shell.LockActions
import com.stainedhead.paperclipmobile.android.shell.LockScreen
import com.stainedhead.paperclipmobile.presentation.DeviceAuthIssue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi baselines for the shell in light and dark (T3.9): the tab shell with the Inbox badge, the first-run
 * address step and the lock screen. The theme is a parameter, so the images do not depend on the host's night mode.
 * `recordRoborazziDebug` writes them under `src/test/screenshots`, `verifyRoborazziDebug` compares against them.
 * The pairing screen is never captured (AGENTS.md "Documentation Requirements").
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ShellScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var environment: AppEnvironment? = null

    @After
    fun tearDown() {
        environment?.shutdown()
        scope.cancel()
    }

    private fun capture(
        name: String,
        darkTheme: Boolean,
        content: @Composable () -> Unit,
    ) {
        rule.setContent { PaperclipTheme(darkTheme = darkTheme, content = content) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/$name-${if (darkTheme) "dark" else "light"}.png")
    }

    @Test
    fun shellLight() = capture("shell", darkTheme = false) { AppTabs(inboxBadge = BADGE) }

    @Test
    fun shellDark() = capture("shell", darkTheme = true) { AppTabs(inboxBadge = BADGE) }

    private fun firstRun(darkTheme: Boolean) {
        val env =
            AppEnvironment(ScriptedProvider(GraphMode.Demo, listOf(::firstRunOpen)), scope, demoEntryAvailable = true)
        environment = env
        val session = env.session.value
        capture("first-run", darkTheme) { FirstRunScreen(session, onTryDemo = {}) }
    }

    @Test
    fun firstRunLight() = firstRun(darkTheme = false)

    @Test
    fun firstRunDark() = firstRun(darkTheme = true)

    @Test
    fun lockLight() = capture("lock", darkTheme = false) { LockScreen(LockActions(DeviceAuthIssue.Cancelled) {}) }

    @Test
    fun lockDark() = capture("lock", darkTheme = true) { LockScreen(LockActions(DeviceAuthIssue.Cancelled) {}) }

    private companion object {
        const val BADGE = 3
    }
}
