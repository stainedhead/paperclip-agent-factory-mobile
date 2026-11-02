package com.stainedhead.paperclipmobile.application.agent

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.InvokeOutcome
import com.stainedhead.paperclipmobile.domain.RunStatus
import com.stainedhead.paperclipmobile.testing.AgentFixtures
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** F-A-6: after a timeout, run now compares the agent's runs with the ones that existed before the tap. */
class InvokeHeartbeatReconcileTest {
    private val engineer = AgentId("agent-eng-1")

    private suspend fun Env.invoke() = InvokeHeartbeat(agents, runs)(context(), agents.store.getValue(engineer))

    @Test
    fun `heartbeat_timeout_with_preexisting_live_run_is_not_reported_started`() =
        runTest {
            val env = Env()
            env.agents.failures["invokeHeartbeat"] = DomainError.Timeout

            val result = env.invoke()

            result shouldBe MutationResult.Unknown(DomainError.Timeout)
            env.agents.count("invokeHeartbeat") shouldBe 1
        }

    @Test
    fun `heartbeat_timeout_without_new_run_is_not_reported_skipped`() =
        runTest {
            val env = Env()
            env.runs.store.clear()
            env.agents.failures["invokeHeartbeat"] = DomainError.Timeout

            env.invoke() shouldBe MutationResult.Unknown(DomainError.Timeout)
        }

    @Test
    fun `heartbeat_timeout_with_new_live_run_is_reported_started`() =
        runTest {
            val env = Env()
            env.agents.failures["invokeHeartbeat"] = DomainError.Timeout
            env.agents.onInvoke = {
                val started = AgentFixtures.run("run-new", RunStatus.Queued, engineer, null)
                env.runs.store[started.id] = started
            }

            val result = env.invoke()

            val reconciled = result.shouldBeInstanceOf<MutationResult.Reconciled<InvokeOutcome>>()
            reconciled.error shouldBe DomainError.Timeout
            reconciled.current
                .shouldBeInstanceOf<InvokeOutcome.Started>()
                .run.id.value shouldBe "run-new"
        }

    @Test
    fun `a timeout when the runs before the tap could not be read is unknown even if they read later`() =
        runTest {
            val env = Env()
            env.agents.failures["invokeHeartbeat"] = DomainError.Timeout
            env.runs.failures["forAgent"] = DomainError.Unreachable
            env.agents.onInvoke = { env.runs.failures.clear() }

            env.invoke() shouldBe MutationResult.Unknown(DomainError.Timeout)
        }

    @Test
    fun `a timeout whose runs cannot be read afterwards is reported as the original error`() =
        runTest {
            val env = Env()
            env.agents.failures["invokeHeartbeat"] = DomainError.Timeout
            env.agents.onInvoke = { env.runs.failures["forAgent"] = DomainError.Unreachable }
            env.runs.store.clear()

            env.invoke() shouldBe MutationResult.Failed(DomainError.Timeout)
        }

    @Test
    fun `an error that says the call did not happen is not reconciled`() =
        runTest {
            val env = Env()
            env.agents.failures["invokeHeartbeat"] = DomainError.Forbidden

            env.invoke() shouldBe MutationResult.Failed(DomainError.Forbidden)
            env.runs.count("forAgent") shouldBe 1
        }
}
