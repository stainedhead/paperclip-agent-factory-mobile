package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LARGE_TEXT = 2.0f

/**
 * Roborazzi baselines for the attention family (T5.1 to T5.4) in light and dark, and at font scale 2.0 (PRD acceptance
 * criterion 8), on the demo graph: nine Acme Labs cards with the badge at 3, the billing decision with three options,
 * the QA hire approval and the Engineer 2 budget incident. Nothing is captured while a spinner runs. The pairing step
 * is never captured. `recordRoborazziDebug` writes them under `src/test/screenshots`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1000dp-xhdpi")
class AttentionScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun capture(
        name: String,
        darkTheme: Boolean,
        fontScale: Float? = null,
        ready: () -> Boolean,
        content: @Composable () -> Unit,
    ) {
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

    private fun inbox(
        darkTheme: Boolean,
        fontScale: Float? = null,
    ) = capture("inbox", darkTheme, fontScale, { rule.cardCount() > 0 }) { InboxScreen(app.session, app.navigator) }

    private fun inboxOffline(darkTheme: Boolean) {
        app.controls.setOnline(false)
        capture("inbox-offline", darkTheme, ready = { rule.hasTag("offline-banner") && rule.cardCount() > 0 }) {
            InboxScreen(app.session, app.navigator)
        }
    }

    @Test
    fun inboxLight() = inbox(darkTheme = false)

    @Test
    fun inboxDark() = inbox(darkTheme = true)

    @Test
    fun inboxLargeText() = inbox(darkTheme = false, fontScale = LARGE_TEXT)

    @Test
    fun inboxOfflineLight() = inboxOffline(darkTheme = false)

    @Test
    fun inboxOfflineDark() = inboxOffline(darkTheme = true)
}
