package com.stainedhead.paperclipmobile.infrastructure.platform

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.testing.contract.BiometricsContract
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** A launcher that answers with a script and remembers every prompt, standing in for the system prompt. */
internal class ScriptedPromptLauncher : PromptLauncher {
    var next: PromptOutcome = PromptOutcome.Succeeded
    val texts = mutableListOf<String>()

    override suspend fun prompt(text: String): PromptOutcome {
        texts += text
        return next
    }

    var nextCipher: PromptOutcome? = null
    val cipherTexts = mutableListOf<String>()

    override suspend fun promptWithCipher(
        text: String,
        cipher: javax.crypto.Cipher,
    ): PromptOutcome {
        cipherTexts += text
        return nextCipher ?: PromptOutcome.Authorized(cipher)
    }
}

class BiometricPromptBiometricsContractTest : BiometricsContract() {
    override fun create(): Biometrics = BiometricPromptBiometrics(ScriptedPromptLauncher(), ReasonTexts.default)

    override fun willAnswer(
        biometrics: Biometrics,
        result: BiometricResult,
    ) {
        val launcher = (biometrics as BiometricPromptBiometrics).launcher as ScriptedPromptLauncher
        launcher.next =
            when (result) {
                BiometricResult.Approved -> PromptOutcome.Succeeded
                BiometricResult.Cancelled -> PromptOutcome.Error(BiometricPrompt.ERROR_USER_CANCELED)
                BiometricResult.Failed -> PromptOutcome.Error(BiometricPrompt.ERROR_UNABLE_TO_PROCESS)
                BiometricResult.LockedOut -> PromptOutcome.Error(BiometricPrompt.ERROR_LOCKOUT)
                BiometricResult.Unavailable -> PromptOutcome.Unavailable
            }
    }

    @Test
    fun `AND-002 the BiometricPrompt adapter obeys the biometrics contract`() = runTest { runAll() }
}

class BiometricPromptBiometricsTest {
    private val launcher = ScriptedPromptLauncher()
    private val biometrics = BiometricPromptBiometrics(launcher, ReasonTexts("Unlock it", "Confirm it"))

    @Test
    fun `AND-002 every androidx error code maps to one result and none to approval`() {
        val expected =
            mapOf(
                BiometricPrompt.ERROR_USER_CANCELED to BiometricResult.Cancelled,
                BiometricPrompt.ERROR_NEGATIVE_BUTTON to BiometricResult.Cancelled,
                BiometricPrompt.ERROR_CANCELED to BiometricResult.Cancelled,
                BiometricPrompt.ERROR_LOCKOUT to BiometricResult.LockedOut,
                BiometricPrompt.ERROR_LOCKOUT_PERMANENT to BiometricResult.LockedOut,
                BiometricPrompt.ERROR_HW_UNAVAILABLE to BiometricResult.Unavailable,
                BiometricPrompt.ERROR_HW_NOT_PRESENT to BiometricResult.Unavailable,
                BiometricPrompt.ERROR_NO_BIOMETRICS to BiometricResult.Unavailable,
                BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL to BiometricResult.Unavailable,
                BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED to BiometricResult.Unavailable,
                BiometricPrompt.ERROR_UNABLE_TO_PROCESS to BiometricResult.Failed,
                BiometricPrompt.ERROR_TIMEOUT to BiometricResult.Failed,
                BiometricPrompt.ERROR_NO_SPACE to BiometricResult.Failed,
                BiometricPrompt.ERROR_VENDOR to BiometricResult.Failed,
            )
        expected.forEach { (code, result) -> mapPromptError(code) shouldBe result }
    }

    @Test
    fun `AND-002 an error code nobody knows fails closed`() {
        UNKNOWN_CODES.forEach { mapPromptError(it) shouldBe BiometricResult.Failed }
    }

    @Test
    fun `AND-002 the person sees a reason that matches what they are doing`() =
        runTest {
            biometrics.authenticate(AuthReason.UnlockApp)
            biometrics.authenticate(AuthReason.ConfirmDestructiveAction)
            launcher.texts shouldBe listOf("Unlock it", "Confirm it")
        }

    @Test
    fun `AND-001 the secret store can ask for a prompt with its own text and gets the same result mapping`() =
        runTest {
            biometrics.authenticateWithText("Unlock the board key") shouldBe BiometricResult.Approved
            launcher.next = PromptOutcome.Error(BiometricPrompt.ERROR_LOCKOUT)
            biometrics.authenticateWithText("Unlock the board key") shouldBe BiometricResult.LockedOut
            launcher.next = PromptOutcome.Unavailable
            biometrics.authenticateWithText("Unlock the board key") shouldBe BiometricResult.Unavailable
            launcher.texts shouldBe List(PROMPTS) { "Unlock the board key" }
        }

    @Test
    fun `R-001 a cipher prompt answers the authorised cipher and nothing else`() =
        runTest {
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            biometrics.authenticateWithCipher("Unlock the board key", cipher) shouldBe cipher
            launcher.cipherTexts shouldBe listOf("Unlock the board key")
        }

    @Test
    fun `R-001 a cipher prompt that is not approved answers no cipher`() =
        runTest {
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            for (refusal in listOf(
                PromptOutcome.Unavailable,
                PromptOutcome.Error(BiometricPrompt.ERROR_USER_CANCELED),
                PromptOutcome.Succeeded,
            )) {
                launcher.nextCipher = refusal
                biometrics.authenticateWithCipher("Unlock", cipher) shouldBe null
            }
        }

    @Test
    fun `AND-002 every prompt asks the launcher again so an approval is never remembered`() =
        runTest {
            repeat(PROMPTS) { biometrics.authenticate(AuthReason.ConfirmDestructiveAction) }
            launcher.texts.size shouldBe PROMPTS
        }

    @Test
    fun `AND-002 cancelling, lockout and a missing activity each end in their own result`() =
        runTest {
            launcher.next = PromptOutcome.Error(BiometricPrompt.ERROR_USER_CANCELED)
            biometrics.authenticate(AuthReason.ConfirmDestructiveAction) shouldBe BiometricResult.Cancelled
            launcher.next = PromptOutcome.Error(BiometricPrompt.ERROR_LOCKOUT)
            biometrics.authenticate(AuthReason.ConfirmDestructiveAction) shouldBe BiometricResult.LockedOut
            launcher.next = PromptOutcome.Unavailable
            biometrics.authenticate(AuthReason.ConfirmDestructiveAction) shouldBe BiometricResult.Unavailable
        }

    @Test
    fun `AND-002 with no resumed activity the prompt is never shown and the result is unavailable`() =
        runTest {
            val noActivity = ActivityPromptLauncher(host = { null }, availability = { error("must not be asked") })
            BiometricPromptBiometrics(
                noActivity,
                ReasonTexts.default,
            ).authenticate(AuthReason.ConfirmDestructiveAction) shouldBe
                BiometricResult.Unavailable
        }

    @Test
    fun `AND-002 the authenticators are strong biometrics or the device credential where the platform allows it`() {
        authenticatorsFor(sdkInt = ANDROID_11) shouldBe
            (BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        // Android 9 and 10 do not support strong biometrics combined with the device credential.
        authenticatorsFor(sdkInt = ANDROID_9) shouldBe
            (BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        authenticatorsFor(sdkInt = ANDROID_10) shouldBe authenticatorsFor(sdkInt = ANDROID_9)
    }

    private companion object {
        const val ANDROID_9 = 28
        const val ANDROID_10 = 29
        const val ANDROID_11 = 30
        const val PROMPTS = 3
        val UNKNOWN_CODES = listOf(0, 6, 99, -1, 12345)
    }
}
