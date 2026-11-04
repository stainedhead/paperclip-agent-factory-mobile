@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

internal fun TestScope.workEnv(role: Role = Role.Owner): Env {
    val env = Env(listOf(TestData.instance())) { testScheduler.currentTime }
    env.auth.meResult = DomainResult.Success(TestData.access(role))
    return env
}

internal fun TestScope.agentsHolder(env: Env) =
    AgentsStateHolder(
        env.agentList,
        env.connectivity,
        env.clock,
        CoroutineScope(StandardTestDispatcher(testScheduler)),
        CompanyTarget("inst-1", TestData.acme.value),
    )

private fun AgentsState.row(id: String) = sections.flatMap { it.rows }.first { it.id == id }

class AgentsListTest {
    @Test
    fun `FR-070 agents are grouped with chips for status lifecycle and budget`() =
        runTest {
            val env = workEnv()
            env.agents.store[AgentId("agent-cto")] =
                env.agents.store
                    .getValue(AgentId("agent-cto"))
                    .copy(lifecycleState = "pausing")
            val holder = agentsHolder(env)
            advanceUntilIdle()

            val state = holder.state.value
            state.phase shouldBe DetailPhase.Content
            state.sections.map { it.group } shouldBe
                listOf(AgentGroupUi.NeedsAttention, AgentGroupUi.Running, AgentGroupUi.Paused, AgentGroupUi.Idle)
            state.sections[0].rows.map { it.name } shouldBe listOf("Engineer 2", "QA agent")
            state.row("agent-eng-2").isBudgetPaused shouldBe true
            state.row("agent-eng-2").budgetPercentUsed shouldBe 100
            state.row("agent-qa").status shouldBe AgentStatusUi.Error
            state.row("agent-cto").lifecycleState shouldBe "pausing"
            state.row("agent-ceo").lifecycleState shouldBe null
            state.updatedAtEpochMillis shouldBe env.clock.now().toEpochMilliseconds()
            state.refreshIntervalSeconds shouldBe 15
        }

    @Test
    fun `a row shows the run its agent has in progress with the issue it works on`() =
        runTest {
            val env = workEnv()
            val holder = agentsHolder(env)
            advanceUntilIdle()

            val state = holder.state.value
            val run = state.row("agent-eng-1").currentRun
            run?.runId shouldBe "run-live"
            run?.issueIdentifier shouldBe "PAP-1182"
            state.row("agent-cto").currentRun shouldBe null
            state.row("agent-qa").currentRun shouldBe null
        }

    @Test
    fun `setting a budget is the owner's and an operator is told why not`() =
        runTest {
            val owner = workEnv(Role.Owner)
            val ownerHolder = agentsHolder(owner)
            val operator = workEnv(Role.Operator)
            val operatorHolder = agentsHolder(operator)
            advanceUntilIdle()

            ownerHolder.state.value.canSetBudget shouldBe true
            ownerHolder.state.value.budgetDisabledReason shouldBe null
            operatorHolder.state.value.canSetBudget shouldBe false
            operatorHolder.state.value.budgetDisabledReason shouldBe ActionsDisabledReason.OwnerRole
        }

    @Test
    fun `after run now the list is read again so the new run shows`() =
        runTest {
            val env = workEnv()
            val holder = agentsHolder(env)
            advanceUntilIdle()
            val before = env.agents.count("list")

            holder.onIntent(AgentsIntent.RunNowTapped("agent-eng-1"))
            advanceUntilIdle()

            env.agents.count("list") shouldBe before + 1
        }

    @Test
    fun `row actions follow the agent's state for an operator`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = agentsHolder(env)
            advanceUntilIdle()

            val state = holder.state.value
            state.row("agent-eng-1").actions shouldBe listOf(AgentActionUi.Pause, AgentActionUi.RunNow)
            state.row("agent-design").actions shouldBe listOf(AgentActionUi.Resume)
            state.row("agent-eng-2").actions shouldBe emptyList()
            state.row("agent-qa").actions shouldBe listOf(AgentActionUi.RunNow)
            state.actionsDisabledReason shouldBe null
        }

    @Test
    fun `a viewer has no row actions and sees why`() =
        runTest {
            val env = workEnv(Role.Viewer)
            val holder = agentsHolder(env)
            advanceUntilIdle()

            holder.state.value.sections
                .flatMap { it.rows }
                .all { it.actions.isEmpty() } shouldBe true
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `acceptance 11 offline keeps the list but disables every action with a reason`() =
        runTest {
            val env = workEnv()
            val holder = agentsHolder(env)
            advanceUntilIdle()
            env.connectivity.setOnline(false)
            advanceUntilIdle()

            holder.state.value.phase shouldBe DetailPhase.Content
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.Offline
            holder.state.value.sections
                .flatMap { it.rows }
                .all { it.actions.isEmpty() } shouldBe true
            holder.onIntent(AgentsIntent.PauseTapped("agent-eng-1"))
            advanceUntilIdle()
            holder.state.value.confirmPauseAgentId shouldBe null
        }

    @Test
    fun `a failed load is a failed screen with an error key`() =
        runTest {
            val env = workEnv()
            env.agents.failures["list"] = DomainError.Forbidden
            val holder = agentsHolder(env)
            advanceUntilIdle()

            holder.state.value.phase shouldBe DetailPhase.Failed
            holder.state.value.error shouldBe ErrorUi(ErrorKey.NoPermission)
        }
}

class AgentsActionsTest {
    @Test
    fun `FR-071 pause asks for confirmation then device authentication then calls once`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = agentsHolder(env)
            advanceUntilIdle()

            holder.onIntent(AgentsIntent.PauseTapped("agent-eng-1"))
            advanceUntilIdle()
            holder.state.value.confirmPauseAgentId shouldBe "agent-eng-1"
            env.agents.count("pause") shouldBe 0
            env.biometrics.calls shouldBe 0

            holder.onIntent(AgentsIntent.PauseConfirmed)
            advanceUntilIdle()

            env.biometrics.calls shouldBe 1
            env.agents.count("pause") shouldBe 1
            holder.state.value.confirmPauseAgentId shouldBe null
            holder.state.value
                .row("agent-eng-1")
                .status shouldBe AgentStatusUi.Paused
            holder.state.value.feedback shouldBe null
        }

    @Test
    fun `acceptance 9 a cancelled device authentication means no gateway call and a feedback key`() =
        runTest {
            val env = workEnv()
            env.biometrics.result = BiometricResult.Cancelled
            val holder = agentsHolder(env)
            advanceUntilIdle()

            holder.onIntent(AgentsIntent.PauseTapped("agent-eng-1"))
            holder.onIntent(AgentsIntent.PauseConfirmed)
            advanceUntilIdle()

            env.agents.count("pause") shouldBe 0
            holder.state.value.feedback shouldBe
                FeedbackUi(FeedbackKind.DeviceAuth, deviceAuth = DeviceAuthIssue.Cancelled)
            holder.state.value
                .row("agent-eng-1")
                .status shouldBe AgentStatusUi.Running
        }

    @Test
    fun `cancelling the confirmation does nothing`() =
        runTest {
            val env = workEnv()
            val holder = agentsHolder(env)
            advanceUntilIdle()

            holder.onIntent(AgentsIntent.PauseTapped("agent-eng-1"))
            holder.onIntent(AgentsIntent.PauseCancelled)
            holder.onIntent(AgentsIntent.PauseConfirmed)
            advanceUntilIdle()

            env.agents.count("pause") shouldBe 0
            env.biometrics.calls shouldBe 0
        }

    @Test
    fun `a second confirm while the first is in flight is ignored`() =
        runTest {
            val env = workEnv()
            val holder = agentsHolder(env)
            advanceUntilIdle()

            holder.onIntent(AgentsIntent.PauseTapped("agent-eng-1"))
            holder.onIntent(AgentsIntent.PauseConfirmed)
            holder.onIntent(AgentsIntent.PauseConfirmed)
            advanceUntilIdle()

            env.agents.count("pause") shouldBe 1
        }

    @Test
    fun `a viewer's pause tap does nothing`() =
        runTest {
            val env = workEnv(Role.Viewer)
            val holder = agentsHolder(env)
            advanceUntilIdle()

            holder.onIntent(AgentsIntent.PauseTapped("agent-eng-1"))
            holder.onIntent(AgentsIntent.PauseConfirmed)
            advanceUntilIdle()

            env.agents.count("pause") shouldBe 0
            env.biometrics.calls shouldBe 0
        }

    @Test
    fun `FR-071 resume and run now call once each with no device authentication`() =
        runTest {
            val env = workEnv()
            val holder = agentsHolder(env)
            advanceUntilIdle()

            holder.onIntent(AgentsIntent.ResumeTapped("agent-design"))
            advanceUntilIdle()
            holder.onIntent(AgentsIntent.RunNowTapped("agent-cto"))
            advanceUntilIdle()

            env.agents.count("resume") shouldBe 1
            env.agents.count("invokeHeartbeat") shouldBe 1
            env.biometrics.calls shouldBe 0
            holder.state.value
                .row("agent-design")
                .status shouldBe AgentStatusUi.Idle
        }

    @Test
    fun `acceptance 10 a timeout on pause is sent once and the list shows the true state`() =
        runTest {
            val env = workEnv()
            env.agents.failures["pause"] = DomainError.Timeout
            val holder = agentsHolder(env)
            advanceUntilIdle()

            holder.onIntent(AgentsIntent.PauseTapped("agent-eng-1"))
            holder.onIntent(AgentsIntent.PauseConfirmed)
            advanceUntilIdle()

            env.agents.count("pause") shouldBe 1
            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Reconciled, error = ErrorUi(ErrorKey.Timeout))
            holder.state.value
                .row("agent-eng-1")
                .status shouldBe AgentStatusUi.Running
        }

    @Test
    fun `a message can be dismissed and refresh reads again`() =
        runTest {
            val env = workEnv()
            env.agents.failures["resume"] = DomainError.Forbidden
            val holder = agentsHolder(env)
            advanceUntilIdle()
            holder.onIntent(AgentsIntent.ResumeTapped("agent-design"))
            advanceUntilIdle()
            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Failed, error = ErrorUi(ErrorKey.NoPermission))

            holder.onIntent(AgentsIntent.MessageDismissed)
            holder.onIntent(AgentsIntent.Refresh)
            advanceUntilIdle()

            holder.state.value.feedback shouldBe null
            env.agents.count("list") shouldBe 2
        }
}
