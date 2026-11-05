package com.stainedhead.paperclipmobile.infrastructure.platform

import android.app.KeyguardManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * What the real `BiometricManager` and the real launcher decide on an emulator. The system prompt itself needs a person
 * (or an enrolled fingerprint and `adb emu finger touch`), so only the decisions before it are exercised here.
 */
@RunWith(AndroidJUnit4::class)
class BiometricAvailabilityDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun theDeviceCanAuthenticateExactlyWhenItHasASecureLockScreenOrEnrolledBiometrics() {
        val secure = context.getSystemService(KeyguardManager::class.java).isDeviceSecure
        assertEquals(secure, canAuthenticate(context))
    }

    @Test
    fun withNoResumedActivityThePromptIsNeverShownAndTheResultIsUnavailable() =
        runBlocking {
            val biometrics = BiometricPromptBiometrics(AuthHost { null }, ReasonTexts.default)
            for (reason in AuthReason.entries) {
                assertEquals(BiometricResult.Unavailable, biometrics.authenticate(reason))
            }
        }

    @Test
    fun theAuthenticatorsTheLauncherAsksForAreAcceptedByTheLibraryOnThisDevice() {
        val authenticators = authenticatorsFor(android.os.Build.VERSION.SDK_INT)
        // The call throws IllegalArgumentException for a combination the platform does not support.
        androidx.biometric.BiometricManager
            .from(context)
            .canAuthenticate(authenticators)
    }
}
