package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.instance.RemoveInstanceHereOnly
import com.stainedhead.paperclipmobile.application.instance.RemoveInstanceResult
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Rereview1b F-R-2: a key read that stays Denied must not trap the factory on the phone. The person can remove or sign
 * out "here only": the local key and configuration go, the server is not contacted, and the server key is reported as
 * unconfirmed so the paired-devices hint appears.
 */
class LocalOnlyRemovalTest {
    private val signedIn = TestData.instance()
    private val other = TestData.instance("other", "Cloud", sortOrder = 1)

    private fun removeHere(env: Env) = RemoveInstanceHereOnly(env.repository, env.secrets, env.deviceAuth, env.locks)

    private fun signOutHere(env: Env) = SignOutInstanceHereOnly(env.repository, env.secrets, env.deviceAuth, env.locks)

    private suspend fun Env.deny() {
        storeToken()
        storeToken(other)
        secrets.denyReads = true
    }

    @Test
    fun `remove here only deletes the row and the namespace without any gateway call`() =
        runTest {
            val env = Env(listOf(signedIn, other))
            env.deny()

            removeHere(env)(signedIn.id) shouldBe RemoveInstanceResult.Removed(RevokeOutcome.Unconfirmed)

            env.repository.deletedIds shouldBe listOf(signedIn.id)
            env.secrets.pathsFor(signedIn.id) shouldBe emptySet()
            env.secrets.pathsFor(other.id).size shouldBe 1
            env.auth.count("revokeCurrentKey") shouldBe 0
            env.biometrics.calls shouldBe 1
        }

    @Test
    fun `remove here only of a signed out factory reports no server key at stake`() =
        runTest {
            val env = Env(listOf(signedIn.copy(status = InstanceStatus.SignedOut)))
            removeHere(env)(signedIn.id) shouldBe RemoveInstanceResult.Removed(RevokeOutcome.NotAttempted)
        }

    @Test
    fun `remove here only asks the device owner first and changes nothing when it is refused`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.deny()
            env.biometrics.result = BiometricResult.Cancelled

            removeHere(env)(signedIn.id) shouldBe RemoveInstanceResult.NotAuthorized(BiometricResult.Cancelled)

            env.repository.deletedIds shouldBe emptyList()
            env.secrets.pathsFor(signedIn.id).isNotEmpty() shouldBe true
        }

    @Test
    fun `remove here only of an unknown factory is NotFound`() =
        runTest {
            removeHere(Env())(InstanceId("ghost")) shouldBe RemoveInstanceResult.NotFound
        }

    @Test
    fun `sign out here only drops the key and keeps the configuration and reports the server key as unconfirmed`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.deny()

            signOutHere(env)(signedIn.id) shouldBe SignOutHereOnlyResult.SignedOut(RevokeOutcome.Unconfirmed)

            env.secrets.pathsFor(signedIn.id) shouldBe emptySet()
            env.repository.get(signedIn.id)!!.status shouldBe InstanceStatus.SignedOut
            env.auth.count("revokeCurrentKey") shouldBe 0
        }

    @Test
    fun `sign out here only needs the device owner and changes nothing without it`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.deny()
            env.biometrics.result = BiometricResult.Failed

            signOutHere(env)(signedIn.id) shouldBe SignOutHereOnlyResult.NotAuthorized(BiometricResult.Failed)

            env.repository.get(signedIn.id)!!.status shouldBe InstanceStatus.SignedIn
            env.secrets.pathsFor(signedIn.id).isNotEmpty() shouldBe true
        }

    @Test
    fun `sign out here only of an unknown factory is NotFound`() =
        runTest {
            signOutHere(Env())(InstanceId("ghost")) shouldBe SignOutHereOnlyResult.NotFound
        }

    @Test
    fun `a plain sign out with a declined read still deletes nothing`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.storeToken()
            env.auth.revokeCurrentResult = DomainResult.Failure(DomainError.DeviceAuthDeclined)

            SignOutInstance(env.repository, env.secrets, env.revoker, env.locks)(signedIn.id) shouldBe
                SignOutResult.Declined

            env.secrets.pathsFor(signedIn.id).isNotEmpty() shouldBe true
        }
}
