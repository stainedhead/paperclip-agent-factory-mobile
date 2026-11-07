package com.stainedhead.paperclipmobile.infrastructure.securestore

import android.security.keystore.KeyProperties
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

/**
 * ADR-149 pins the Android key policy: a protected key accepts strong biometrics or the device credential (the PIN
 * fallback), so enrolling a new fingerprint does not invalidate it. Changing the set changes who can open the board
 * token and what forces a re-pair, which is an owner decision; this test makes such a change deliberate.
 */
class KeyPolicyTest {
    @Test
    fun `ADR-149 protected keys accept strong biometrics or the device credential`() {
        PROTECTED_KEY_AUTHENTICATORS shouldBe
            (KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
        // Pinned numerically as well, so a change of both sides at once is still noticed.
        PROTECTED_KEY_AUTHENTICATORS shouldBe STRONG_OR_CREDENTIAL
    }

    @Test
    fun `ADR-149 the policy is not biometric only, which a new fingerprint would invalidate`() {
        PROTECTED_KEY_AUTHENTICATORS shouldNotBe KeyProperties.AUTH_BIOMETRIC_STRONG
        (PROTECTED_KEY_AUTHENTICATORS and KeyProperties.AUTH_DEVICE_CREDENTIAL) shouldBe
            KeyProperties.AUTH_DEVICE_CREDENTIAL
    }

    private companion object {
        /** `AUTH_BIOMETRIC_STRONG` (1) or `AUTH_DEVICE_CREDENTIAL` (2), written out so a changed constant fails. */
        const val STRONG_OR_CREDENTIAL = 3
    }
}
