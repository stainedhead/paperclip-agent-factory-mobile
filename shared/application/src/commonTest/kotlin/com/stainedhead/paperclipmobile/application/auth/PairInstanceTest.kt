@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.domain.AuthMethod
import com.stainedhead.paperclipmobile.domain.ChallengeStatus
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class PairInstanceTest {
    private val unpaired =
        TestData
            .instance(
                status = InstanceStatus.SignedOut,
            ).copy(boardKeyId = null, keyExpiresAt = null)

    private fun TestScope.env() = Env(listOf(unpaired)) { testScheduler.currentTime }

    private fun Env.pair() = PairInstance(repository, auth, browser, clock, settings, enrollment)

    private fun Env.script(vararg statuses: ChallengeStatus) {
        statuses.forEach { auth.pollResults.add(DomainResult.Success(it)) }
    }

    @Test
    fun `FR-020 polls at the suggested interval until approved then stores the token and wipes memory`() =
        runTest {
            val env = env()
            env.script(ChallengeStatus.Pending, ChallengeStatus.Pending, ChallengeStatus.Approved)

            val result = env.pair()(unpaired.id, TestData.acme)

            result shouldBe SignInResult.SignedIn(TestData.KEY_ID, TestData.epoch + 30L.times(24 * 3600).seconds)
            env.auth.count("pollChallenge") shouldBe 3
            testScheduler.currentTime shouldBe 3_000
            env.browser.opened shouldBe 1
            env.secrets.pathsFor(unpaired.id) shouldBe setOf("instance/inst-1/boardToken")
            env.auth.lastPendingToken?.isWiped shouldBe true
            val stored = env.repository.get(unpaired.id)!!
            stored.status shouldBe InstanceStatus.SignedIn
            stored.authMethod shouldBe AuthMethod.PairedBoardKey
            stored.boardKeyId shouldBe TestData.KEY_ID
            stored.lastConnectedAt shouldBe env.clock.now()
        }

    @Test
    fun `FR-020 a cancelled challenge stores nothing and wipes the pending token`() =
        runTest {
            val env = env()
            env.script(ChallengeStatus.Pending, ChallengeStatus.Cancelled)

            env.pair()(unpaired.id, null) shouldBe SignInResult.Cancelled

            env.secrets.putCalls shouldBe 0
            env.auth.lastPendingToken?.isWiped shouldBe true
        }

    @Test
    fun `FR-020 an expired challenge stops polling once the clock passes its expiry`() =
        runTest {
            val env = env()
            env.auth.createResult = DomainResult.Success(TestData.challenge(expiresIn = 5.seconds))

            env.pair()(unpaired.id, null) shouldBe SignInResult.Expired

            env.auth.count("pollChallenge") shouldBe 5
            env.secrets.putCalls shouldBe 0
            env.auth.lastPendingToken?.isWiped shouldBe true
        }

    @Test
    fun `the server saying expired ends the wait`() =
        runTest {
            val env = env()
            env.script(ChallengeStatus.Expired)

            env.pair()(unpaired.id, null) shouldBe SignInResult.Expired
            env.auth.count("pollChallenge") shouldBe 1
        }

    @Test
    fun `a flaky network while waiting does not abort pairing but a hard error does`() =
        runTest {
            val env = env()
            env.auth.pollResults.add(DomainResult.Failure(DomainError.Timeout))
            env.auth.pollResults.add(DomainResult.Success(ChallengeStatus.Approved))
            env.pair()(unpaired.id, null).shouldBeInstanceOf<SignInResult.SignedIn>()

            val hard = env()
            hard.auth.pollResults.add(DomainResult.Failure(DomainError.NotFound))
            hard.pair()(unpaired.id, null) shouldBe SignInResult.Failed(DomainError.NotFound)
            hard.secrets.putCalls shouldBe 0
        }

    @Test
    fun `creating the challenge can fail and nothing is polled`() =
        runTest {
            val env = env()
            env.auth.createResult = DomainResult.Failure(DomainError.Unreachable)

            env.pair()(unpaired.id, null) shouldBe SignInResult.Failed(DomainError.Unreachable)
            env.auth.count("pollChallenge") shouldBe 0
            env.browser.opened shouldBe 0
        }

    @Test
    fun `an unknown factory is not found and the server is not called`() =
        runTest {
            val env = env()

            env.pair()(InstanceId("zzz"), null) shouldBe SignInResult.NotFound
            env.auth.count("createChallenge") shouldBe 0
        }

    @Test
    fun `a failure to read the key after approval rolls the stored token back`() =
        runTest {
            val env = env()
            env.script(ChallengeStatus.Approved)
            env.auth.meResult = DomainResult.Failure(DomainError.ServerError)

            env.pair()(unpaired.id, null) shouldBe SignInResult.Failed(DomainError.ServerError)

            env.secrets.pathsFor(unpaired.id) shouldBe emptySet()
            env.repository.get(unpaired.id)?.status shouldBe InstanceStatus.SignedOut
        }

    @Test
    fun `a secret store failure after approval is reported and nothing is half saved`() =
        runTest {
            val env = env()
            env.script(ChallengeStatus.Approved)
            env.secrets.failWrites = true

            env.pair()(unpaired.id, null) shouldBe SignInResult.StorageFailed
            env.repository.updateCalls shouldBe 0
        }

    @Test
    fun `an unreadable key list still pairs but leaves the expiry unknown`() =
        runTest {
            val env = env()
            env.script(ChallengeStatus.Approved)
            env.auth.keysResult = DomainResult.Failure(DomainError.Timeout)

            env.pair()(unpaired.id, null) shouldBe SignInResult.SignedIn(TestData.KEY_ID, null)
        }

    @Test
    fun `cancelling the coroutine still wipes the pending token`() =
        runTest {
            val env = env()
            val job = launch(start = CoroutineStart.UNDISPATCHED) { env.pair()(unpaired.id, null) }

            testScheduler.advanceTimeBy(2_500)
            job.cancel()
            job.join()

            env.secrets.putCalls shouldBe 0
            env.auth.lastPendingToken?.isWiped shouldBe true
        }

    @Test
    fun `a zero poll interval from the server is clamped so the app never busy-loops`() =
        runTest {
            val env = env()
            env.auth.createResult = DomainResult.Success(TestData.challenge(pollMs = 0))
            env.script(ChallengeStatus.Pending, ChallengeStatus.Cancelled)

            env.pair()(unpaired.id, null)

            testScheduler.currentTime shouldBe 500
        }
}
