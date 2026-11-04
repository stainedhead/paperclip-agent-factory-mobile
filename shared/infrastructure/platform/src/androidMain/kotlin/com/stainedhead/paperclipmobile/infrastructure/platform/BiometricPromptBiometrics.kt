package com.stainedhead.paperclipmobile.infrastructure.platform

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.Biometrics
import javax.crypto.Cipher

/**
 * [Biometrics] on `androidx.biometric` (AND-002). The policy is device-owner authentication: strong biometrics with the
 * device credential as the fallback. A phone with neither, an app that is not in the foreground, and a security update
 * that is still pending are all [BiometricResult.Unavailable], so destructive actions stay blocked (fail closed,
 * INTENT principle 1). Every call shows a new prompt, so an approval is never reused. An error code nobody knows is
 * [BiometricResult.Failed].
 */
class BiometricPromptBiometrics internal constructor(
    internal val launcher: PromptLauncher,
    private val reasons: ReasonTexts,
) : Biometrics {
    /** Prompts in the activity [host] reports, with the sentence [reasons] gives for each reason. */
    constructor(host: AuthHost, reasons: ReasonTexts) : this(ActivityPromptLauncher(host), reasons)

    override suspend fun authenticate(reason: AuthReason): BiometricResult =
        authenticateWithText(reasons.textFor(reason))

    /**
     * The same prompt and result mapping with a sentence the caller chooses. The Keystore secret store asks for it
     * when the key it needs demands the device owner (the text says which secret is being read), so every prompt of the
     * app goes through this one class and fails closed the same way.
     */
    suspend fun authenticateWithText(text: String): BiometricResult =
        when (val outcome = launcher.prompt(text)) {
            PromptOutcome.Succeeded -> BiometricResult.Approved

            PromptOutcome.Unavailable -> BiometricResult.Unavailable

            is PromptOutcome.Authorized -> BiometricResult.Failed

            // a plain prompt is never bound to a cipher
            is PromptOutcome.Error -> mapPromptError(outcome.code)
        }

    /**
     * The prompt for one use of a Keystore key (R-001). The authentication is bound to [cipher] by the system, so an
     * approval made earlier, or for anything else, can not stand in for it. Answers the authorised cipher, or `null`
     * when the person did not approve (cancelled, locked out, no prompt to show, or an error): fail closed.
     */
    suspend fun authenticateWithCipher(
        text: String,
        cipher: Cipher,
    ): Cipher? = (launcher.promptWithCipher(text, cipher) as? PromptOutcome.Authorized)?.cipher
}

/** Maps a `BiometricPrompt.ERROR_*` code to the port's result. Unknown codes fail closed. */
internal fun mapPromptError(code: Int): BiometricResult =
    when (code) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        BiometricPrompt.ERROR_CANCELED,
        -> {
            BiometricResult.Cancelled
        }

        BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
            BiometricResult.LockedOut
        }

        BiometricPrompt.ERROR_HW_UNAVAILABLE,
        BiometricPrompt.ERROR_HW_NOT_PRESENT,
        BiometricPrompt.ERROR_NO_BIOMETRICS,
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
        BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
        -> {
            BiometricResult.Unavailable
        }

        else -> {
            BiometricResult.Failed
        } // including unable to process, timeout, no space and vendor errors
    }

internal const val ANDROID_11 = 30

/**
 * The allowed authenticators. Strong biometrics combined with the device credential needs Android 11; on Android 9 and
 * 10 the library only accepts weak biometrics with the device credential.
 */
internal fun authenticatorsFor(sdkInt: Int): Int =
    if (sdkInt >= ANDROID_11) {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
