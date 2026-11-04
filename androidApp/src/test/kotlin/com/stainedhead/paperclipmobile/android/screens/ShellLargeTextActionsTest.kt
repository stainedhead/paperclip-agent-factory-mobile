package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.shell.LockActions
import com.stainedhead.paperclipmobile.android.shell.LockScreen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val LARGEST = 2.0f

/**
 * T8.1 for the app's own screens (the lock, first run, Factories, add and edit a factory): at font scale 2.0 on a
 * 360 dp screen the named primary actions are displayed, not clipped, and at least 48 dp tall. The attention family is
 * in `LargeTextActionsTest`, the operations screens in `OperationsLargeTextTest`, Work and Issue in
 * `WorkLargeTextTest`.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class ShellLargeTextActionsTest {
    @get:Rule
    val rule = createComposeRule()

    private var app: DemoApp? = null

    @After
    fun tearDown() {
        app?.close()
    }

    private fun ComposeTestRule.assertTarget(tag: String) {
        onNodeWithTag(tag).assertIsDisplayed()
        onNodeWithTag(tag).assertHeightIsAtLeast(48.dp)
    }

    private fun show(
        demo: DemoApp,
        ready: () -> Boolean,
        content: @Composable () -> Unit,
    ) {
        app = demo
        rule.setContent { demo.ThemedAt(false, LARGEST, content) }
        rule.waitUntil(WAIT_MILLIS, ready)
        rule.waitForIdle()
    }

    @Test
    fun theUnlockButtonFitsAndIsAtLeast48dpTall() {
        rule.setContent { DemoApp().ThemedAt(false, LARGEST) { LockScreen(LockActions(null) {}) } }
        rule.waitForIdle()

        rule.assertTarget("unlock-button")
    }

    @Test
    fun theFirstRunActionsFitAndAreAtLeast48dpTall() {
        val demo = firstRunApp()
        show(demo, { rule.hasTag("check-address-button") }) { FirstRunScreen(demo.session, onTryDemo = {}) }

        rule.assertTarget("check-address-button")
        rule.assertTarget("try-demo-button")
    }

    @Test
    fun theFactoriesAddButtonFitsAndIsAtLeast48dpTall() {
        val demo = DemoApp()
        show(demo, { rule.hasTag("add-factory-button") }) { FactoriesScreen(demo.session, demo.navigator) }

        rule.assertTarget("add-factory-button")
    }

    @Test
    fun theEditFactoryButtonsFitAndAreAtLeast48dpTall() {
        val demo = DemoApp()
        show(demo, { rule.hasTag("edit-save-button") }) { EditFactoryScreen(demo.session, DemoId.HOME, demo.navigator) }

        rule.assertTarget("edit-cancel-button")
        rule.assertTarget("edit-save-button")
    }
}
