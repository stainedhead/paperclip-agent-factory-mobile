package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.domain.AuthMethod
import com.stainedhead.paperclipmobile.domain.BoardKey
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class KeyUseCasesTest {
    private val unpaired =
        TestData
            .instance(
                status = InstanceStatus.SignedOut,
            ).copy(boardKeyId = null, keyExpiresAt = null)
    private val signedIn = TestData.instance()

    private fun Env.manual() = SaveManualBoardKey(repository, auth, enrollment)

    @Test
    fun `FR-021 a valid pasted key is validated with me stored and the caller's copy is wiped`() =
        runTest {
            val env = Env(listOf(unpaired))
            val key = Secret.of(TestData.TOKEN)

            val result = env.manual()(unpaired.id, key)

            (result as SignInResult.SignedIn).keyId shouldBe TestData.KEY_ID
            env.auth.count("meWithKey") shouldBe 1
            env.auth.count("me") shouldBe 0
            env.secrets.pathsFor(unpaired.id) shouldBe setOf("instance/inst-1/boardToken")
            env.repository.get(unpaired.id)?.authMethod shouldBe AuthMethod.ManualBoardKey
            env.repository.get(unpaired.id)?.status shouldBe InstanceStatus.SignedIn
            key.isWiped shouldBe true
        }

    @Test
    fun `F-C-8 the pasted key is validated against the entered address only`() =
        runTest {
            val other = TestData.instance(id = "inst-2", name = "Other", uri = "https://other.example.com")
            val env = Env(listOf(unpaired, other))

            env.manual()(unpaired.id, Secret.of(TestData.TOKEN)).shouldBeInstanceOf<SignInResult.SignedIn>()

            env.auth.authenticatedCalls
                .filter { it.token == TestData.TOKEN }
                .mapNotNull { it.host }
                .toSet() shouldBe setOf(unpaired.baseUri)
        }

    @Test
    fun `FR-021 a key the server rejects stores nothing`() =
        runTest {
            val env = Env(listOf(unpaired))
            env.auth.meWithKeyResult = DomainResult.Failure(DomainError.Unauthorized)
            val key = Secret.of(TestData.TOKEN)

            env.manual()(unpaired.id, key) shouldBe SignInResult.Failed(DomainError.Unauthorized)

            env.secrets.putCalls shouldBe 0
            env.secrets.paths shouldBe emptySet()
            env.repository.updateCalls shouldBe 0
            key.isWiped shouldBe true
        }

    @Test
    fun `FR-021 text that is not a board key never leaves the phone`() =
        runTest {
            val env = Env(listOf(unpaired))

            env.manual()(unpaired.id, Secret.of("not-a-key")) shouldBe SignInResult.InvalidKey
            env.manual()(InstanceId("zzz"), Secret.of(TestData.TOKEN)) shouldBe SignInResult.NotFound
            env.auth.count("meWithKey") shouldBe 0
            env.secrets.putCalls shouldBe 0
        }

    @Test
    fun `FR-021 a failing secret store is reported`() =
        runTest {
            val env = Env(listOf(unpaired))
            env.secrets.failWrites = true

            env.manual()(unpaired.id, Secret.of(TestData.TOKEN)) shouldBe SignInResult.StorageFailed
        }

    @Test
    fun `FR-024 sign out revokes once deletes the secrets and keeps the configuration`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.storeToken()

            val result = SignOutInstance(env.repository, env.secrets, env.revoker)(signedIn.id)

            result shouldBe SignOutResult.SignedOut(RevokeOutcome.Revoked)
            env.auth.count("revokeCurrentKey") shouldBe 1
            env.secrets.pathsFor(signedIn.id) shouldBe emptySet()
            val stored = env.repository.get(signedIn.id)!!
            stored.status shouldBe InstanceStatus.SignedOut
            stored.boardKeyId shouldBe null
            stored.displayName shouldBe "Home factory"
        }

    @Test
    fun `FR-024 sign out while the server is unreachable still deletes locally and says the revoke is unconfirmed`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.storeToken()
            env.auth.revokeCurrentResult = DomainResult.Failure(DomainError.Timeout)

            val result = SignOutInstance(env.repository, env.secrets, env.revoker)(signedIn.id)

            result shouldBe SignOutResult.SignedOut(RevokeOutcome.Unconfirmed)
            env.auth.count("revokeCurrentKey") shouldBe 1
            env.secrets.paths shouldBe emptySet()
        }

    @Test
    fun `FR-023 a 401 on revoke means the key was already unusable`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.auth.revokeCurrentResult = DomainResult.Failure(DomainError.Unauthorized)

            SignOutInstance(env.repository, env.secrets, env.revoker)(signedIn.id) shouldBe
                SignOutResult.SignedOut(RevokeOutcome.AlreadyInvalid)
        }

    @Test
    fun `signing out of a factory that is already signed out does not call the server`() =
        runTest {
            val env = Env(listOf(unpaired))
            val signOut = SignOutInstance(env.repository, env.secrets, env.revoker)

            signOut(unpaired.id) shouldBe SignOutResult.SignedOut(RevokeOutcome.NotAttempted)
            signOut(InstanceId("zzz")) shouldBe SignOutResult.NotFound
            env.auth.count("revokeCurrentKey") shouldBe 0
        }

    @Test
    fun `FR-024 sign out everywhere signs out every factory and counts unconfirmed revokes`() =
        runTest {
            val env = Env(listOf(signedIn, TestData.instance("b", "Cloud", sortOrder = 1)))
            env.auth.revokeCurrentResult = DomainResult.Failure(DomainError.Unreachable)

            val result = SignOutEverywhere(env.repository, SignOutInstance(env.repository, env.secrets, env.revoker))()

            result shouldBe SignOutEverywhereResult(signedOut = 2, unconfirmed = 2)
            env.repository.current.all { it.status == InstanceStatus.SignedOut } shouldBe true
        }

    @Test
    fun `FR-025 the key list marks this phone's key and hides revoked keys`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.auth.keysResult =
                DomainResult.Success(
                    TestData.boardKeys() + BoardKey("old", "old", null, null, null, TestData.epoch),
                )

            val keys = ListBoardKeys(env.repository, env.auth, env.signedOut)(signedIn.id)

            val list = (keys as DomainResult.Success).value
            list.map { it.id to it.isCurrentDevice } shouldBe listOf(TestData.KEY_ID to true, "key-laptop" to false)
        }

    @Test
    fun `FR-025 the key list is a read so it retries and a 401 marks the factory signed out`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.auth.keysResult = DomainResult.Failure(DomainError.Timeout)
            val list = ListBoardKeys(env.repository, env.auth, env.signedOut)

            list(signedIn.id) shouldBe DomainResult.Failure(DomainError.Timeout)
            env.auth.count("listBoardKeys") shouldBe 4

            env.auth.keysResult = DomainResult.Failure(DomainError.Unauthorized)
            list(signedIn.id) shouldBe DomainResult.Failure(DomainError.Unauthorized)
            env.repository.get(signedIn.id)?.status shouldBe InstanceStatus.SignedOut
            list(InstanceId("zzz")) shouldBe DomainResult.Failure(DomainError.NotFound)
        }

    @Test
    fun `FR-025 revoking another key calls the server once and revoking this phone's key signs out`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.storeToken()
            val revoke =
                RevokeBoardKey(
                    env.repository,
                    env.auth,
                    SignOutInstance(env.repository, env.secrets, env.revoker),
                    env.signedOut,
                )

            revoke(signedIn.id, "key-laptop") shouldBe RevokeKeyResult.Revoked
            env.auth.count("revokeBoardKey") shouldBe 1
            env.auth.count("revokeCurrentKey") shouldBe 0

            revoke(signedIn.id, TestData.KEY_ID) shouldBe RevokeKeyResult.SignedOutThisDevice(RevokeOutcome.Revoked)
            env.auth.count("revokeBoardKey") shouldBe 1
            env.secrets.paths shouldBe emptySet()
        }

    @Test
    fun `FR-025 a failed revoke is attempted once and reported`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.auth.revokeKeyResult = DomainResult.Failure(DomainError.Timeout)
            val revoke =
                RevokeBoardKey(
                    env.repository,
                    env.auth,
                    SignOutInstance(env.repository, env.secrets, env.revoker),
                    env.signedOut,
                )

            revoke(signedIn.id, "key-laptop") shouldBe RevokeKeyResult.Failed(DomainError.Timeout)
            env.auth.count("revokeBoardKey") shouldBe 1

            env.auth.revokeKeyResult = DomainResult.Failure(DomainError.Unauthorized)
            revoke(signedIn.id, "key-laptop") shouldBe RevokeKeyResult.Failed(DomainError.Unauthorized)
            env.repository.get(signedIn.id)?.status shouldBe InstanceStatus.SignedOut
            revoke(InstanceId("zzz"), "k") shouldBe RevokeKeyResult.Failed(DomainError.NotFound)
        }
}
