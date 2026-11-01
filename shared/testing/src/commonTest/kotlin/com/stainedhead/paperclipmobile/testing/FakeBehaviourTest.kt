package com.stainedhead.paperclipmobile.testing

import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.fakes.FakeAuthGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeBiometrics
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import com.stainedhead.paperclipmobile.testing.fakes.FakeConnectivity
import com.stainedhead.paperclipmobile.testing.fakes.FakeIdGenerator
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class FakeBehaviourTest {
    @Test
    fun `the clock follows the scheduler it is tied to and manual advances`() =
        runTest {
            val clock = FakeClock(elapsedMillis = { testScheduler.currentTime })
            val start = clock.now()

            kotlinx.coroutines.delay(2_000)
            clock.advance(3.seconds)

            (clock.now() - start) shouldBe 5.seconds
        }

    @Test
    fun `ids are sequential`() {
        val ids = FakeIdGenerator("x-")

        listOf(ids.generate(), ids.generate()) shouldBe listOf("x-1", "x-2")
    }

    @Test
    fun `the secret store can simulate invalidation denial and write failure`() =
        runTest {
            val store = FakeSecretStore()
            val key =
                SecretKey(
                    com.stainedhead.paperclipmobile.domain
                        .InstanceId("a"),
                    SecretName.BoardToken,
                )
            val policy = SecretPolicy(requireUserPresence = true)

            store.put(key, Secret.of("v"), policy) shouldBe SecretWriteResult.Stored
            store.invalidated = true
            store.get(key, "r") shouldBe SecretReadResult.Invalidated
            store.invalidated = false
            store.denyReads = true
            store.get(key, "r") shouldBe SecretReadResult.Denied
            store.failWrites = true
            store.put(key, Secret.of("w"), policy) shouldBe SecretWriteResult.Failed
            store.putCalls shouldBe 2
        }

    @Test
    fun `biometrics connectivity and the auth gateway record what happened`() =
        runTest {
            val biometrics = FakeBiometrics(BiometricResult.LockedOut)
            biometrics.authenticate(AuthReason.UnlockApp) shouldBe BiometricResult.LockedOut
            biometrics.calls shouldBe 1

            val connectivity = FakeConnectivity()
            connectivity.setOnline(false)
            connectivity.isOnline.value shouldBe false

            val gateway = FakeAuthGateway()
            gateway.revokeCurrentKey(
                com.stainedhead.paperclipmobile.domain
                    .InstanceId("a"),
            ) shouldBe
                DomainResult.Success(Unit)
            gateway.count("revokeCurrentKey") shouldBe 1
            gateway.count("me") shouldBe 0
        }
}
