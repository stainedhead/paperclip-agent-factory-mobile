package com.stainedhead.paperclipmobile.infrastructure.platform

import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.testing.contract.BiometricsContract
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorAuthenticationFailed
import platform.LocalAuthentication.LAErrorBiometryLockout
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorNotInteractive
import platform.LocalAuthentication.LAErrorPasscodeNotSet
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAErrorUserFallback
import kotlin.test.Test

/** An authenticator that answers with a script and remembers every prompt, standing in for the system prompt. */
internal class ScriptedAuthenticator : DeviceAuthenticator {
    var next: AuthOutcome = AuthOutcome.Success
    val reasons = mutableListOf<String>()

    override suspend fun evaluate(reasonText: String): AuthOutcome {
        reasons += reasonText
        return next
    }
}

class LocalAuthenticationBiometricsContractTest : BiometricsContract() {
    override fun create(): Biometrics = LocalAuthenticationBiometrics(ScriptedAuthenticator(), ReasonTexts.default)

    override fun willAnswer(
        biometrics: Biometrics,
        result: BiometricResult,
    ) {
        val authenticator = (biometrics as LocalAuthenticationBiometrics).authenticator as ScriptedAuthenticator
        authenticator.next =
            when (result) {
                BiometricResult.Approved -> AuthOutcome.Success
                BiometricResult.Cancelled -> AuthOutcome.Failure(LAErrorUserCancel)
                BiometricResult.Failed -> AuthOutcome.Failure(LAErrorAuthenticationFailed)
                BiometricResult.LockedOut -> AuthOutcome.Failure(LAErrorBiometryLockout)
                BiometricResult.Unavailable -> AuthOutcome.Failure(LAErrorPasscodeNotSet)
            }
    }

    @Test
    fun `AD-031 the LocalAuthentication adapter obeys the biometrics contract`() = runTest { runAll() }
}

class LocalAuthenticationBiometricsTest {
    private val authenticator = ScriptedAuthenticator()
    private val biometrics = LocalAuthenticationBiometrics(authenticator, ReasonTexts("Unlock it", "Confirm it"))

    @Test
    fun `AD-031 every system error code maps to one result and none to approval`() {
        val expected =
            mapOf(
                LAErrorUserCancel to BiometricResult.Cancelled,
                LAErrorSystemCancel to BiometricResult.Cancelled,
                LAErrorAppCancel to BiometricResult.Cancelled,
                LAErrorUserFallback to BiometricResult.Cancelled,
                LAErrorAuthenticationFailed to BiometricResult.Failed,
                LAErrorBiometryLockout to BiometricResult.LockedOut,
                LAErrorBiometryNotEnrolled to BiometricResult.Unavailable,
                LAErrorBiometryNotAvailable to BiometricResult.Unavailable,
                LAErrorPasscodeNotSet to BiometricResult.Unavailable,
                LAErrorNotInteractive to BiometricResult.Unavailable,
            )
        expected.forEach { (code, result) -> mapAuthError(code) shouldBe result }
    }

    @Test
    fun `AD-031 an error code nobody knows fails closed`() {
        listOf(0L, 1L, -999L, 12345L).forEach { mapAuthError(it) shouldBe BiometricResult.Failed }
    }

    @Test
    fun `AD-031 the person sees a reason that matches what they are doing`() =
        runTest {
            biometrics.authenticate(AuthReason.UnlockApp)
            biometrics.authenticate(AuthReason.ConfirmDestructiveAction)
            authenticator.reasons shouldBe listOf("Unlock it", "Confirm it")
        }

    @Test
    fun `AD-031 every prompt asks the authenticator again so an approval is never remembered`() =
        runTest {
            repeat(3) { biometrics.authenticate(AuthReason.ConfirmDestructiveAction) }
            authenticator.reasons.size shouldBe 3
        }

    @Test
    fun `AD-031 an authentication service that cannot be reached is unavailable`() =
        runTest {
            authenticator.next = AuthOutcome.ServiceUnreachable
            biometrics.authenticate(AuthReason.ConfirmDestructiveAction) shouldBe BiometricResult.Unavailable
        }

    /**
     * A bare Kotlin/Native test binary has no LocalAuthentication daemon (the system answers with an XPC error, code
     * 4099); the app test host has one, and its result is asserted in the Swift tests. Either way the answer here is
     * never an approval and always unavailable.
     */
    @Test
    fun `AD-031 the real system authenticator in a bare test binary is unavailable and never approves`() =
        runTest {
            val outcome = LAContextAuthenticator().evaluate("test")
            (outcome is AuthOutcome.Success) shouldBe false
            val biometrics = LocalAuthenticationBiometrics(LAContextAuthenticator(), ReasonTexts.default)
            biometrics.authenticate(AuthReason.ConfirmDestructiveAction) shouldBe BiometricResult.Unavailable
        }
}
