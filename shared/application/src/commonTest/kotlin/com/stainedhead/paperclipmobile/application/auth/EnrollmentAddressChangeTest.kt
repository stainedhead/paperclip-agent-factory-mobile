@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.instance.InstanceEdit
import com.stainedhead.paperclipmobile.application.instance.ProbeResult
import com.stainedhead.paperclipmobile.application.instance.UpdateInstance
import com.stainedhead.paperclipmobile.domain.ChallengeStatus
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeHealthGateway
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** F-A-1: a key must never be sent to a host other than the one that issued it, even if the address is edited. */
class EnrollmentAddressChangeTest {
    private val unpaired =
        TestData
            .instance(status = InstanceStatus.SignedOut)
            .copy(boardKeyId = null, keyExpiresAt = null)
    private val newHost = TestData.uri("https://evil-b.example.com")

    private fun TestScope.env() = Env(listOf(unpaired)) { testScheduler.currentTime }

    private fun Env.pair() = PairInstance(repository, auth, browser, clock, settings, enrollment)

    private fun Env.update() = UpdateInstance(repository, secrets, validate)

    private fun edit(
        name: String = unpaired.displayName,
        address: String = unpaired.baseUri.value,
    ) = InstanceEdit(
        name,
        address,
        unpaired.defaultCompanyId,
        emptyList(),
        probed = ProbeResult.Accepted(TestData.uri(address), FakeHealthGateway.healthy()),
    )

    private fun Env.keyCallsToNewHost() =
        auth.authenticatedCalls.filter { it.host == newHost && it.token == TestData.TOKEN }

    @Test
    fun `pairing then address edit never sends the key to the new host`() =
        runTest {
            val env = env()
            env.auth.pollResults.add(
                kotlin.run {
                    com.stainedhead.paperclipmobile.domain.DomainResult
                        .Success(ChallengeStatus.Pending)
                },
            )
            env.auth.pollResults.add(
                com.stainedhead.paperclipmobile.domain.DomainResult
                    .Success(ChallengeStatus.Approved),
            )
            val pairing = async { env.pair()(unpaired.id, null) }
            testScheduler.advanceTimeBy(1_500)
            testScheduler.runCurrent()

            env.update()(unpaired.id, edit(address = newHost.value))
            advanceUntilIdle()
            val result = pairing.await()

            env.keyCallsToNewHost() shouldBe emptyList()
            result.shouldBeInstanceOf<SignInResult.Failed>()
            env.repository.get(unpaired.id)?.baseUri shouldBe newHost
            env.repository.get(unpaired.id)?.status shouldBe InstanceStatus.SignedOut
            env.secrets.pathsFor(unpaired.id) shouldBe emptySet()
        }

    @Test
    fun `enrollment does not overwrite a concurrent rename`() =
        runTest {
            val env = env()
            env.auth.pollResults.add(
                com.stainedhead.paperclipmobile.domain.DomainResult
                    .Success(ChallengeStatus.Pending),
            )
            env.auth.pollResults.add(
                com.stainedhead.paperclipmobile.domain.DomainResult
                    .Success(ChallengeStatus.Approved),
            )
            val pairing = async { env.pair()(unpaired.id, null) }
            testScheduler.advanceTimeBy(1_500)
            testScheduler.runCurrent()

            env.update()(unpaired.id, edit(name = "Renamed"))
            advanceUntilIdle()

            pairing.await().shouldBeInstanceOf<SignInResult.SignedIn>()
            env.repository.get(unpaired.id)?.displayName shouldBe "Renamed"
            env.repository.get(unpaired.id)?.status shouldBe InstanceStatus.SignedIn
        }

    @Test
    fun `a pasted key is not used against a host the address was edited to while it was validated`() =
        runTest {
            val env = env()
            env.auth.onCall = { call ->
                if (call.name == "meWithKey") env.update()(unpaired.id, edit(address = newHost.value))
            }

            val result =
                SaveManualBoardKey(env.repository, env.auth, env.enrollment)(unpaired.id, Secret.of(TestData.TOKEN))

            env.keyCallsToNewHost() shouldBe emptyList()
            result shouldBe SignInResult.Failed(DomainError.Conflict)
            env.repository.get(unpaired.id)?.baseUri shouldBe newHost
            env.secrets.pathsFor(unpaired.id) shouldBe emptySet()
        }

    @Test
    fun `an address edit does not revert a key enrollment that finished meanwhile`() =
        runTest {
            val env = env()
            val signedIn = unpaired.copy(boardKeyId = "key-x", status = InstanceStatus.SignedIn)
            val racing = RacingRepository(env.repository) { env.repository.update(signedIn) }
            val update = UpdateInstance(racing, env.secrets, env.validate)

            update(unpaired.id, edit(name = "Renamed"))

            env.repository.get(unpaired.id)?.boardKeyId shouldBe "key-x"
            env.repository.get(unpaired.id)?.status shouldBe InstanceStatus.SignedIn
            env.repository.get(unpaired.id)?.displayName shouldBe "Renamed"
        }

    /** Runs [beforeFirstList] once when the list of factories is first read, as a concurrent writer would. */
    private class RacingRepository(
        private val inner: InstanceRepository,
        private val beforeFirstList: suspend () -> Unit,
    ) : InstanceRepository by inner {
        private var fired = false

        override fun observeAll(): Flow<List<Instance>> =
            flow {
                if (!fired) {
                    fired = true
                    beforeFirstList()
                }
                emitAll(inner.observeAll())
            }
    }
}
