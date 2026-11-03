package com.stainedhead.paperclipmobile.infrastructure.platform

import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.Biometrics
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorBiometryLockout
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorNotInteractive
import platform.LocalAuthentication.LAErrorPasscodeNotSet
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAErrorUserFallback

/** What the system prompt said: approved, an `LAError` code, or that the authentication service was not reachable. */
internal sealed interface AuthOutcome {
    data object Success : AuthOutcome

    /** The error did not come from LocalAuthentication, for example the XPC connection to its daemon failed. */
    data object ServiceUnreachable : AuthOutcome

    data class Failure(
        val code: Long,
    ) : AuthOutcome
}

/** One system prompt for device-owner authentication. A fake answers it in tests. */
internal interface DeviceAuthenticator {
    suspend fun evaluate(reasonText: String): AuthOutcome
}

/** The sentence the system prompt shows for each reason. The app passes localized text; this is only a default. */
class ReasonTexts(
    val unlockApp: String,
    val confirmDestructiveAction: String,
) {
    internal fun textFor(reason: AuthReason): String =
        when (reason) {
            AuthReason.UnlockApp -> unlockApp
            AuthReason.ConfirmDestructiveAction -> confirmDestructiveAction
        }

    companion object {
        val default: ReasonTexts = ReasonTexts("Unlock Paperclip", "Confirm this action")
    }
}

/**
 * [Biometrics] on LocalAuthentication (AD-031). The policy is device-owner authentication: Face ID or Touch ID with the
 * device passcode as the fallback. A phone with no passcode cannot evaluate it at all, which is reported as
 * [BiometricResult.Unavailable], so destructive actions stay blocked there (fail closed, INTENT principle 1). A new
 * system context is made for every prompt, so an approval is never reused. Anything unrecognised is
 * [BiometricResult.Failed].
 */
class LocalAuthenticationBiometrics internal constructor(
    internal val authenticator: DeviceAuthenticator,
    private val reasons: ReasonTexts,
) : Biometrics {
    constructor(reasons: ReasonTexts) : this(LAContextAuthenticator(), reasons)

    override suspend fun authenticate(reason: AuthReason): BiometricResult =
        when (val outcome = authenticator.evaluate(reasons.textFor(reason))) {
            AuthOutcome.Success -> BiometricResult.Approved
            AuthOutcome.ServiceUnreachable -> BiometricResult.Unavailable
            is AuthOutcome.Failure -> mapAuthError(outcome.code)
        }
}

/** Maps an `LAError` code to the port's result. Unknown codes fail closed. */
internal fun mapAuthError(code: Long): BiometricResult =
    when (code) {
        LAErrorUserCancel, LAErrorSystemCancel, LAErrorAppCancel, LAErrorUserFallback -> {
            BiometricResult.Cancelled
        }

        LAErrorBiometryLockout -> {
            BiometricResult.LockedOut
        }

        LAErrorBiometryNotEnrolled, LAErrorBiometryNotAvailable, LAErrorPasscodeNotSet, LAErrorNotInteractive -> {
            BiometricResult.Unavailable
        }

        else -> {
            BiometricResult.Failed
        } // including LAErrorAuthenticationFailed
    }
