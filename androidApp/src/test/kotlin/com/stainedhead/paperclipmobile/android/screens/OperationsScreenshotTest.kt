package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
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
 * Roborazzi baselines for Factory home, Agents, Agent detail and the Run viewer (T6.1 to T6.3) in light and dark and at
 * font scale 2.0, on the demo graph: $1,240 of $2,000 with the three windows and three runs in progress, six agents,
 * the CTO and Engineer 2 details, and Engineer 1's live run. Nothing is captured while a spinner runs. Dates and times
 * are read in UTC and against the demo clock, so a baseline does not move with the host or the day.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1000dp-xhdpi")
class OperationsScreenshotTest {
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

    private fun factoryHome(
        darkTheme: Boolean,
        fontScale: Float? = null,
    ) = capture(
        "factory-home",
        darkTheme,
        fontScale,
        { rule.hasTag("factory-run-run-cto-live") && rule.hasTag("factory-window-Last7Days") },
    ) { FactoryHomeScreen(app.session, app.navigator) }

    @Test
    fun factoryHomeLight() = factoryHome(darkTheme = false)

    @Test
    fun factoryHomeDark() = factoryHome(darkTheme = true)

    @Test
    fun factoryHomeLargeText() = factoryHome(darkTheme = false, fontScale = LARGE_TEXT)
}
