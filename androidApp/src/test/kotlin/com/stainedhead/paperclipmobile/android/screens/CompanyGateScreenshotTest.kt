package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.captureBaseline
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import com.stainedhead.paperclipmobile.presentation.CompanyGate
import com.stainedhead.paperclipmobile.presentation.ErrorKey
import com.stainedhead.paperclipmobile.presentation.ErrorUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi baselines for the states of a factory without a usable company (F-1, ADR-150), in the shared design
 * system on fixed state, so no demo data or address appears. `recordRoborazziDebug` writes them under
 * `src/test/screenshots`, `verifyRoborazziDebug` compares against them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class CompanyGateScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun capture(
        name: String,
        darkTheme: Boolean,
        gate: CompanyGate,
        error: ErrorUi? = null,
    ) {
        rule.setContent {
            PaperclipTheme(darkTheme = darkTheme) {
                CompanyGateContent(gate, isChecking = false, error = error, onRecheck = {}, onOpenFactories = {})
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureBaseline("src/test/screenshots/$name-${if (darkTheme) "dark" else "light"}.png")
    }

    @Test
    fun noCompanyLight() = capture("company-gate-none", darkTheme = false, gate = CompanyGate.NoCompany)

    @Test
    fun noCompanyDark() = capture("company-gate-none", darkTheme = true, gate = CompanyGate.NoCompany)

    @Test
    fun chooseCompanyLight() = capture("company-gate-choose", darkTheme = false, gate = CompanyGate.ChooseCompany)

    @Test
    fun failedLight() =
        capture("company-gate-failed", darkTheme = false, gate = CompanyGate.Failed, error = ErrorUi(ErrorKey.Timeout))
}
