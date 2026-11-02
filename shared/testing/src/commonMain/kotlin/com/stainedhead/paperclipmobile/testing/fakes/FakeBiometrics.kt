package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.Biometrics

/**
 * Answers prompts from a script first (see [script]), then with [result], and remembers each prompt. Prompts come from
 * the shared use cases on one thread at a time, so the queue needs no lock.
 */
class FakeBiometrics(
    var result: BiometricResult = BiometricResult.Approved,
) : Biometrics {
    val prompts = mutableListOf<AuthReason>()
    private val scripted = ArrayDeque<BiometricResult>()

    val calls: Int get() = prompts.size

    /** The next prompts, in order, are answered with [results]; after that [result] applies again. */
    fun script(vararg results: BiometricResult) {
        scripted.addAll(results)
    }

    override suspend fun authenticate(reason: AuthReason): BiometricResult {
        prompts += reason
        return scripted.removeFirstOrNull() ?: result
    }
}
