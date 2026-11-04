package com.stainedhead.paperclipmobile.infrastructure.platform

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume

/** What the system prompt said, or that there was no prompt to show. */
internal sealed interface PromptOutcome {
    data object Succeeded : PromptOutcome

    /** The person authorised [cipher], the very cipher the prompt was shown for; it is good for one operation. */
    class Authorized(
        val cipher: Cipher,
    ) : PromptOutcome

    /** No resumed activity, or the device cannot authenticate at all (no hardware, nothing enrolled, no credential). */
    data object Unavailable : PromptOutcome

    data class Error(
        val code: Int,
    ) : PromptOutcome
}

/** One system prompt for device-owner authentication. A fake answers it in tests. */
internal interface PromptLauncher {
    suspend fun prompt(text: String): PromptOutcome

    /**
     * The same prompt for one use of a Keystore key: the system binds the authentication to [cipher] (a crypto object)
     * and answers [PromptOutcome.Authorized] with the cipher that may now be finished.
     */
    suspend fun promptWithCipher(
        text: String,
        cipher: Cipher,
    ): PromptOutcome
}

/**
 * Shows the real `BiometricPrompt` in the activity [host] reports. Whether the device can authenticate is asked first
 * ([availability]), so "nothing enrolled" is [PromptOutcome.Unavailable] rather than a prompt that cannot succeed.
 * Cancelling the calling coroutine cancels the prompt.
 */
internal class ActivityPromptLauncher(
    private val host: AuthHost,
    private val availability: (Context) -> Boolean = ::canAuthenticate,
    private val cipherAvailability: (Context) -> Boolean = ::canAuthenticateForCipher,
) : PromptLauncher {
    override suspend fun prompt(text: String): PromptOutcome = show(text, null)

    override suspend fun promptWithCipher(
        text: String,
        cipher: Cipher,
    ): PromptOutcome = show(text, cipher)

    private suspend fun show(
        text: String,
        cipher: Cipher?,
    ): PromptOutcome {
        val activity = host.current() ?: return PromptOutcome.Unavailable
        if (!(if (cipher == null) availability else cipherAvailability)(activity)) return PromptOutcome.Unavailable
        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(activity)
            executor.execute {
                val callback =
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            if (continuation.isActive) continuation.resume(succeeded(cipher, result))
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            if (continuation.isActive) continuation.resume(PromptOutcome.Error(errorCode))
                        }
                    }
                val prompt = BiometricPrompt(activity, executor, callback)
                val info =
                    BiometricPrompt.PromptInfo
                        .Builder()
                        .setTitle(text)
                        .setAllowedAuthenticators(
                            if (cipher ==
                                null
                            ) {
                                authenticatorsFor(Build.VERSION.SDK_INT)
                            } else {
                                CIPHER_AUTHENTICATORS
                            },
                        ).setConfirmationRequired(false)
                        .build()
                continuation.invokeOnCancellation { executor.execute { prompt.cancelAuthentication() } }
                if (cipher ==
                    null
                ) {
                    prompt.authenticate(info)
                } else {
                    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
                }
            }
        }
    }
}

/** Whether this device can authenticate the device owner right now with the allowed authenticators. */
internal fun canAuthenticate(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(authenticatorsFor(Build.VERSION.SDK_INT)) ==
        BiometricManager.BIOMETRIC_SUCCESS

/**
 * A prompt bound to a crypto object accepts only strong biometrics or the device credential (and the credential only
 * from Android 11), which is also what per-use Keystore keys need (R-001).
 */
private const val CIPHER_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** Whether this device can authenticate the device owner for a crypto object right now. */
internal fun canAuthenticateForCipher(context: Context): Boolean =
    Build.VERSION.SDK_INT >= ANDROID_11 &&
        BiometricManager.from(context).canAuthenticate(CIPHER_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/** What a successful authentication means: the authorised cipher when one was asked for, or a bare success. */
private fun succeeded(
    asked: Cipher?,
    result: BiometricPrompt.AuthenticationResult,
): PromptOutcome {
    if (asked == null) return PromptOutcome.Succeeded
    val authorised = result.cryptoObject?.cipher ?: return PromptOutcome.Error(BiometricPrompt.ERROR_UNABLE_TO_PROCESS)
    return PromptOutcome.Authorized(authorised)
}
