package com.stainedhead.paperclipmobile.infrastructure.platform

import androidx.biometric.BiometricPrompt
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import javax.crypto.Cipher
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/** A screen the test drives: posts run at once, and the test decides what the "system prompt" says and when. */
internal class FakePromptScreen : PromptScreen {
    var authenticationPossible = true
    var showable = true
    var failToShow: RuntimeException? = null
    var postsRun = true
    val posted = mutableListOf<() -> Unit>()
    val shown = mutableListOf<Pair<String, Cipher?>>()
    var cancelled = 0
    private var onResult: ((PromptOutcome) -> Unit)? = null

    override fun canAuthenticate(forCipher: Boolean): Boolean = authenticationPossible

    override fun post(block: () -> Unit) {
        if (postsRun) block() else posted += block
    }

    override fun canShowNow(): Boolean = showable

    override fun show(
        text: String,
        cipher: Cipher?,
        onResult: (PromptOutcome) -> Unit,
    ): () -> Unit {
        failToShow?.let { throw it }
        shown += text to cipher
        this.onResult = onResult
        return { cancelled++ }
    }

    /** The system prompt answers. */
    fun answer(outcome: PromptOutcome) = checkNotNull(onResult) { "no prompt is showing" }(outcome)
}

class ActivityPromptLauncherTest {
    private val screen = FakePromptScreen()
    private val launcher = ActivityPromptLauncher { screen }
    private val cipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    @Test
    fun `R-011 a prompt that succeeds answers success`() =
        runTest {
            val outcome = async(start = CoroutineStart.UNDISPATCHED) { launcher.prompt("Unlock it") }
            screen.shown shouldBe listOf("Unlock it" to null)
            screen.answer(PromptOutcome.Succeeded)
            outcome.await() shouldBe PromptOutcome.Succeeded
        }

    @Test
    fun `R-011 a prompt for a cipher is shown for that cipher and answers the authorised one`() =
        runTest {
            val outcome = async(start = CoroutineStart.UNDISPATCHED) { launcher.promptWithCipher("Unlock it", cipher) }
            screen.shown shouldBe listOf("Unlock it" to cipher)
            screen.answer(PromptOutcome.Authorized(cipher))
            (outcome.await() as PromptOutcome.Authorized).cipher shouldBe cipher
        }

    @Test
    fun `R-011 every error code of the system prompt reaches the caller as it is`() =
        runTest {
            for (code in listOf(
                BiometricPrompt.ERROR_USER_CANCELED,
                BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                BiometricPrompt.ERROR_LOCKOUT,
                BiometricPrompt.ERROR_HW_UNAVAILABLE,
                BiometricPrompt.ERROR_CANCELED,
                BiometricPrompt.ERROR_TIMEOUT,
            )) {
                val outcome = async(start = CoroutineStart.UNDISPATCHED) { launcher.prompt("Unlock it") }
                screen.answer(PromptOutcome.Error(code))
                outcome.await() shouldBe PromptOutcome.Error(code)
            }
        }

    @Test
    fun `R-011 a second answer from the system prompt is ignored`() =
        runTest {
            val outcome = async(start = CoroutineStart.UNDISPATCHED) { launcher.prompt("Unlock it") }
            screen.answer(PromptOutcome.Succeeded)
            screen.answer(PromptOutcome.Error(BiometricPrompt.ERROR_CANCELED))
            outcome.await() shouldBe PromptOutcome.Succeeded
        }

    @Test
    fun `R-011 cancelling the caller cancels the system prompt`() =
        runTest {
            val outcome = async(start = CoroutineStart.UNDISPATCHED) { launcher.prompt("Unlock it") }
            screen.cancelled shouldBe 0
            outcome.cancel()
            screen.cancelled shouldBe 1
        }

    @Test
    fun `R-002 a prompt the activity can no longer show at post time answers unavailable and does not hang`() =
        runTest {
            screen.showable = false
            withTimeout(1.seconds) { launcher.prompt("Unlock it") } shouldBe PromptOutcome.Unavailable
            screen.shown shouldBe emptyList()
        }

    @Test
    fun `R-002 a system prompt that throws answers unavailable instead of crashing`() =
        runTest {
            for (failure in listOf(
                IllegalStateException("state saved"),
                IllegalArgumentException("no fragment manager"),
            )) {
                screen.failToShow = failure
                withTimeout(1.seconds) { launcher.prompt("Unlock it") } shouldBe PromptOutcome.Unavailable
            }
        }

    @Test
    fun `R-002 a caller cancelled before the prompt was posted never shows it`() =
        runTest {
            screen.postsRun = false
            val outcome = async(start = CoroutineStart.UNDISPATCHED) { launcher.prompt("Unlock it") }
            outcome.cancel()
            screen.posted.forEach { it() }
            screen.shown shouldBe emptyList()
        }

    @Test
    fun `R-011 a device that cannot authenticate never shows a prompt`() =
        runTest {
            screen.authenticationPossible = false
            launcher.prompt("Unlock it") shouldBe PromptOutcome.Unavailable
            launcher.promptWithCipher("Unlock it", cipher) shouldBe PromptOutcome.Unavailable
            screen.shown shouldBe emptyList()
        }

    @Test
    fun `R-011 with no screen the app is in the background and the answer is unavailable`() =
        runTest {
            ActivityPromptLauncher { null }.prompt("Unlock it") shouldBe PromptOutcome.Unavailable
        }
}
