package com.stainedhead.paperclipmobile.application.agent

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.issue.LoadIssueRefs
import com.stainedhead.paperclipmobile.domain.Agent
import com.stainedhead.paperclipmobile.domain.AgentGroup
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.AgentStatus
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InvokeOutcome
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.ResolutionIssue
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private fun <T> DomainResult<T>.value(): T = (this as DomainResult.Success).value

private fun Env.agent(id: String): Agent = agents.store.getValue(AgentId(id))

class ListAgentsTest {
    @Test
    fun `FR-070 agents are grouped with needs attention first and names sorted`() =
        runTest {
            val env = Env()

            val roster = ListAgents(env.agents, env.runs, LoadIssueRefs(env.issues))(env.scope).value()

            roster.sections.map { it.group } shouldBe
                listOf(AgentGroup.NeedsAttention, AgentGroup.Running, AgentGroup.Paused, AgentGroup.Idle)
            roster.sections[0].agents.map { it.name } shouldBe listOf("Engineer 2", "QA agent")
            roster.sections[3].agents.map { it.name } shouldBe listOf("CEO", "CTO")
        }

    @Test
    fun `each agent's run in progress comes with its issue and the runs are best effort`() =
        runTest {
            val env = Env()

            val roster = ListAgents(env.agents, env.runs, LoadIssueRefs(env.issues))(env.scope).value()
            roster.liveRuns.map { it.id.value } shouldBe listOf("run-live")
            roster.issueRefs.getValue(IssueId("pap-1182")).identifier shouldBe "PAP-1182"

            env.runs.failures["live"] = DomainError.ServerError
            val without = ListAgents(env.agents, env.runs, LoadIssueRefs(env.issues))(env.scope).value()
            without.liveRuns shouldBe emptyList()
            without.agents.size shouldBe 6
        }

    @Test
    fun `a failure is retried as a read and reported`() =
        runTest {
            val env = Env()
            env.agents.failures["list"] = DomainError.ServerError

            (
                ListAgents(
                    env.agents,
                    env.runs,
                    LoadIssueRefs(env.issues),
                )(env.scope) as DomainResult.Failure
            ).error shouldBe
                DomainError.ServerError
            env.agents.count("list") shouldBe 4
        }
}

class LoadAgentTest {
    @Test
    fun `FR-072 the detail has the manager the current issue and recent runs`() =
        runTest {
            val env = Env()

            val view = LoadAgent(env.agents, env.runs, env.issues)(env.scope, AgentId("agent-eng-1")).value()

            view.agent.name shouldBe "Engineer 1"
            view.reportsToName shouldBe "CTO"
            view.currentRun?.id?.value shouldBe "run-live"
            view.currentIssue?.identifier shouldBe "PAP-1182"
            view.recentRuns.size shouldBe 2
            view.issueRefs.getValue(IssueId("pap-1182")).identifier shouldBe "PAP-1182"
        }

    @Test
    fun `an idle agent has no current run or issue and the extras are best effort`() =
        runTest {
            val env = Env()
            env.runs.failures["forAgent"] = DomainError.ServerError

            val view = LoadAgent(env.agents, env.runs, env.issues)(env.scope, AgentId("agent-cto")).value()

            view.currentRun shouldBe null
            view.currentIssue shouldBe null
            view.recentRuns shouldBe emptyList()
            view.reportsToName shouldBe "CEO"
        }

    @Test
    fun `a missing agent is not found`() =
        runTest {
            val env = Env()

            (
                LoadAgent(
                    env.agents,
                    env.runs,
                    env.issues,
                )(env.scope, AgentId("nope")) as DomainResult.Failure
            ).error shouldBe
                DomainError.NotFound
        }
}

class PauseAgentTest {
    private fun Env.pause() = PauseAgent(agents, deviceAuth)

    @Test
    fun `FR-071 pause asks for device authentication first and then calls the gateway once`() =
        runTest {
            val env = Env()

            val result = env.pause()(env.context(Role.Operator), env.agent("agent-eng-1"))

            (result as MutationResult.Done).value.status shouldBe AgentStatus.Paused
            env.biometrics.calls shouldBe 1
            env.agents.count("pause") shouldBe 1
        }

    @Test
    fun `every device authentication failure means no gateway call`() =
        runTest {
            listOf(
                BiometricResult.Cancelled,
                BiometricResult.Failed,
                BiometricResult.LockedOut,
                BiometricResult.Unavailable,
            ).forEach { outcome ->
                val env = Env()
                env.biometrics.result = outcome

                val result = env.pause()(env.context(), env.agent("agent-eng-1"))

                result shouldBe MutationResult.NotAuthorized(outcome)
                env.agents.count("pause") shouldBe 0
            }
        }

    @Test
    fun `a viewer is denied without a prompt and a paused agent is refused without a prompt`() =
        runTest {
            val env = Env()

            env.pause()(env.context(Role.Viewer), env.agent("agent-eng-1")) shouldBe MutationResult.Denied
            env.pause()(env.context(), env.agent("agent-design")) shouldBe
                MutationResult.Invalid(InvalidInput.WrongStatus)
            env.biometrics.calls shouldBe 0
            env.agents.count("pause") shouldBe 0
        }

    @Test
    fun `FR-096 a timeout is sent once and the agent is read again`() =
        runTest {
            val env = Env()
            env.agents.failures["pause"] = DomainError.Timeout

            val result = env.pause()(env.context(), env.agent("agent-eng-1"))

            (result as MutationResult.Reconciled).current.status shouldBe AgentStatus.Running
            env.agents.count("pause") shouldBe 1
            env.agents.count("get") shouldBe 1
        }
}

class ResumeAndRunNowTest {
    @Test
    fun `FR-071 a manually paused agent resumes without device authentication`() =
        runTest {
            val env = Env()

            val result = ResumeAgent(env.agents)(env.context(Role.Operator), env.agent("agent-design"))

            (result as MutationResult.Done).value.status shouldBe AgentStatus.Idle
            env.biometrics.calls shouldBe 0
        }

    @Test
    fun `a budget pause cannot be resumed here and a viewer is denied`() =
        runTest {
            val env = Env()

            ResumeAgent(env.agents)(env.context(), env.agent("agent-eng-2")) shouldBe
                MutationResult.Invalid(InvalidInput.WrongStatus)
            ResumeAgent(env.agents)(env.context(Role.Viewer), env.agent("agent-design")) shouldBe MutationResult.Denied
            env.agents.count("resume") shouldBe 0
        }

    @Test
    fun `FR-071 run now starts a run and a paused agent cannot be woken`() =
        runTest {
            val env = Env()

            val started = InvokeHeartbeat(env.agents, env.runs)(env.context(), env.agent("agent-cto"))
            (started as MutationResult.Done).value.shouldBeStarted()
            InvokeHeartbeat(env.agents, env.runs)(env.context(), env.agent("agent-design")) shouldBe
                MutationResult.Invalid(InvalidInput.WrongStatus)
            InvokeHeartbeat(env.agents, env.runs)(env.context(Role.Viewer), env.agent("agent-cto")) shouldBe
                MutationResult.Denied
            env.agents.count("invokeHeartbeat") shouldBe 1
        }

    @Test
    fun `a timeout on run now is sent once and the agent's runs are read before and after`() =
        runTest {
            val env = Env()
            env.agents.failures["invokeHeartbeat"] = DomainError.Timeout

            val result = InvokeHeartbeat(env.agents, env.runs)(env.context(), env.agent("agent-eng-1"))

            // Engineer 1 already had a live run before the tap, so nothing proves the tap started one (F-A-6).
            result shouldBe MutationResult.Unknown(DomainError.Timeout)
            env.agents.count("invokeHeartbeat") shouldBe 1
            env.runs.count("forAgent") shouldBe 2
        }

    private fun InvokeOutcome.shouldBeStarted() {
        (this is InvokeOutcome.Started) shouldBe true
    }
}

class SetAgentBudgetTest {
    private val fiveHundred = Money.usdCents(50_000)

    @Test
    fun `FR-071 an owner sets the monthly budget with no device authentication`() =
        runTest {
            val env = Env()

            val result = SetAgentBudget(env.agents)(env.context(Role.Owner), env.agent("agent-eng-1"), fiveHundred)

            (result as MutationResult.Done).value.monthlyBudget shouldBe fiveHundred
            env.biometrics.calls shouldBe 0
        }

    @Test
    fun `an operator and a viewer are denied with no gateway call`() =
        runTest {
            val env = Env()

            SetAgentBudget(env.agents)(env.context(Role.Operator), env.agent("agent-eng-1"), fiveHundred) shouldBe
                MutationResult.Denied
            SetAgentBudget(env.agents)(env.context(Role.Viewer), env.agent("agent-eng-1"), fiveHundred) shouldBe
                MutationResult.Denied
            env.agents.count("setBudget") shouldBe 0
        }

    @Test
    fun `a budget in another currency is refused and a timeout re-reads once`() =
        runTest {
            val env = Env()

            SetAgentBudget(env.agents)(env.context(), env.agent("agent-eng-1"), Money(100, "EUR")) shouldBe
                MutationResult.Invalid(InvalidInput.Amount(ResolutionIssue.WrongCurrency))

            env.agents.failures["setBudget"] = DomainError.Timeout
            val result = SetAgentBudget(env.agents)(env.context(), env.agent("agent-eng-1"), fiveHundred)
            (result as MutationResult.Reconciled).current.id shouldBe AgentId("agent-eng-1")
            env.agents.count("setBudget") shouldBe 1
            env.agents.count("get") shouldBe 1
        }
}
