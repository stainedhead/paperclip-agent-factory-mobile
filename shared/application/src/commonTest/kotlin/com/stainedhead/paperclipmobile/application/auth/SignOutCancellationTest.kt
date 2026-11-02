@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** F-A-2: a cancelled sign out must still delete the local credentials (the user's emergency control). */
class SignOutCancellationTest {
    private val signedIn = TestData.instance()

    @Test
    fun `sign out cancelled during the revoke still deletes the secrets and marks the factory signed out`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.storeToken(signedIn)
            env.auth.onCall = { if (it.name == "revokeCurrentKey") awaitCancellation() }
            val signOut = SignOutInstance(env.repository, env.secrets, env.revoker)

            val job = launch { signOut(signedIn.id) }
            advanceUntilIdle()
            job.cancel()
            job.join()

            env.secrets.pathsFor(signedIn.id) shouldBe emptySet()
            val stored = env.repository.get(signedIn.id)!!
            stored.status shouldBe InstanceStatus.SignedOut
            stored.boardKeyId shouldBe null
            stored.displayName shouldBe signedIn.displayName
            stored.baseUri shouldBe signedIn.baseUri
        }

    @Test
    fun `sign out everywhere continues after one factory fails and reports it`() =
        runTest {
            val second = TestData.instance("b", "Cloud", sortOrder = 1)
            val env = Env(listOf(signedIn, second))
            env.storeToken(signedIn)
            env.storeToken(second)
            val failingSecrets = FailingDelete(env.secrets, signedIn.id)
            val signOut = SignOutInstance(env.repository, failingSecrets, env.revoker)

            val result = SignOutEverywhere(env.repository, signOut)()

            result shouldBe SignOutEverywhereResult(signedOut = 1, unconfirmed = 0, failed = listOf(signedIn.id))
            env.repository.get(second.id)?.status shouldBe InstanceStatus.SignedOut
            env.secrets.pathsFor(second.id) shouldBe emptySet()
        }

    @Test
    fun `cancelling sign out everywhere propagates the cancellation`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.storeToken(signedIn)
            env.auth.onCall = { if (it.name == "revokeCurrentKey") awaitCancellation() }

            val job =
                launch {
                    SignOutEverywhere(
                        env.repository,
                        SignOutInstance(env.repository, env.secrets, env.revoker),
                    )()
                }
            advanceUntilIdle()
            job.cancel()
            job.join()

            job.isCancelled shouldBe true
            env.secrets.pathsFor(signedIn.id) shouldBe emptySet()
        }

    private class FailingDelete(
        private val inner: SecretStore,
        private val failFor: InstanceId,
    ) : SecretStore by inner {
        override suspend fun deleteNamespace(instanceId: InstanceId) {
            if (instanceId == failFor) error("secure store unavailable")
            inner.deleteNamespace(instanceId)
        }
    }
}
