package com.stainedhead.paperclipmobile.android

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi round trip: `recordRoborazziDebug` writes `src/test/screenshots/smoke.png`, `verifyRoborazziDebug`
 * compares against it and fails when the image is missing or different (ADR-121).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoborazziSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a composable matches its recorded screenshot`() {
        composeRule.setContent { BasicText("Smoke") }

        composeRule.onRoot().captureRoboImage("src/test/screenshots/smoke.png")
    }
}
