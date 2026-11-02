@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.domain.AuthMethod
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test

/** F-A-5: a failed or cancelled enrollment must leave the key, the status and the configuration in agreement. */
class EnrollmentFailureTest {
    private val signedIn = TestData.instance()
    private val oldKey = SecretKey(signedIn.id, SecretName.BoardToken)
    private val newToken = "pcp_board_TESTONLY0000000000000002"

    private suspend fun Env.withOldKey() = storeToken(signedIn)

    @Test
    fun `failed validation of the new key keeps the previous key and status`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.withOldKey()
            env.auth.meWithKeyResult = DomainResult.Failure(DomainError.Timeout)

            env.enrollment.complete(signedIn, Secret.of(newToken), AuthMethod.PairedBoardKey, null) shouldBe
                SignInResult.Failed(DomainError.Timeout)

            env.secrets.peek(oldKey) shouldBe TestData.TOKEN
            env.repository.get(signedIn.id)?.status shouldBe InstanceStatus.SignedIn
            env.repository.get(signedIn.id)?.boardKeyId shouldBe TestData.KEY_ID
        }

    @Test
    fun `a storage failure keeps the previous key and status`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.withOldKey()
            env.secrets.failWrites = true

            env.enrollment.complete(signedIn, Secret.of(newToken), AuthMethod.PairedBoardKey, null) shouldBe
                SignInResult.StorageFailed

            env.secrets.peek(oldKey) shouldBe TestData.TOKEN
            env.repository.get(signedIn.id)?.status shouldBe InstanceStatus.SignedIn
        }

    @Test
    fun `re-pairing a signed in factory replaces the key and records the new key id`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.withOldKey()
            env.auth.meWithKeyResult = DomainResult.Success(TestData.access(keyId = "key-new"))

            env.enrollment
                .complete(signedIn, Secret.of(newToken), AuthMethod.PairedBoardKey, null)
                .shouldBeInstanceOf<SignInResult.SignedIn>()

            env.secrets.peek(oldKey) shouldBe newToken
            env.repository.get(signedIn.id)?.boardKeyId shouldBe "key-new"
            env.repository.get(signedIn.id)?.status shouldBe InstanceStatus.SignedIn
        }

    @Test
    fun `cancel between put and update leaves no orphan secret`() =
        runTest {
            val unpaired = signedIn.copy(boardKeyId = null, keyExpiresAt = null, status = InstanceStatus.SignedOut)
            val env = Env(listOf(unpaired))
            val cancelling = CancelAfterPut(env.secrets)
            val enrollment = KeyEnrollment(env.auth, cancelling, env.repository, env.clock, env.settings)

            val job =
                launch {
                    SaveManualBoardKey(env.repository, env.auth, enrollment)(unpaired.id, Secret.of(TestData.TOKEN))
                }
            cancelling.target = job
            advanceUntilIdle()
            job.join()

            val stored = env.repository.get(unpaired.id)!!
            val hasSecret = env.secrets.pathsFor(unpaired.id).isNotEmpty()
            hasSecret shouldBe (stored.status == InstanceStatus.SignedIn)
            (stored.boardKeyId != null) shouldBe hasSecret
        }

    @Test
    fun `a failing update after put removes the new key and marks the factory signed out`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.withOldKey()
            val failing = FailFirstUpdate(env.repository)
            val enrollment = KeyEnrollment(env.auth, env.secrets, failing, env.clock, env.settings)

            shouldThrow<IllegalStateException> {
                enrollment.complete(signedIn, Secret.of(newToken), AuthMethod.PairedBoardKey, null)
            }

            env.secrets.pathsFor(signedIn.id) shouldBe emptySet()
            env.repository.get(signedIn.id)?.status shouldBe InstanceStatus.SignedOut
            env.repository.get(signedIn.id)?.boardKeyId shouldBe null
        }

    /** Cancels the calling coroutine right after the key was written, the way a dismissed screen would. */
    private class CancelAfterPut(
        private val inner: SecretStore,
    ) : SecretStore by inner {
        var target: Job? = null

        override suspend fun put(
            key: SecretKey,
            value: Secret,
            policy: SecretPolicy,
        ): SecretWriteResult {
            val result = inner.put(key, value, policy)
            target?.cancel()
            yield()
            return result
        }
    }

    private class FailFirstUpdate(
        private val inner: InstanceRepository,
    ) : InstanceRepository by inner {
        private var failed = false

        override suspend fun update(instance: Instance) {
            if (!failed) {
                failed = true
                error("disk full")
            }
            inner.update(instance)
        }
    }
}
