@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.instance.SwitchCompany
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** F-R-1 (ADR-033): a use case that changes one field never writes back a stale copy of the whole factory. */
class StaleWriteTest {
    private val signedIn = TestData.instance()
    private val newHost = TestData.uri("https://other.example.com")

    /** An address edit as ADR-012 leaves it, written from the copy the editor read first, with [gate] in between. */
    private fun TestScope.addressEdit(
        env: Env,
        gate: CompletableDeferred<Unit>,
    ) = launch {
        env.locks.withLock(signedIn.id) {
            val seen = env.repository.get(signedIn.id)!!
            gate.await()
            env.repository.update(
                seen.copy(
                    baseUri = newHost,
                    boardKeyId = null,
                    keyExpiresAt = null,
                    status = InstanceStatus.SignedOut,
                    displayName = "Edited meanwhile",
                ),
            )
        }
    }

    private fun Env.assertEditSurvived() {
        val stored = repository.current.single()
        stored.baseUri shouldBe newHost
        stored.status shouldBe InstanceStatus.SignedOut
        stored.boardKeyId shouldBe null
        stored.displayName shouldBe "Edited meanwhile"
    }

    @Test
    fun `switching company during an address edit keeps both changes`() =
        runTest {
            val env = Env(listOf(signedIn))
            val gate = CompletableDeferred<Unit>()
            val edit = addressEdit(env, gate)
            runCurrent()

            val switch = launch { SwitchCompany(env.repository, env.locks)(signedIn.id, CompanyId("c2")) }
            runCurrent()
            gate.complete(Unit)
            joinAll(edit, switch)

            env.assertEditSurvived()
            env.repository.current
                .single()
                .lastCompanyId shouldBe CompanyId("c2")
        }

    @Test
    fun `recording the connection time during an address edit keeps both changes`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.auth.meResult = DomainResult.Success(TestData.access())
            val gate = CompletableDeferred<Unit>()
            val edit = addressEdit(env, gate)
            runCurrent()

            val load =
                launch {
                    LoadInstanceAccess(env.repository, env.auth, env.signedOut, env.clock, env.connectivity, env.locks)(
                        signedIn.id,
                    )
                }
            runCurrent()
            gate.complete(Unit)
            joinAll(edit, load)

            env.assertEditSurvived()
            env.repository.current
                .single()
                .lastConnectedAt shouldBe env.clock.now()
        }

    /** Holds the secure store read, as a biometric prompt would, while the address is edited. */
    private class SlowStore(
        private val inner: SecretStore,
        val reading: CompletableDeferred<Unit>,
        val release: CompletableDeferred<Unit>,
    ) : SecretStore by inner {
        override suspend fun get(
            key: SecretKey,
            reason: String,
        ): SecretReadResult {
            reading.complete(Unit)
            release.await()
            return inner.get(key, reason)
        }
    }

    @Test
    fun `an invalidated key found after an address edit leaves the edit as it is`() =
        runTest {
            val env = Env(listOf(signedIn))
            env.storeToken()
            env.secrets.invalidated = true
            val store = SlowStore(env.secrets, CompletableDeferred(), CompletableDeferred())

            val check = launch { CheckCredentials(env.repository, store, env.locks)(signedIn.id) }
            store.reading.await()
            val gate = CompletableDeferred<Unit>().also { it.complete(Unit) }
            addressEdit(env, gate).join()
            store.release.complete(Unit)
            check.join()

            env.assertEditSurvived()
        }
}
