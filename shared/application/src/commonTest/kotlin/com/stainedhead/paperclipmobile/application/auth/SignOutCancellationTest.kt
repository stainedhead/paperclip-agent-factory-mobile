@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.domain.InstanceStatus
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
}
