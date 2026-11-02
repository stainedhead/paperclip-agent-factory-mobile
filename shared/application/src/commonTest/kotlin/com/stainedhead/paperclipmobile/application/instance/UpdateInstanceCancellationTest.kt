@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.application.instance

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.HealthReport
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.ServerStatus
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** F-R-2: cancelling an address edit must never leave a Signed in factory whose key is already deleted. */
class UpdateInstanceCancellationTest {
    private val signedIn = TestData.instance()
    private val newHost = TestData.uri("https://other.example.com")

    private class GatedUpdate(
        private val inner: InstanceRepository,
        val reached: CompletableDeferred<Unit>,
        val release: CompletableDeferred<Unit>,
    ) : InstanceRepository by inner {
        override suspend fun update(instance: Instance) {
            reached.complete(Unit)
            release.await()
            inner.update(instance)
        }
    }

    @Test
    fun `cancelling an address edit after the key is deleted still signs the factory out at the new address`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.storeToken(signedIn)
            val gated = GatedUpdate(env.repository, CompletableDeferred(), CompletableDeferred())
            val accepted =
                ProbeResult.Accepted(
                    newHost,
                    HealthReport(ServerStatus.Ok, DeploymentMode.Authenticated, Exposure.Private, "1", false),
                )
            val edit = InstanceEdit("Home factory", newHost.value, null, emptyList(), accepted)

            val job = launch { UpdateInstance(gated, env.secrets, env.validate, env.locks)(signedIn.id, edit) }
            gated.reached.await()
            job.cancel()
            runCurrent()
            gated.release.complete(Unit)
            job.join()

            val stored = env.repository.get(signedIn.id)!!
            val keyPresent = env.secrets.pathsFor(signedIn.id).isNotEmpty()
            // Never Signed in without its key: either the untouched old state with the key, or the finished edit.
            (stored.status == InstanceStatus.SignedIn) shouldBe keyPresent
            stored.baseUri shouldBe newHost
            stored.status shouldBe InstanceStatus.SignedOut
        }
}
