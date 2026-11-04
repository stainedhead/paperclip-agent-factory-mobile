package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

private const val LARGE_TEXT = 2.0f

/**
 * Roborazzi baselines for the Work list and Issue PAP-1204 (T7.1 to T7.3) in light and dark and at font scale 2.0, on
 * the demo graph: the nine open issues, and Engineer 1's two questions in the thread with the plan, runs and files one
 * tab away. Nothing is captured while a spinner runs. Dates and times are read in UTC and against the demo clock.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1000dp-xhdpi")
class WorkScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()
    private val hostZone = TimeZone.getDefault()

    @Before
    fun pinZone() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @After
    fun tearDown() {
        TimeZone.setDefault(hostZone)
        app.close()
    }

    private fun capture(
        name: String,
        darkTheme: Boolean,
        fontScale: Float? = null,
        ready: () -> Boolean,
        content: @Composable () -> Unit,
    ) {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent { app.ThemedAt(darkTheme, fontScale, content) }
        rule.waitUntil(WAIT_MILLIS, ready)
        rule.waitForIdle()
        val suffix =
            if (fontScale != null) {
                "large"
            } else if (darkTheme) {
                "dark"
            } else {
                "light"
            }
        rule.onRoot().captureRoboImage("src/test/screenshots/$name-$suffix.png")
    }

    private fun work(
        darkTheme: Boolean,
        fontScale: Float? = null,
    ) = capture(
        "work",
        darkTheme,
        fontScale,
        { rule.hasTag("work-row-${DemoId.ISSUE_RETRIES}") && rule.hasTag("work-row-pap-1210") },
    ) { WorkListScreen(app.session, app.navigator) }

    @Test
    fun workLight() = work(darkTheme = false)

    @Test
    fun workDark() = work(darkTheme = true)

    @Test
    fun workLargeText() = work(darkTheme = false, fontScale = LARGE_TEXT)

    private fun issue(
        darkTheme: Boolean,
        fontScale: Float? = null,
    ) = capture(
        "issue",
        darkTheme,
        fontScale,
        {
            rule.hasTag("interaction-int-1204-questions-primary") && rule.hasTag("issue-comment-c-1204-2") &&
                rule.hasTag("issue-comment-send")
        },
    ) { IssueScreen(app.session, AppRoute.Issue(DemoId.ISSUE_RETRIES), app.navigator) }

    @Test
    fun issueLight() = issue(darkTheme = false)

    @Test
    fun issueDark() = issue(darkTheme = true)

    @Test
    fun issueLargeText() = issue(darkTheme = false, fontScale = LARGE_TEXT)
}
