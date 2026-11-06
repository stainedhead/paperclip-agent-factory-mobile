package com.stainedhead.paperclipmobile.application.settings

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.auth.SessionCachingSecretStore
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * With app lock on, the protected read of the active factory's board token IS the unlock: one prompt, and only an
 * attested read counts (ADR-146). Anything else falls back to the plain device-owner prompt, so the lock always asks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UnlockSessionTest {
    private val id = InstanceId("inst-1")
    private val key = SecretKey(id, SecretName.BoardToken)
    private val protected = SecretPolicy(requireUserPresence = true)
    private val open = SecretPolicy(requireUserPresence = false)

    private fun env() = Env(listOf(TestData.instance()))

    private fun Env.unlock(secrets: SecretStore = this.secrets) = UnlockSession(UnlockApp(biometrics), secrets, signedOut)

    private suspend fun Env.store(policy: SecretPolicy) {
        secrets.put(key, Secret.of(TestData.TOKEN), policy)
    }

    @Test
    fun `a protected token that authenticates unlocks with one prompt and no separate unlock prompt`() =
        runTest {
            val env = env()
            env.store(protected)

            env.unlock()(id) shouldBe BiometricResult.Approved

            env.biometrics.prompts shouldBe emptyList()
            env.secrets.attestedReads shouldBe 1
        }

    @Test
    fun `an unprotected token falls back to the plain unlock prompt`() =
        runTest {
            val env = env()
            env.store(open)

            env.unlock()(id) shouldBe BiometricResult.Approved

            env.biometrics.prompts shouldBe listOf(AuthReason.UnlockApp)
        }

    @Test
    fun `no active factory and a missing token fall back to the plain unlock prompt`() =
        runTest {
            val env = env()

            env.unlock()(null) shouldBe BiometricResult.Approved
            env.unlock()(id) shouldBe BiometricResult.Approved

            env.biometrics.prompts shouldBe listOf(AuthReason.UnlockApp, AuthReason.UnlockApp)
            env.secrets.attestedReads shouldBe 1
        }

    @Test
    fun `the fallback prompt can still refuse, and then the app stays locked`() =
        runTest {
            val env = env()
            env.store(open)
            env.biometrics.result = BiometricResult.Cancelled

            env.unlock()(id) shouldBe BiometricResult.Cancelled
        }

    @Test
    fun `a declined key prompt leaves the app locked and shows no second prompt`() =
        runTest {
            val env = env()
            env.store(protected)
            env.secrets.denyReads = true

            env.unlock()(id) shouldBe BiometricResult.Cancelled

            env.biometrics.prompts shouldBe emptyList()
        }

    @Test
    fun `an invalidated key marks the factory for re-pairing and the lock still asks the owner`() =
        runTest {
            val env = env()
            env.store(protected)
            env.secrets.invalidated = true
            env.biometrics.result = BiometricResult.Cancelled

            env.unlock()(id) shouldBe BiometricResult.Cancelled

            env.biometrics.prompts shouldBe listOf(AuthReason.UnlockApp)
            env.repository.current.single().status shouldBe InstanceStatus.NeedsRePairing
        }

    @Test
    fun `a cached token never unlocks the app, cold cache or warm`() =
        runTest {
            val env = env()
            env.store(open)
            val cache = SessionCachingSecretStore(env.secrets)
            cache.get(key, "warm up") shouldBe SecretReadResult.Found(Secret.of(TestData.TOKEN))
            env.biometrics.result = BiometricResult.Cancelled

            env.unlock(cache)(id) shouldBe BiometricResult.Cancelled
            cache.forgetAll()
            env.unlock(cache)(id) shouldBe BiometricResult.Cancelled

            env.biometrics.prompts shouldBe listOf(AuthReason.UnlockApp, AuthReason.UnlockApp)
        }

    @Test
    fun `an attested unlock warms the cache, so the first refresh does not ask again`() =
        runTest {
            val env = env()
            env.store(protected)
            val cache = SessionCachingSecretStore(env.secrets)

            env.unlock(cache)(id) shouldBe BiometricResult.Approved
            val reads = env.secrets.attestedReads
            cache.get(key, "refresh") shouldBe SecretReadResult.Found(Secret.of(TestData.TOKEN))

            reads shouldBe 1
            env.secrets.attestedReads shouldBe 1
        }

    @Test
    fun `an unlock and a refresh that start together share one device read`() =
        runTest {
            val env = env()
            env.store(protected)
            val gate = CompletableDeferred<Unit>()
            val gated =
                object : SecretStore by env.secrets {
                    override suspend fun readAttested(
                        key: SecretKey,
                        reason: String,
                    ) = env.secrets.readAttested(key, reason).also { gate.await() }
                }
            val cache = SessionCachingSecretStore(gated)

            val unlocked = async { env.unlock(cache)(id) }
            runCurrent()
            val refresh = async { cache.get(key, "refresh") }
            runCurrent()
            gate.complete(Unit)

            unlocked.await() shouldBe BiometricResult.Approved
            refresh.await() shouldBe SecretReadResult.Found(Secret.of(TestData.TOKEN))
            env.secrets.attestedReads shouldBe 1
        }
}
