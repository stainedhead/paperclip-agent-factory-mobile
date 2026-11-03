@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

internal fun TestScope.detailHolder(
    env: Env,
    agentId: String = "agent-eng-1",
) = AgentDetailStateHolder(
    env.agentDetail,
    env.connectivity,
    env.clock,
    CoroutineScope(StandardTestDispatcher(testScheduler)),
    CompanyTarget("inst-1", TestData.acme.value),
    agentId,
)

class AgentDetailLoadTest {
    @Test
    fun `FR-072 the detail shows role manager current issue recent runs and month spend`() =
        runTest {
            val env = workEnv()
            val holder = detailHolder(env)
            advanceUntilIdle()

            val state = holder.state.value
            state.phase shouldBe DetailPhase.Content
            state.agent?.name shouldBe "Engineer 1"
            state.agent?.role shouldBe "engineer"
            state.reportsToName shouldBe "CTO"
            state.currentRun?.runId shouldBe "run-live"
            state.currentIssue?.identifier shouldBe "PAP-1182"
            state.recentRuns.size shouldBe 2
            state.currentRun?.issueIdentifier shouldBe "PAP-1182"
            state.recentRuns.all { it.issueIdentifier == "PAP-1182" } shouldBe true
            state.monthSpend shouldBe MoneyUi(25_000, "USD")
            state.monthlyBudget shouldBe MoneyUi(100_000, "USD")
            state.budgetPercentUsed shouldBe 25
            state.refreshIntervalSeconds shouldBe 5
            state.updatedAtEpochMillis shouldBe env.clock.now().toEpochMilliseconds()
        }

    @Test
    fun `an idle agent polls slowly and a missing agent is a failed screen`() =
        runTest {
            val env = workEnv()
            val idle = detailHolder(env, "agent-cto")
            val missing = detailHolder(env, "nope")
            advanceUntilIdle()

            idle.state.value.refreshIntervalSeconds shouldBe 15
            missing.state.value.phase shouldBe DetailPhase.Failed
            missing.state.value.error shouldBe ErrorUi(ErrorKey.NotFound)
        }

    @Test
    fun `acceptance 11 offline with nothing loaded makes no call`() =
        runTest {
            val env = workEnv()
            env.connectivity.setOnline(false)
            val holder = detailHolder(env)
            advanceUntilIdle()

            holder.state.value.phase shouldBe DetailPhase.Failed
            holder.state.value.isOffline shouldBe true
            env.agents.count("get") shouldBe 0
        }
}

class AgentDetailActionsTest {
    @Test
    fun `FR-071 an owner sets the monthly budget and the detail shows it`() =
        runTest {
            val env = workEnv(Role.Owner)
            val holder = detailHolder(env)
            advanceUntilIdle()
            holder.state.value.canSetBudget shouldBe true

            holder.enterBudget("500.00")
            holder.onIntent(AgentDetailIntent.BudgetSubmitted)
            advanceUntilIdle()

            env.agents.count("setBudget") shouldBe 1
            env.biometrics.calls shouldBe 0
            holder.state.value.monthlyBudget shouldBe MoneyUi(50_000, "USD")
            holder.state.value.budgetPercentUsed shouldBe 50
        }

    @Test
    fun `acceptance 8 an operator cannot set a budget and the intent is ignored`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = detailHolder(env)
            advanceUntilIdle()
            holder.state.value.canSetBudget shouldBe false

            holder.enterBudget("500.00")
            holder.onIntent(AgentDetailIntent.BudgetSubmitted)
            advanceUntilIdle()

            env.agents.count("setBudget") shouldBe 0
        }

    @Test
    fun `a negative budget is ignored`() =
        runTest {
            val env = workEnv()
            val holder = detailHolder(env)
            advanceUntilIdle()

            holder.enterBudget("-1")
            holder.onIntent(AgentDetailIntent.BudgetSubmitted)
            advanceUntilIdle()

            env.agents.count("setBudget") shouldBe 0
        }

    @Test
    fun `FR-071 pause confirms authenticates and then calls once`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = detailHolder(env)
            advanceUntilIdle()
            holder.state.value.actions shouldBe listOf(AgentActionUi.Pause, AgentActionUi.RunNow)

            holder.onIntent(AgentDetailIntent.PauseTapped)
            advanceUntilIdle()
            holder.state.value.confirmPause shouldBe true
            holder.onIntent(AgentDetailIntent.PauseConfirmed)
            advanceUntilIdle()

            env.biometrics.calls shouldBe 1
            env.agents.count("pause") shouldBe 1
            holder.state.value.agent
                ?.status shouldBe AgentStatusUi.Paused
            holder.state.value.actions shouldBe listOf(AgentActionUi.Resume)
        }

    @Test
    fun `acceptance 9 a failed device authentication means no gateway call`() =
        runTest {
            val env = workEnv()
            env.biometrics.result = BiometricResult.LockedOut
            val holder = detailHolder(env)
            advanceUntilIdle()

            holder.onIntent(AgentDetailIntent.PauseTapped)
            holder.onIntent(AgentDetailIntent.PauseConfirmed)
            advanceUntilIdle()

            env.agents.count("pause") shouldBe 0
            holder.state.value.feedback shouldBe
                FeedbackUi(FeedbackKind.DeviceAuth, deviceAuth = DeviceAuthIssue.LockedOut)
        }

    @Test
    fun `a viewer has no actions and a second confirm in flight is ignored`() =
        runTest {
            val viewer = workEnv(Role.Viewer)
            val view = detailHolder(viewer)
            advanceUntilIdle()
            view.state.value.actions shouldBe emptyList()
            view.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole

            val env = workEnv()
            val holder = detailHolder(env)
            advanceUntilIdle()
            holder.onIntent(AgentDetailIntent.PauseTapped)
            holder.onIntent(AgentDetailIntent.PauseConfirmed)
            holder.onIntent(AgentDetailIntent.PauseConfirmed)
            advanceUntilIdle()
            env.agents.count("pause") shouldBe 1
        }

    @Test
    fun `acceptance 10 a timeout on budget is sent once and the true value is shown`() =
        runTest {
            val env = workEnv()
            env.agents.failures["setBudget"] = DomainError.Timeout
            val holder = detailHolder(env)
            advanceUntilIdle()

            holder.enterBudget("500.00")
            holder.onIntent(AgentDetailIntent.BudgetSubmitted)
            advanceUntilIdle()

            env.agents.count("setBudget") shouldBe 1
            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Reconciled, error = ErrorUi(ErrorKey.Timeout))
            holder.state.value.monthlyBudget shouldBe MoneyUi(100_000, "USD")
        }

    @Test
    fun `resume run now refresh and dismiss work`() =
        runTest {
            val env = workEnv()
            val holder = detailHolder(env, "agent-design")
            advanceUntilIdle()
            holder.onIntent(AgentDetailIntent.ResumeTapped)
            advanceUntilIdle()
            holder.state.value.agent
                ?.status shouldBe AgentStatusUi.Idle

            holder.onIntent(AgentDetailIntent.RunNowTapped)
            advanceUntilIdle()
            env.agents.count("invokeHeartbeat") shouldBe 1

            holder.onIntent(AgentDetailIntent.Refresh)
            holder.onIntent(AgentDetailIntent.MessageDismissed)
            advanceUntilIdle()
            env.agents.count("get") shouldBe 2
        }

    @Test
    fun `heartbeat_timeout_with_preexisting_live_run_shows_the_refresh_prompt_key`() =
        runTest {
            val env = workEnv()
            env.agents.failures["invokeHeartbeat"] = DomainError.Timeout
            val holder = detailHolder(env)
            advanceUntilIdle()

            holder.onIntent(AgentDetailIntent.RunNowTapped)
            advanceUntilIdle()

            env.agents.count("invokeHeartbeat") shouldBe 1
            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Unknown, error = ErrorUi(ErrorKey.Timeout))
        }
}

/** F-B-7: a read that was answered before an action finished must not replace the action's result. */
class AgentDetailStaleLoadTest {
    @Test
    fun `staleLoadDoesNotOverwriteAfterPause`() =
        runTest {
            val env = workEnv()
            val holder = detailHolder(env)
            advanceUntilIdle()
            env.agents.afterRead = { delay(5_000) }
            holder.onIntent(AgentDetailIntent.Refresh)
            runCurrent()
            env.agents.afterRead = null

            holder.onIntent(AgentDetailIntent.PauseTapped)
            holder.onIntent(AgentDetailIntent.PauseConfirmed)
            advanceTimeBy(100)
            holder.state.value.agent
                ?.status shouldBe AgentStatusUi.Paused

            advanceUntilIdle()

            holder.state.value.agent
                ?.status shouldBe AgentStatusUi.Paused
        }
}

internal fun AgentDetailStateHolder.enterBudget(text: String) =
    onIntent(AgentDetailIntent.BudgetAmountTyped(text, ".", 2))

class AgentDetailBudgetInputTest {
    @Test
    fun `A4 the typed budget is parsed in shared code and says what is wrong`() =
        runTest {
            val env = workEnv(Role.Owner)
            val holder = detailHolder(env)
            advanceUntilIdle()

            holder.onIntent(AgentDetailIntent.BudgetAmountTyped("250,50", ".", 2))
            runCurrent()
            holder.state.value.budgetAmountIssue shouldBe InvalidKey.AmountNotNumeric
            holder.state.value.canSaveBudget shouldBe false

            holder.onIntent(AgentDetailIntent.BudgetAmountTyped("250,50", ",", 2))
            runCurrent()
            holder.state.value.budgetAmountIssue shouldBe null
            holder.state.value.budgetAmountMinorUnits shouldBe 25_050L
            holder.state.value.canSaveBudget shouldBe true
        }

    @Test
    fun `A11 zero is not a budget and nothing is sent`() =
        runTest {
            val env = workEnv(Role.Owner)
            val holder = detailHolder(env)
            advanceUntilIdle()

            holder.enterBudget("0")
            runCurrent()
            holder.state.value.canSaveBudget shouldBe false
            holder.onIntent(AgentDetailIntent.BudgetSubmitted)
            advanceUntilIdle()

            env.agents.count("setBudget") shouldBe 0
        }

    @Test
    fun `B-06 a saved budget is reported once and a failed one keeps the amount`() =
        runTest {
            val env = workEnv(Role.Owner)
            val holder = detailHolder(env)
            advanceUntilIdle()
            env.agents.failures["setBudget"] = DomainError.Timeout
            holder.enterBudget("500.00")
            holder.onIntent(AgentDetailIntent.BudgetSubmitted)
            advanceUntilIdle()

            holder.state.value.budgetSavedCount shouldBe 0
            holder.state.value.budgetAmountMinorUnits shouldBe 50_000L

            env.agents.failures.remove("setBudget")
            holder.onIntent(AgentDetailIntent.BudgetSubmitted)
            advanceUntilIdle()

            holder.state.value.budgetSavedCount shouldBe 1
            holder.state.value.budgetAmountMinorUnits shouldBe null
        }
}
