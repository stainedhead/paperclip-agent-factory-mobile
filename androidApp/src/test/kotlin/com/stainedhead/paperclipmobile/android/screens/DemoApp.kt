package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.LocalReferenceMillis
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import com.stainedhead.paperclipmobile.android.env.AppEnvironment
import com.stainedhead.paperclipmobile.android.env.GraphMode
import com.stainedhead.paperclipmobile.android.env.GraphSession
import com.stainedhead.paperclipmobile.android.env.ScriptedProvider
import com.stainedhead.paperclipmobile.android.env.demoOpen
import com.stainedhead.paperclipmobile.android.env.firstRunOpen
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.android.shell.TabNavigator
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.umbrella.AndroidGraphOpen
import com.stainedhead.paperclipmobile.umbrella.DemoControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

const val WAIT_MILLIS = 10_000L

/** The ids of the demo data set (the same constants the shared demo graph is seeded with). */
object DemoId {
    const val HOME = "demo-home"
    const val CLOUD = "demo-cloud"
    const val TEST = "demo-test"
    const val ACME = "company-acme"
    const val SIDE_PROJECT = "company-side"
}

/** One demo graph and its session for a screen test; [close] releases everything the screen test owned. */
class DemoApp(
    open: () -> AndroidGraphOpen = ::demoOpen,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val environment = AppEnvironment(ScriptedProvider(GraphMode.Demo, listOf(open)), scope, demoEntryAvailable = true)
    val session: GraphSession get() = environment.session.value
    val controls: DemoControls get() = checkNotNull(session.graph?.demoControls)
    val appState get() = checkNotNull(session.app).state.value

    /** The navigation a screen is given: everything pushed lands in [pushed], pops are counted. */
    val pushed = mutableListOf<AppRoute>()
    var pops = 0
    var popsToRoot = 0
    val navigator = TabNavigator(push = { pushed += it }, pop = { pops++ }, popToRoot = { popsToRoot++ })

    fun scriptBiometrics(result: BiometricResult) = controls.scriptBiometrics(result)

    @Composable
    fun Themed(
        darkTheme: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        val now = checkNotNull(session.graph).nowEpochMillis
        PaperclipTheme(darkTheme) {
            // The root screen paints the page; a screen shown on its own gets the same background here.
            Box(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
                CompositionLocalProvider(LocalReferenceMillis provides now, content)
            }
        }
    }

    override fun close() {
        environment.shutdown()
        scope.cancel()
    }
}

fun firstRunApp() = DemoApp(::firstRunOpen)

fun ComposeTestRule.hasTag(tag: String) = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

fun ComposeTestRule.hasText(
    text: String,
    substring: Boolean = false,
) = onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

/** True when the node with [tag] says it is selected (a tab, a chip, a radio). */
fun ComposeTestRule.isSelected(tag: String) =
    onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.config?.let {
        SemanticsProperties.Selected in it && it[SemanticsProperties.Selected]
    } == true
