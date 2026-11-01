package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.Biometrics

/** Answers every prompt with [result] and remembers each prompt. */
class FakeBiometrics(
    var result: BiometricResult = BiometricResult.Approved,
) : Biometrics {
    val prompts = mutableListOf<AuthReason>()

    val calls: Int get() = prompts.size

    override suspend fun authenticate(reason: AuthReason): BiometricResult {
        prompts += reason
        return result
    }
}
