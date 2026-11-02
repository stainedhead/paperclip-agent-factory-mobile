package com.stainedhead.paperclipmobile.testing

import com.stainedhead.paperclipmobile.domain.ChallengeStatus
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
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
import com.stainedhead.paperclipmobile.testing.fakes.approvePairings
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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
    fun `scripted biometric results answer prompts in order before the default applies`() =
        runTest {
            val biometrics = FakeBiometrics(BiometricResult.Approved)
            biometrics.script(BiometricResult.Cancelled, BiometricResult.Failed)

            biometrics.authenticate(AuthReason.UnlockApp) shouldBe BiometricResult.Cancelled
            biometrics.authenticate(AuthReason.ConfirmDestructiveAction) shouldBe BiometricResult.Failed
            biometrics.authenticate(AuthReason.UnlockApp) shouldBe BiometricResult.Approved
            biometrics.calls shouldBe 3
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

class FakeAuthApprovalTest {
    @Test
    fun `approvePairings hands out a fresh unwiped challenge each time and approves every poll`() =
        runTest {
            val gateway = FakeAuthGateway()
            val now = Instant.fromEpochSeconds(2_000_000_000)
            gateway.approvePairings(from = { now })
            val uri = TestData.uri()

            val first = (gateway.createChallenge(uri, "Phone", null) as DomainResult.Success).value
            first.wipe()
            val second = (gateway.createChallenge(uri, "Phone", null) as DomainResult.Success).value

            second.pendingBoardToken.isWiped shouldBe false
            (second.expiresAt > now) shouldBe true
            val poll = gateway.pollChallenge(uri, second.id, second.challengeToken)
            (poll as DomainResult.Success).value shouldBe ChallengeStatus.Approved
        }

    @Test
    fun `a new pairing issues a new key so a revoked factory can sign in again`() =
        runTest {
            val gateway = FakeAuthGateway()
            val uri = TestData.uri()
            val id = InstanceId("cloud")
            gateway.hostOf = { uri }
            gateway.approvePairings(from = { Instant.fromEpochSeconds(2_000_000_000) })

            gateway.revokeCurrentKey(id)
            gateway.me(id) shouldBe DomainResult.Failure(DomainError.Unauthorized)

            gateway.createChallenge(uri, "Phone", null)
            (gateway.me(id) is DomainResult.Success) shouldBe true
        }

    @Test
    fun `a new pairing on another host leaves a revoked factory revoked`() =
        runTest {
            val gateway = FakeAuthGateway()
            val id = InstanceId("cloud")
            gateway.hostOf = { TestData.uri() }
            gateway.approvePairings(from = { Instant.fromEpochSeconds(2_000_000_000) })
            gateway.revokeCurrentKey(id)

            gateway.createChallenge(TestData.uri("https://other.example.com"), "Phone", null)

            gateway.me(id) shouldBe DomainResult.Failure(DomainError.Unauthorized)
        }
}
