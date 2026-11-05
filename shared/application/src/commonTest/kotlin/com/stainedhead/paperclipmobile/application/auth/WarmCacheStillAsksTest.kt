package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.agent.PauseAgent
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.AgentFixtures
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * With the board token in memory, a destructive action still goes through the `Biometrics` port before the use case
 * runs, and a refusal never reaches the gateway (AGENTS.md Security Rules, ADR-145).
 */
class WarmCacheStillAsksTest {
    private suspend fun warmEnv(): Env {
        val env = Env(listOf(TestData.instance()))
        env.storeToken()
        val cache = SessionCachingSecretStore(env.secrets)
        cache.get(SecretKey(env.scope.instanceId, SecretName.BoardToken), "warm up")
        return env
    }

    @Test
    fun `every destructive action asks the device owner even with the token in memory`() =
        runTest {
            val env = warmEnv()

            listOf(Action.PauseAgent, Action.CancelRun, Action.RejectApproval, Action.RaiseAgentBudget).forEach {
                env.deviceAuth(it) shouldBe BiometricResult.Approved
            }

            env.biometrics.prompts shouldBe List(4) { AuthReason.ConfirmDestructiveAction }
        }

    @Test
    fun `a refused prompt stops the use case before the gateway`() =
        runTest {
            val env = warmEnv()
            env.biometrics.result = BiometricResult.Cancelled
            val agent = AgentFixtures.agents()[2]

            val result = PauseAgent(env.agents, env.deviceAuth)(env.context(), agent)

            result.shouldBeInstanceOf<MutationResult.NotAuthorized>()
            env.agents.count("pause") shouldBe 0
        }
}
