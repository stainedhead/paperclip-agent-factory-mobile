package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val LARGEST = 2.0f
private val MINIMUM_TARGET = 48.dp

/**
 * The operations family at the largest font scale the system offers: the named primary actions are displayed (not
 * clipped) and at least 48 dp tall. The baselines of the same screens are in `OperationsScreenshotTest`.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class OperationsLargeTextTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun ComposeTestRule.assertTarget(tag: String) {
        onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        onNodeWithTag(tag).assertHeightIsAtLeast(MINIMUM_TARGET)
    }

    private fun showAtLargest(
        ready: () -> Boolean,
        content: @Composable () -> Unit,
    ) {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent { app.ThemedAt(false, LARGEST, content) }
        rule.waitUntil(WAIT_MILLIS, ready)
        rule.waitForIdle()
    }

    @Test
    fun theFactoryHomeTilesAndRunsFitAndAreAtLeast48dpTall() {
        showAtLargest({ rule.hasTag("factory-run-run-cto-live") }) { FactoryHomeScreen(app.session, app.navigator) }

        for (tile in listOf("agents", "work", "approvals", "budget")) rule.assertTarget("factory-tile-$tile")
        rule.assertTarget("factory-run-run-cto-live")
    }
}
