package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.InstanceLocks
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** After a 401 the next call must read the token again, never reuse the one that was just refused (ADR-145). */
class SessionTokensForgottenTest {
    private class Recorder : SessionTokens {
        val forgotten = mutableListOf<InstanceId>()

        override fun forget(instanceId: InstanceId) {
            forgotten += instanceId
        }

        override fun forgetAll() = error("not expected")
    }

    private val id = InstanceId("inst-1")

    private fun marker(
        env: Env,
        tokens: SessionTokens,
    ) = SignedOutMarker(env.repository, InstanceLocks(), tokens)

    @Test
    fun `a 401 makes the cache forget that factory's token`() =
        runTest {
            val env = Env(listOf(TestData.instance()))
            val tokens = Recorder()

            marker(env, tokens).noteIfUnauthorized(id, DomainResult.Failure(DomainError.Unauthorized))

            tokens.forgotten shouldBe listOf(id)
        }

    @Test
    fun `other outcomes leave the cache alone`() =
        runTest {
            val env = Env(listOf(TestData.instance()))
            val tokens = Recorder()
            val marker = marker(env, tokens)

            marker.noteIfUnauthorized(id, DomainResult.Success(Unit))
            marker.noteIfUnauthorized(id, DomainResult.Failure(DomainError.NotFound))
            marker.noteIfUnauthorized(id, DomainResult.Failure(DomainError.DeviceAuthDeclined))

            tokens.forgotten shouldBe emptyList()
        }

    @Test
    fun `a key the device invalidated also drops the cached token`() =
        runTest {
            val env = Env(listOf(TestData.instance()))
            val tokens = Recorder()

            marker(env, tokens).noteIfUnauthorized(id, DomainResult.Failure(DomainError.KeyInvalidated))

            tokens.forgotten shouldBe listOf(id)
        }
}
