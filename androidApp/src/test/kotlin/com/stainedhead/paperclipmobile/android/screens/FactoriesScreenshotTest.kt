package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.captureBaseline
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi baselines for the Factories family (T4.1 to T4.5) in light and dark, on the demo graph: the demo values of
 * the mocks (Home factory with 23 days left, Cloud factory with an expired key, Test factory signed out). The
 * pairing step is never captured (AGENTS.md "Documentation Requirements"), so no baseline shows a waiting re-pair.
 * `recordRoborazziDebug` writes them under `src/test/screenshots`, `verifyRoborazziDebug` compares against them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1000dp-xhdpi")
class FactoriesScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun capture(
        name: String,
        darkTheme: Boolean,
        ready: () -> Boolean,
        content: @Composable () -> Unit,
    ) {
        rule.setContent { app.Themed(darkTheme, content) }
        rule.waitUntil(WAIT_MILLIS, ready)
        rule.waitForIdle()
        rule.onRoot().captureBaseline("src/test/screenshots/$name-${if (darkTheme) "dark" else "light"}.png")
    }

    private fun factories(darkTheme: Boolean) =
        capture(
            "factories",
            darkTheme,
            { rule.hasTag("company-chip-demo-home-company-acme") },
        ) { FactoriesScreen(app.session, app.navigator) }

    private fun manage(darkTheme: Boolean) =
        capture("manage-factories", darkTheme, { rule.hasText("Home factory") }) {
            ManageFactoriesScreen(app.session, app.navigator)
        }

    private fun edit(darkTheme: Boolean) =
        capture(
            "edit-factory",
            darkTheme,
            { rule.hasTag("edit-access-company-company-acme") },
        ) { EditFactoryScreen(app.session, DemoId.HOME, app.navigator) }

    private fun add(darkTheme: Boolean) =
        capture("add-factory", darkTheme, { rule.hasTag("edit-name") }) {
            EditFactoryScreen(app.session, null, app.navigator)
        }

    private fun settings(darkTheme: Boolean) =
        capture("settings", darkTheme, { rule.hasText("Alex's iPhone") }) { SettingsScreen(app.session, app.navigator) }

    private fun devices(darkTheme: Boolean) =
        capture("paired-devices", darkTheme, { rule.hasTag("device-key-bk-demo-a91f") }) {
            PairedDevicesScreen(app.session, app.navigator)
        }

    private fun diagnostics(darkTheme: Boolean) =
        capture("diagnostics", darkTheme, { rule.hasTag("diagnostics") }) {
            DiagnosticsScreen(app.session, app.navigator)
        }

    private fun factoriesOffline(darkTheme: Boolean) {
        app.controls.setOnline(false)
        capture("factories-offline", darkTheme, { rule.hasTag("offline-banner") }) {
            FactoriesScreen(app.session, app.navigator)
        }
    }

    @Test
    fun factoriesLight() = factories(darkTheme = false)

    @Test
    fun factoriesDark() = factories(darkTheme = true)

    @Test
    fun factoriesOfflineLight() = factoriesOffline(darkTheme = false)

    @Test
    fun factoriesOfflineDark() = factoriesOffline(darkTheme = true)

    @Test
    fun manageLight() = manage(darkTheme = false)

    @Test
    fun manageDark() = manage(darkTheme = true)

    @Test
    fun editLight() = edit(darkTheme = false)

    @Test
    fun editDark() = edit(darkTheme = true)

    @Test
    fun addLight() = add(darkTheme = false)

    @Test
    fun addDark() = add(darkTheme = true)

    @Test
    fun settingsLight() = settings(darkTheme = false)

    @Test
    fun settingsDark() = settings(darkTheme = true)

    @Test
    fun devicesLight() = devices(darkTheme = false)

    @Test
    fun devicesDark() = devices(darkTheme = true)

    @Test
    fun diagnosticsLight() = diagnostics(darkTheme = false)

    @Test
    fun diagnosticsDark() = diagnostics(darkTheme = true)
}
