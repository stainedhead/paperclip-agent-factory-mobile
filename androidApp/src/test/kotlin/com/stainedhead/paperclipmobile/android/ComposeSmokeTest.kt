package com.stainedhead.paperclipmobile.android

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A Compose UI test on the JVM: JUnit 4 and Robolectric, as every Compose and Roborazzi test in the app (ADR-121). */
@RunWith(AndroidJUnit4::class)
class ComposeSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a composable renders under Robolectric`() {
        composeRule.setContent { BasicText("Smoke") }

        composeRule.onNodeWithText("Smoke").assertIsDisplayed()
    }
}
