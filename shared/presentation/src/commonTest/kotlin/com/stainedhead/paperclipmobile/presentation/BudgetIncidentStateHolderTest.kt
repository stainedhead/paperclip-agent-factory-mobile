@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** The amount as the person types it: whole cents at a point-decimal, two-digit currency phone. */
internal fun typed(minorUnits: Long?): BudgetIntent.AmountTyped =
    BudgetIntent.AmountTyped(
        if (minorUnits == null) "" else "${minorUnits / 100}.${(minorUnits % 100).toString().padStart(2, '0')}",
        ".",
        2,
    )

internal fun TestScope.budgetEnv(role: Role = Role.Owner): Env {
    val env = Env(listOf(TestData.instance())) { testScheduler.currentTime }
    env.auth.meResult = DomainResult.Success(TestData.access(role))
    return env
}

internal fun TestScope.budgetHolder(
    env: Env,
    incidentId: String = "inc-1",
) = BudgetIncidentStateHolder(
    env.budget,
    env.connectivity,
    env.clock,
    CoroutineScope(StandardTestDispatcher(testScheduler)),
    CompanyTarget("inst-1", TestData.acme.value),
    incidentId,
)

class BudgetIncidentLoadingTest {
    @Test
    fun `FR-052 the incident shows its scope threshold observed spend and limit`() =
        runTest {
            val env = budgetEnv()
            val holder = budgetHolder(env)
            advanceUntilIdle()

            val state = holder.state.value
            state.phase shouldBe DetailPhase.Content
            state.scopeName shouldBe "Engineer 2"
            state.scopeType shouldBe "agent"
            state.threshold shouldBe BudgetThresholdUi.Hard
            state.observed shouldBe MoneyUi(10_000, "USD")
            state.limit shouldBe MoneyUi(10_000, "USD")
            state.observedPercent shouldBe 100
            state.outcome shouldBe null
            state.canKeepPaused shouldBe true
            state.canRaise shouldBe true
            state.updatedAtEpochMillis shouldBe env.clock.now().toEpochMilliseconds()
        }

    @Test
    fun `an operator can keep paused but not raise`() =
        runTest {
            val env = budgetEnv(Role.Operator)
            val holder = budgetHolder(env)
            advanceUntilIdle()

            holder.state.value.canKeepPaused shouldBe true
            holder.state.value.canRaise shouldBe false
        }

    @Test
    fun `a viewer can do neither and sees why`() =
        runTest {
            val env = budgetEnv(Role.Viewer)
            val holder = budgetHolder(env)
            advanceUntilIdle()

            holder.state.value.canKeepPaused shouldBe false
            holder.state.value.canRaise shouldBe false
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `an incident that is no longer active shows that as its final state`() =
        runTest {
            val env = budgetEnv()
            val holder = budgetHolder(env, "gone")
            advanceUntilIdle()

            holder.state.value.phase shouldBe DetailPhase.Content
            holder.state.value.outcome shouldBe BudgetOutcomeUi.NoLongerActive
            holder.state.value.refreshIntervalSeconds shouldBe 0
            holder.state.value.canKeepPaused shouldBe false
            holder.state.value.canRaise shouldBe false
        }

    @Test
    fun `another load failure is a failed screen with an error key`() =
        runTest {
            val env = budgetEnv()
            env.budgets.failures["incident"] = DomainError.Forbidden
            val holder = budgetHolder(env)
            advanceUntilIdle()

            holder.state.value.phase shouldBe DetailPhase.Failed
            holder.state.value.error shouldBe ErrorUi(ErrorKey.NoPermission)
        }

    @Test
    fun `offline there is nothing to show and no call is made`() =
        runTest {
            val env = budgetEnv()
            env.connectivity.setOnline(false)
            val holder = budgetHolder(env)
            advanceUntilIdle()

            holder.state.value.phase shouldBe DetailPhase.Failed
            holder.state.value.isOffline shouldBe true
            env.budgets.count("incident") shouldBe 0
        }
}

class BudgetIncidentActionsTest {
    private suspend fun TestScope.loaded(env: Env): BudgetIncidentStateHolder {
        val holder = budgetHolder(env)
        advanceUntilIdle()
        return holder
    }

    @Test
    fun `FR-052 a raise must exceed the observed spend and says so before anything is sent`() =
        runTest {
            val env = budgetEnv()
            val holder = loaded(env)

            holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.RaiseAndResume))
            holder.onIntent(typed(10_000))
            runCurrent()
            holder.state.value.amountIssue shouldBe InvalidKey.AmountMustExceedObserved
            holder.state.value.canSubmit shouldBe false

            holder.onIntent(typed(20_000))
            runCurrent()
            holder.state.value.amountIssue shouldBe null
            holder.state.value.canSubmit shouldBe true
            holder.state.value.submitNeedsDeviceAuth shouldBe true

            holder.onIntent(typed(null))
            runCurrent()
            holder.state.value.canSubmit shouldBe false
        }

    @Test
    fun `FR-052 an owner raises the budget after device authentication`() =
        runTest {
            val env = budgetEnv()
            val holder = loaded(env)

            holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.RaiseAndResume))
            holder.onIntent(typed(20_000))
            holder.onIntent(BudgetIntent.SubmitTapped)
            advanceUntilIdle()

            env.biometrics.calls shouldBe 1
            env.budgets.count("resolve") shouldBe 1
            holder.state.value.outcome shouldBe BudgetOutcomeUi.Raised
            holder.state.value.canSubmit shouldBe false
        }

    @Test
    fun `keeping the budget paused is open to operators and needs no device authentication`() =
        runTest {
            val env = budgetEnv(Role.Operator)
            val holder = loaded(env)

            holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.KeepPaused))
            runCurrent()
            holder.state.value.submitNeedsDeviceAuth shouldBe false
            holder.onIntent(BudgetIntent.SubmitTapped)
            advanceUntilIdle()

            env.biometrics.calls shouldBe 0
            holder.state.value.outcome shouldBe BudgetOutcomeUi.KeptPaused
        }

    @Test
    fun `an operator's raise choice is ignored`() =
        runTest {
            val env = budgetEnv(Role.Operator)
            val holder = loaded(env)

            holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.RaiseAndResume))
            holder.onIntent(typed(20_000))
            holder.onIntent(BudgetIntent.SubmitTapped)
            advanceUntilIdle()

            holder.state.value.choice shouldBe null
            env.budgets.count("resolve") shouldBe 0
        }

    @Test
    fun `FR-014 a refused device prompt sends nothing`() =
        runTest {
            val env = budgetEnv()
            env.biometrics.result = BiometricResult.Unavailable
            val holder = loaded(env)

            holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.RaiseAndResume))
            holder.onIntent(typed(20_000))
            holder.onIntent(BudgetIntent.SubmitTapped)
            advanceUntilIdle()

            env.budgets.count("resolve") shouldBe 0
            holder.state.value.feedback shouldBe
                FeedbackUi(FeedbackKind.DeviceAuth, deviceAuth = DeviceAuthIssue.Unavailable)
            holder.state.value.outcome shouldBe null
        }

    @Test
    fun `FR-052 the server's 422 reason is shown as it is and the incident stays open`() =
        runTest {
            val env = budgetEnv()
            env.budgets.serverObservedMinor = 25_000
            val holder = loaded(env)

            holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.RaiseAndResume))
            holder.onIntent(typed(20_000))
            holder.onIntent(BudgetIntent.SubmitTapped)
            advanceUntilIdle()

            holder.state.value.feedback shouldBe
                FeedbackUi(
                    FeedbackKind.Failed,
                    error = ErrorUi(ErrorKey.Rejected, "New budget must exceed current observed spend"),
                )
            holder.state.value.outcome shouldBe null
            env.budgets.count("resolve") shouldBe 1
        }

    @Test
    fun `acceptance 10 a timeout is sent once and the incident is read again`() =
        runTest {
            val env = budgetEnv()
            val holder = loaded(env)
            env.budgets.failures["resolve"] = DomainError.Timeout

            holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.KeepPaused))
            holder.onIntent(BudgetIntent.SubmitTapped)
            advanceUntilIdle()

            env.budgets.count("resolve") shouldBe 1
            env.budgets.count("incident") shouldBe 2
            holder.state.value.feedback
                ?.kind shouldBe FeedbackKind.Reconciled
            holder.state.value.outcome shouldBe null
        }

    @Test
    fun `acceptance 12 an incident resolved elsewhere shows as no longer active`() =
        runTest {
            val env = budgetEnv()
            val holder = loaded(env)
            env.budgets.failures["resolve"] = DomainError.Timeout
            env.budgets.failures["incident"] = DomainError.NotFound

            holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.KeepPaused))
            holder.onIntent(BudgetIntent.SubmitTapped)
            advanceUntilIdle()

            holder.state.value.outcome shouldBe BudgetOutcomeUi.NoLongerActive
        }

    @Test
    fun `a second tap while the call is in flight is ignored`() =
        runTest {
            val env = budgetEnv()
            val holder = loaded(env)

            holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.KeepPaused))
            holder.onIntent(BudgetIntent.SubmitTapped)
            holder.onIntent(BudgetIntent.SubmitTapped)
            advanceUntilIdle()

            env.budgets.count("resolve") shouldBe 1
        }

    @Test
    fun `submitting with no choice or no amount does nothing`() =
        runTest {
            val env = budgetEnv()
            val holder = loaded(env)

            holder.onIntent(BudgetIntent.SubmitTapped)
            holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.RaiseAndResume))
            holder.onIntent(BudgetIntent.SubmitTapped)
            advanceUntilIdle()

            env.budgets.count("resolve") shouldBe 0
        }

    @Test
    fun `FR-095 offline the actions are disabled with a reason and ignored`() =
        runTest {
            val env = budgetEnv()
            val holder = loaded(env)
            env.connectivity.setOnline(false)
            advanceUntilIdle()

            holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.KeepPaused))
            holder.onIntent(BudgetIntent.SubmitTapped)
            advanceUntilIdle()

            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.Offline
            holder.state.value.canKeepPaused shouldBe false
            env.budgets.count("resolve") shouldBe 0
        }

    @Test
    fun `refresh dismissing a message and closing behave`() =
        runTest {
            val env = budgetEnv()
            env.biometrics.result = BiometricResult.Cancelled
            val holder = loaded(env)
            holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.RaiseAndResume))
            holder.onIntent(typed(20_000))
            holder.onIntent(BudgetIntent.SubmitTapped)
            advanceUntilIdle()
            holder.state.value.feedback
                ?.kind shouldBe FeedbackKind.DeviceAuth

            holder.onIntent(BudgetIntent.MessageDismissed)
            runCurrent()
            holder.state.value.feedback shouldBe null
            holder.onIntent(BudgetIntent.Refresh)
            advanceUntilIdle()
            env.budgets.count("incident") shouldBe 2

            holder.close()
            holder.onIntent(BudgetIntent.Refresh)
            advanceUntilIdle()
            env.budgets.count("incident") shouldBe 2
        }

    @Test
    fun `refreshAfterResolveKeepsRaisedOutcome`() =
        runTest {
            val env = budgetEnv()
            val holder = loaded(env)
            holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.RaiseAndResume))
            holder.onIntent(typed(20_000))
            holder.onIntent(BudgetIntent.SubmitTapped)
            advanceUntilIdle()
            holder.state.value.outcome shouldBe BudgetOutcomeUi.Raised

            holder.onIntent(BudgetIntent.Refresh)
            advanceUntilIdle()

            holder.state.value.outcome shouldBe BudgetOutcomeUi.Raised
        }
}

class BudgetAmountInputTest {
    private suspend fun TestScope.raising(env: Env): BudgetIncidentStateHolder {
        val holder = budgetHolder(env)
        advanceUntilIdle()
        holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.RaiseAndResume))
        return holder
    }

    @Test
    fun `A4 a comma decimal in a point locale is refused not read as a hundred times more`() =
        runTest {
            val holder = raising(budgetEnv())

            holder.onIntent(BudgetIntent.AmountTyped("250,50", ".", 2))
            runCurrent()

            holder.state.value.amountIssue shouldBe InvalidKey.AmountNotNumeric
            holder.state.value.amountMinorUnits shouldBe null
            holder.state.value.canSubmit shouldBe false
        }

    @Test
    fun `R-024 an amount typed before choosing Keep paused is gone when Raise is chosen again`() =
        runTest {
            val holder = raising(budgetEnv())
            holder.onIntent(typed(25_000))
            runCurrent()
            holder.state.value.amountMinorUnits shouldBe 25_000L

            holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.KeepPaused))
            runCurrent()
            holder.state.value.amountMinorUnits shouldBe null

            holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.RaiseAndResume))
            runCurrent()
            holder.state.value.amountMinorUnits shouldBe null
            holder.state.value.amountIssue shouldBe null
            holder.state.value.canSubmit shouldBe false
        }

    @Test
    fun `R-024 choosing the same option again keeps what was typed`() =
        runTest {
            val holder = raising(budgetEnv())
            holder.onIntent(typed(25_000))
            runCurrent()

            holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.RaiseAndResume))
            runCurrent()

            holder.state.value.amountMinorUnits shouldBe 25_000L
        }

    @Test
    fun `A4 the amount the person will approve is in the state`() =
        runTest {
            val holder = raising(budgetEnv())

            holder.onIntent(BudgetIntent.AmountTyped("1.200,50", ",", 2))
            runCurrent()

            holder.state.value.amountMinorUnits shouldBe 120_050L
            holder.state.value.amountIssue shouldBe null
            holder.state.value.canSubmit shouldBe true
        }

    @Test
    fun `A4 zero negative and huge amounts cannot be submitted`() =
        runTest {
            val env = budgetEnv()
            val holder = raising(env)
            val cases =
                mapOf(
                    "0" to InvalidKey.AmountNotPositive,
                    "-20" to InvalidKey.AmountNotPositive,
                    "99999999999999999999" to InvalidKey.AmountTooLarge,
                )
            for ((text, key) in cases) {
                holder.onIntent(BudgetIntent.AmountTyped(text, ".", 2))
                runCurrent()
                holder.state.value.amountIssue shouldBe key
                holder.state.value.canSubmit shouldBe false
            }
            holder.onIntent(BudgetIntent.SubmitTapped)
            advanceUntilIdle()
            env.budgets.count("resolve") shouldBe 0
        }
}
