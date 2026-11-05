package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import com.stainedhead.paperclipmobile.presentation.RunViewerIntent
import com.stainedhead.paperclipmobile.presentation.RunViewerState
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** R-005: a touch that interrupts the live tail must not end the following; the next appended line is followed. */
@RunWith(AndroidJUnit4::class)
class RunLogFollowTest {
    @get:Rule
    val rule = createComposeRule()

    private var state by mutableStateOf(RunViewerState(logText = lines(0, INITIAL_LINES), autoScroll = true))

    private fun lines(
        from: Int,
        to: Int,
    ) = (from until to).joinToString("\n") { "log line $it" }

    private fun range() =
        rule
            .onNodeWithTag("run-log-panel")
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]

    private fun show() {
        rule.setContent {
            PaperclipTheme(darkTheme = false) {
                LogTail(state) { intent ->
                    if (intent is RunViewerIntent.UserScrolled) state = state.copy(autoScroll = intent.atBottom)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun append(from: Int) {
        state = state.copy(logText = state.logText + "\n" + lines(from, from + APPENDED_LINES))
        rule.waitForIdle()
    }

    @Test
    fun theTailFollowsAgainAfterATouchInterruptedIt() {
        show()
        range().value() shouldBe range().maxValue()

        rule.onNodeWithTag("run-log-panel").performTouchInput {
            down(center)
            moveBy(Offset(0f, -DRAG_PIXELS))
        }
        val whileHeld = range().value()
        append(INITIAL_LINES)
        range().value() shouldBe whileHeld
        rule.onNodeWithTag("run-log-panel").performTouchInput { up() }
        rule.waitForIdle()
        append(INITIAL_LINES + APPENDED_LINES)

        range().value() shouldBe range().maxValue()
    }

    private companion object {
        const val INITIAL_LINES = 80
        const val APPENDED_LINES = 20
        const val DRAG_PIXELS = 60f
    }
}
