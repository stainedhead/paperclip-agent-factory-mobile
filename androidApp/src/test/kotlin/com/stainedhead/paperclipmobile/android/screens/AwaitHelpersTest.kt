package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SHORT_WAIT_MILLIS = 300L
private const val LATER_NANOS = 100_000_000L

/**
 * The wait helpers are what keeps a screen test from racing its state holder, so each one must return as soon as its
 * condition holds and must still fail, after its timeout, when the condition never becomes true.
 */
@RunWith(AndroidJUnit4::class)
class AwaitHelpersTest {
    @get:Rule
    val rule = createComposeRule()

    private fun showGreeting() =
        rule.setContent {
            Column {
                Text("Hello", Modifier.testTag("greeting"))
            }
        }

    private fun failureOf(block: () -> Unit): Throwable = runCatching(block).exceptionOrNull().shouldBeInstanceOf()

    @Test
    fun theHelpersReturnAtOnceWhenTheConditionAlreadyHolds() {
        showGreeting()

        rule.awaitDisplayed("greeting", SHORT_WAIT_MILLIS)
        rule.awaitText("greeting", "Hello", SHORT_WAIT_MILLIS)
        rule.awaitTextDisplayed("Hello", SHORT_WAIT_MILLIS)
        rule.awaitGone("absent", SHORT_WAIT_MILLIS)
        rule.awaitCount("greeting", 1, SHORT_WAIT_MILLIS)
        awaitThat("always true", SHORT_WAIT_MILLIS) { true }
    }

    @Test
    fun aNodeThatNeverAppearsMakesAwaitDisplayedFail() {
        showGreeting()

        failureOf { rule.awaitDisplayed("absent", SHORT_WAIT_MILLIS) }
    }

    @Test
    fun textThatNeverMatchesMakesAwaitTextAndAwaitTextDisplayedFail() {
        showGreeting()

        failureOf { rule.awaitText("greeting", "Goodbye", SHORT_WAIT_MILLIS) }
        failureOf { rule.awaitTextDisplayed("Goodbye", SHORT_WAIT_MILLIS) }
    }

    @Test
    fun aNodeThatNeverLeavesMakesAwaitGoneFail() {
        showGreeting()

        failureOf { rule.awaitGone("greeting", SHORT_WAIT_MILLIS) }
    }

    @Test
    fun aCountThatNeverMatchesMakesAwaitCountFail() {
        showGreeting()

        failureOf { rule.awaitCount("greeting", 2, SHORT_WAIT_MILLIS) }
    }

    @Test
    fun aNodeThatIsNeverEnabledMakesAwaitEnabledAndAwaitDisabledFail() {
        showGreeting()

        failureOf { rule.awaitEnabled("absent", SHORT_WAIT_MILLIS) }
        failureOf { rule.awaitDisabled("absent", SHORT_WAIT_MILLIS) }
    }

    @Test
    fun aConditionThatNeverHoldsMakesAwaitThatFailAfterItsTimeoutAndNameWhatItWaitedFor() {
        val failure = failureOf { awaitThat("the holder to answer", SHORT_WAIT_MILLIS) { false } }

        (failure.message ?: "").contains("the holder to answer") shouldBe true
    }

    @Test
    fun aConditionThatBecomesTrueLaterIsAwaited() {
        val started = System.nanoTime()

        awaitThat("a later flip", WAIT_MILLIS) { System.nanoTime() - started > LATER_NANOS }
    }
}
