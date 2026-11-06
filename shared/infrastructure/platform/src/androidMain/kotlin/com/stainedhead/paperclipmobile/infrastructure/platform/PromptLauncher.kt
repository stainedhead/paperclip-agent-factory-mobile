package com.stainedhead.paperclipmobile.infrastructure.platform

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellableContinuation
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
 * The place a system prompt can be shown: in the app, the resumed activity. It is a seam so the launcher's handling of
 * a prompt that cannot be shown, that throws, or that is cancelled is tested without an activity.
 */
internal interface PromptScreen {
    /** Whether the device can authenticate the device owner right now (for a crypto object when [forCipher]). */
    fun canAuthenticate(forCipher: Boolean): Boolean

    /** Runs [block] on the main thread. */
    fun post(block: () -> Unit)

    /** On the main thread: whether the prompt can be shown now (activity alive, fragment state not saved). */
    fun canShowNow(): Boolean

    /**
     * On the main thread: shows the system prompt and later reports its one result to [onResult]. Returns what cancels
     * the prompt. May throw [IllegalStateException] or [IllegalArgumentException] when the system refuses to show it.
     */
    fun show(
        text: String,
        cipher: Cipher?,
        onResult: (PromptOutcome) -> Unit,
    ): () -> Unit
}

/** Where the launcher finds the screen, or `null` when the app is not in the foreground. */
internal fun interface PromptScreenSource {
    suspend fun current(): PromptScreen?
}

/**
 * Shows the system `BiometricPrompt` on the screen the source reports. Whether the device can authenticate is asked
 * first, so "nothing enrolled" is [PromptOutcome.Unavailable] rather than a prompt that cannot succeed. Cancelling the
 * calling coroutine cancels the prompt.
 */
internal class ActivityPromptLauncher(
    private val source: PromptScreenSource,
) : PromptLauncher {
    /** The prompt in the activity [host] reports; [availability] answers whether the device can authenticate. */
    constructor(
        host: AuthHost,
        availability: (Context) -> Boolean = ::canAuthenticate,
        cipherAvailability: (Context) -> Boolean = ::canAuthenticateForCipher,
    ) : this(
        PromptScreenSource {
            host.current()?.let { ActivityPromptScreen(it, availability, cipherAvailability) }
        },
    )

    override suspend fun prompt(text: String): PromptOutcome = show(text, null)

    override suspend fun promptWithCipher(
        text: String,
        cipher: Cipher,
    ): PromptOutcome = show(text, cipher)

    private suspend fun show(
        text: String,
        cipher: Cipher?,
    ): PromptOutcome {
        val screen = source.current() ?: return PromptOutcome.Unavailable
        if (!screen.canAuthenticate(forCipher = cipher != null)) return PromptOutcome.Unavailable
        return suspendCancellableCoroutine { continuation ->
            var cancel: (() -> Unit)? = null
            screen.post {
                // Between the caller's check and this moment the caller may have gone or the activity stopped; an
                // unresumed continuation would hang the caller (and whatever it holds) for ever.
                when {
                    !continuation.isActive -> Unit
                    !screen.canShowNow() -> continuation.resume(PromptOutcome.Unavailable)
                    else -> cancel = showOrUnavailable(screen, text, cipher, continuation)
                }
            }
            continuation.invokeOnCancellation { screen.post { cancel?.invoke() } }
        }
    }
}

/** Shows the prompt; a system that refuses (state saved, no fragment manager) answers unavailable, never throws. */
private fun showOrUnavailable(
    screen: PromptScreen,
    text: String,
    cipher: Cipher?,
    continuation: CancellableContinuation<PromptOutcome>,
): (() -> Unit)? =
    try {
        screen.show(text, cipher) { if (continuation.isActive) continuation.resume(it) }
    } catch (_: IllegalStateException) {
        continuation.resume(PromptOutcome.Unavailable)
        null
    } catch (_: IllegalArgumentException) {
        continuation.resume(PromptOutcome.Unavailable)
        null
    }

/** The real screen: the resumed [FragmentActivity] and its `BiometricPrompt`. */
internal class ActivityPromptScreen(
    private val activity: FragmentActivity,
    private val availability: (Context) -> Boolean,
    private val cipherAvailability: (Context) -> Boolean,
) : PromptScreen {
    private val executor = ContextCompat.getMainExecutor(activity)

    override fun canAuthenticate(forCipher: Boolean): Boolean =
        if (forCipher) cipherAvailability(activity) else availability(activity)

    override fun post(block: () -> Unit) = executor.execute(block)

    override fun canShowNow(): Boolean =
        !activity.isFinishing && !activity.isDestroyed && !activity.supportFragmentManager.isStateSaved

    override fun show(
        text: String,
        cipher: Cipher?,
        onResult: (PromptOutcome) -> Unit,
    ): () -> Unit {
        val callback =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                    onResult(succeeded(cipher, result))

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) = onResult(PromptOutcome.Error(errorCode))
            }
        val prompt = BiometricPrompt(activity, executor, callback)
        val authenticators = if (cipher == null) authenticatorsFor(Build.VERSION.SDK_INT) else CIPHER_AUTHENTICATORS
        val info =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(text)
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(false)
                .build()
        if (cipher ==
            null
        ) {
            prompt.authenticate(info)
        } else {
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        }
        return { prompt.cancelAuthentication() }
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
internal const val CIPHER_AUTHENTICATORS =
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
