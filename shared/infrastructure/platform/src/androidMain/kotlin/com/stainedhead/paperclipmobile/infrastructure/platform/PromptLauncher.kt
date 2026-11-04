package com.stainedhead.paperclipmobile.infrastructure.platform

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** What the system prompt said, or that there was no prompt to show. */
internal sealed interface PromptOutcome {
    data object Succeeded : PromptOutcome

    /** No resumed activity, or the device cannot authenticate at all (no hardware, nothing enrolled, no credential). */
    data object Unavailable : PromptOutcome

    data class Error(
        val code: Int,
    ) : PromptOutcome
}

/** One system prompt for device-owner authentication. A fake answers it in tests. */
internal interface PromptLauncher {
    suspend fun prompt(text: String): PromptOutcome
}

/**
 * Shows the real `BiometricPrompt` in the activity [host] reports. Whether the device can authenticate is asked first
 * ([availability]), so "nothing enrolled" is [PromptOutcome.Unavailable] rather than a prompt that cannot succeed.
 * Cancelling the calling coroutine cancels the prompt.
 */
internal class ActivityPromptLauncher(
    private val host: AuthHost,
    private val availability: (Context) -> Boolean = ::canAuthenticate,
) : PromptLauncher {
    override suspend fun prompt(text: String): PromptOutcome {
        val activity = host.current() ?: return PromptOutcome.Unavailable
        if (!availability(activity)) return PromptOutcome.Unavailable
        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(activity)
            executor.execute {
                val callback =
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            if (continuation.isActive) continuation.resume(PromptOutcome.Succeeded)
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
                        .setAllowedAuthenticators(authenticatorsFor(Build.VERSION.SDK_INT))
                        .setConfirmationRequired(false)
                        .build()
                continuation.invokeOnCancellation { executor.execute { prompt.cancelAuthentication() } }
                prompt.authenticate(info)
            }
        }
    }
}

/** Whether this device can authenticate the device owner right now with the allowed authenticators. */
internal fun canAuthenticate(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(authenticatorsFor(Build.VERSION.SDK_INT)) ==
        BiometricManager.BIOMETRIC_SUCCESS
