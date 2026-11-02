@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.instance.InstanceEdit
import com.stainedhead.paperclipmobile.application.instance.ProbeResult
import com.stainedhead.paperclipmobile.application.instance.UpdateInstance
import com.stainedhead.paperclipmobile.domain.AuthMethod
import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.HealthReport
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.ServerStatus
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** ADR-033: the address edit and the key enrollment of one factory never write over each other. */
class InstanceLockingTest {
    private val unpaired =
        TestData.instance(status = InstanceStatus.SignedOut).copy(boardKeyId = null, keyExpiresAt = null)
    private val newHost = TestData.uri("https://other.example.com")

    /** Suspends the first `update` until [release] completes, as a slow storage write would. */
    private class GatedRepository(
        private val inner: InstanceRepository,
        val reached: CompletableDeferred<Unit>,
        val release: CompletableDeferred<Unit>,
    ) : InstanceRepository by inner {
        private var gated = true

        override suspend fun update(instance: Instance) {
            if (gated) {
                gated = false
                reached.complete(Unit)
                release.await()
            }
            inner.update(instance)
        }
    }

    @Test
    fun `an address edit during a key enrollment cannot leave a signed in factory without its key`() =
        runTest {
            val env = Env(listOf(unpaired))
            val gated = GatedRepository(env.repository, CompletableDeferred(), CompletableDeferred())
            val enrollment = KeyEnrollment(env.auth, env.secrets, gated, env.clock, env.settings, env.locks)
            val update = UpdateInstance(gated, env.secrets, env.validate, env.locks)
            val accepted =
                ProbeResult.Accepted(
                    newHost,
                    HealthReport(ServerStatus.Ok, DeploymentMode.Authenticated, Exposure.Private, "1", false),
                )

            val signIn =
                launch { enrollment.complete(unpaired, Secret.of(TestData.TOKEN), AuthMethod.PairedBoardKey, "key-1") }
            gated.reached.await()
            val edit =
                launch { update(unpaired.id, InstanceEdit("Home factory", newHost.value, null, emptyList(), accepted)) }
            advanceUntilIdle()
            gated.release.complete(Unit)
            signIn.join()
            edit.join()

            val stored = env.repository.get(unpaired.id)!!
            val keyPresent = env.secrets.pathsFor(unpaired.id).isNotEmpty()
            // Consistent either way: the enrolled key on the old address, or no key and Signed out on the new one.
            (stored.status == InstanceStatus.SignedIn) shouldBe keyPresent
            (stored.baseUri == newHost) shouldBe (stored.status == InstanceStatus.SignedOut)
        }
}
