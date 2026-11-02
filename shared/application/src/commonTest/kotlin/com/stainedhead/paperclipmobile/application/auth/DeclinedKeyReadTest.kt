package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.instance.RemoveInstance
import com.stainedhead.paperclipmobile.application.instance.RemoveInstanceResult
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Review1b F-A-2: a declined key read (Face ID cancelled) or an invalidated key is not a server 401. A declined read
 * changes nothing; an invalidated one needs re-pairing; neither is reported as "already invalid on the server".
 */
class DeclinedKeyReadTest {
    private val signedIn = TestData.instance()

    private fun signOut(env: Env) = SignOutInstance(env.repository, env.secrets, env.revoker, env.locks)

    @Test
    fun `a declined key read does not mark the factory signed out`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.signedOut.noteIfUnauthorized(signedIn.id, DomainResult.Failure(DomainError.DeviceAuthDeclined))
            env.repository.get(signedIn.id)!!.status shouldBe InstanceStatus.SignedIn
        }

    @Test
    fun `an invalidated key marks the factory as needing re-pairing and not signed out`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.signedOut.noteIfUnauthorized(signedIn.id, DomainResult.Failure(DomainError.KeyInvalidated))
            env.repository.get(signedIn.id)!!.status shouldBe InstanceStatus.NeedsRePairing
        }

    @Test
    fun `revoke with a declined prompt is Declined and not AlreadyInvalid`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.auth.revokeCurrentResult = DomainResult.Failure(DomainError.DeviceAuthDeclined)
            env.revoker.revoke(signedIn.id) shouldBe RevokeOutcome.Declined
        }

    @Test
    fun `revoke with an invalidated key is Unconfirmed because the server key may be live`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.auth.revokeCurrentResult = DomainResult.Failure(DomainError.KeyInvalidated)
            env.revoker.revoke(signedIn.id) shouldBe RevokeOutcome.Unconfirmed
        }

    @Test
    fun `sign out with a declined prompt keeps the local key and the signed in state`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.storeToken()
            env.auth.revokeCurrentResult = DomainResult.Failure(DomainError.DeviceAuthDeclined)

            signOut(env)(signedIn.id) shouldBe SignOutResult.Declined

            env.secrets.pathsFor(signedIn.id).isNotEmpty() shouldBe true
            env.repository.get(signedIn.id)!!.status shouldBe InstanceStatus.SignedIn
        }

    @Test
    fun `sign out everywhere counts a declined factory as not signed out`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.storeToken()
            env.auth.revokeCurrentResult = DomainResult.Failure(DomainError.DeviceAuthDeclined)

            val result = SignOutEverywhere(env.repository, signOut(env))()

            result shouldBe SignOutEverywhereResult(signedOut = 0, unconfirmed = 0, failed = listOf(signedIn.id))
        }

    @Test
    fun `removing a factory with a declined key prompt changes nothing`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.storeToken()
            env.auth.revokeCurrentResult = DomainResult.Failure(DomainError.DeviceAuthDeclined)
            val remove = RemoveInstance(env.repository, env.secrets, env.revoker, env.deviceAuth, env.locks)

            remove(signedIn.id) shouldBe RemoveInstanceResult.NotAuthorized(BiometricResult.Cancelled)

            env.repository.deletedIds shouldBe emptyList()
            env.secrets.pathsFor(signedIn.id).isNotEmpty() shouldBe true
        }
}
