package com.stainedhead.paperclipmobile.application.instance

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.auth.RevokeOutcome
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.SecretKey
import com.stainedhead.paperclipmobile.domain.port.SecretName
import com.stainedhead.paperclipmobile.domain.port.SecretPolicy
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RemoveInstanceTest {
    private val instance = TestData.instance()
    private val other = TestData.instance("other", "Cloud", sortOrder = 1)

    private fun Env.remove() = RemoveInstance(repository, secrets, revoker, deviceAuth)

    private suspend fun Env.storeAllSecrets() {
        SecretName.entries.forEach {
            secrets.put(SecretKey(instance.id, it), Secret.of("x"), SecretPolicy(false))
        }
        storeToken(other)
    }

    @Test
    fun `FR-006 removal asks the device first, then revokes, then leaves no secret of that factory`() =
        runTest {
            val env = Env(listOf(instance, other))
            env.storeAllSecrets()

            val result = env.remove()(instance.id)

            result shouldBe RemoveInstanceResult.Removed(RevokeOutcome.Revoked)
            env.biometrics.calls shouldBe 1
            env.auth.count("revokeCurrentKey") shouldBe 1
            env.secrets.pathsFor(instance.id) shouldBe emptySet()
            env.secrets.pathsFor(other.id).size shouldBe 1
            env.repository.deletedIds shouldBe listOf(instance.id)
            env.repository.current.map { it.id } shouldBe listOf(other.id)
        }

    @Test
    fun `FR-014 cancel, failure, lockout and unavailable make zero gateway calls and change nothing`() =
        runTest {
            listOf(
                BiometricResult.Cancelled,
                BiometricResult.Failed,
                BiometricResult.LockedOut,
                BiometricResult.Unavailable,
            ).forEach { denied ->
                val env = Env(listOf(instance))
                env.storeAllSecrets()
                env.biometrics.result = denied

                env.remove()(instance.id) shouldBe RemoveInstanceResult.NotAuthorized(denied)

                env.auth.calls shouldBe emptyMap()
                env.repository.deletedIds shouldBe emptyList()
                env.secrets.pathsFor(instance.id).size shouldBe SecretName.entries.size
            }
        }

    @Test
    fun `ADR-012 a revoke that times out is attempted once and still leaves no secrets behind`() =
        runTest {
            val env = Env(listOf(instance))
            env.storeAllSecrets()
            env.auth.revokeCurrentResult = DomainResult.Failure(DomainError.Timeout)

            env.remove()(instance.id) shouldBe RemoveInstanceResult.Removed(RevokeOutcome.Unconfirmed)

            env.auth.count("revokeCurrentKey") shouldBe 1
            env.secrets.pathsFor(instance.id) shouldBe emptySet()
            env.repository.deletedIds shouldBe listOf(instance.id)
        }

    @Test
    fun `removing a signed out factory does not call the server`() =
        runTest {
            val signedOut = instance.copy(status = InstanceStatus.SignedOut)
            val env = Env(listOf(signedOut))

            env.remove()(signedOut.id) shouldBe RemoveInstanceResult.Removed(RevokeOutcome.NotAttempted)
            env.auth.calls shouldBe emptyMap()
        }

    @Test
    fun `an unknown factory is not found and the device is not asked`() =
        runTest {
            val env = Env(listOf(instance))

            env.remove()(InstanceId("zzz")) shouldBe RemoveInstanceResult.NotFound
            env.biometrics.calls shouldBe 0
        }
}
