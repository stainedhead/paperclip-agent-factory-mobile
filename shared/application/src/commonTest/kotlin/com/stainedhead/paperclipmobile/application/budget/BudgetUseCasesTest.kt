package com.stainedhead.paperclipmobile.application.budget

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.domain.BudgetIncident
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.BudgetResolution
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.IncidentStatus
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.ResolutionIssue
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class LoadBudgetIncidentTest {
    private val id = BudgetIncidentId("inc-1")

    @Test
    fun `FR-052 an active incident is loaded`() =
        runTest {
            val env = Env()

            val result = LoadBudgetIncident(env.budgets)(env.scope, id)

            (result as DomainResult.Success).value.scopeName shouldBe "Engineer 2"
        }

    @Test
    fun `an incident that is no longer active is not found and not retried`() =
        runTest {
            val env = Env()

            val result = LoadBudgetIncident(env.budgets)(env.scope, BudgetIncidentId("gone"))

            (result as DomainResult.Failure).error shouldBe DomainError.NotFound
            env.budgets.count("incident") shouldBe 1
        }

    @Test
    fun `FR-096 a transient failure is retried three times`() =
        runTest {
            val env = Env()
            env.budgets.failures["incident"] = DomainError.Timeout

            LoadBudgetIncident(env.budgets)(env.scope, id)

            env.budgets.count("incident") shouldBe 4
        }
}

class ResolveBudgetIncidentTest {
    private val incident = AttentionFixtures.incident(observed = 10_000, limit = 10_000)
    private val raise = BudgetResolution.RaiseAndResume(Money.usdCents(20_000))

    private fun Env.resolve() = ResolveBudgetIncident(budgets, deviceAuth)

    @Test
    fun `FR-052 keep paused is open to operators and needs no device authentication`() =
        runTest {
            val env = Env()

            val result = env.resolve()(env.context(Role.Operator), incident, BudgetResolution.KeepPaused)

            (result as MutationResult.Done).value.status shouldBe IncidentStatus.Dismissed
            env.biometrics.calls shouldBe 0
        }

    @Test
    fun `FR-052 an owner raises the budget after device authentication and the amount reaches the gateway`() =
        runTest {
            val env = Env()

            val result = env.resolve()(env.context(Role.Owner), incident, raise)

            (result as MutationResult.Done).value.status shouldBe IncidentStatus.Resolved
            env.biometrics.calls shouldBe 1
            env.budgets.resolutions shouldBe listOf(raise)
        }

    @Test
    fun `an operator cannot raise a budget and is never asked to authenticate`() =
        runTest {
            val env = Env()

            env.resolve()(env.context(Role.Operator), incident, raise) shouldBe MutationResult.Denied

            env.biometrics.calls shouldBe 0
            env.budgets.count("resolve") shouldBe 0
        }

    @Test
    fun `a viewer cannot do either`() =
        runTest {
            val env = Env()

            env.resolve()(env.context(Role.Viewer), incident, BudgetResolution.KeepPaused) shouldBe
                MutationResult.Denied
            env.resolve()(env.context(Role.Viewer), incident, raise) shouldBe MutationResult.Denied

            env.budgets.count("resolve") shouldBe 0
        }

    @Test
    fun `FR-052 a raise that does not exceed the observed spend is refused before the device prompt`() =
        runTest {
            val env = Env()
            val tooLow = BudgetResolution.RaiseAndResume(Money.usdCents(10_000))

            env.resolve()(env.context(), incident, tooLow) shouldBe
                MutationResult.Invalid(InvalidInput.Amount(ResolutionIssue.MustExceedObserved))

            env.biometrics.calls shouldBe 0
            env.budgets.count("resolve") shouldBe 0
        }

    @Test
    fun `a raise in another currency is refused`() =
        runTest {
            val env = Env()

            env.resolve()(env.context(), incident, BudgetResolution.RaiseAndResume(Money(50_000, "EUR"))) shouldBe
                MutationResult.Invalid(InvalidInput.Amount(ResolutionIssue.WrongCurrency))
        }

    @Test
    fun `an incident that is no longer open is refused`() =
        runTest {
            val env = Env()

            env.resolve()(env.context(), incident.copy(status = IncidentStatus.Resolved), raise) shouldBe
                MutationResult.Invalid(InvalidInput.WrongStatus)
            env.budgets.count("resolve") shouldBe 0
        }

    @Test
    fun `FR-014 cancel failure lockout and unavailable make zero gateway calls`() =
        runTest {
            listOf(
                BiometricResult.Cancelled,
                BiometricResult.Failed,
                BiometricResult.LockedOut,
                BiometricResult.Unavailable,
            ).forEach { denied ->
                val env = Env()
                env.biometrics.result = denied

                env.resolve()(env.context(), incident, raise) shouldBe MutationResult.NotAuthorized(denied)

                env.budgets.count("resolve") shouldBe 0
            }
        }

    @Test
    fun `FR-052 the server's 422 reason is surfaced and the call is not retried or re-read`() =
        runTest {
            val env = Env()
            env.budgets.serverObservedMinor = 25_000

            val result = env.resolve()(env.context(), incident, raise)

            result shouldBe MutationResult.Failed(DomainError.Rejected("New budget must exceed current observed spend"))
            env.budgets.count("resolve") shouldBe 1
            env.budgets.count("incident") shouldBe 0
        }

    @Test
    fun `acceptance 10 a timeout is attempted once and the incident is read again`() =
        runTest {
            val env = Env()
            env.budgets.failures["resolve"] = DomainError.Timeout

            val result = env.resolve()(env.context(), incident, raise)

            val reconciled = result as MutationResult.Reconciled<BudgetIncident>
            reconciled.error shouldBe DomainError.Timeout
            reconciled.current.status shouldBe IncidentStatus.Open
            env.budgets.count("resolve") shouldBe 1
            env.budgets.count("incident") shouldBe 1
        }

    @Test
    fun `an incident that left the active list after a timeout is reported as no longer active`() =
        runTest {
            val env = Env()
            env.budgets.failures["resolve"] = DomainError.Timeout
            env.budgets.failures["incident"] = DomainError.NotFound

            val result = env.resolve()(env.context(), incident, raise)

            (result as MutationResult.Reconciled<BudgetIncident>).current.status shouldBe IncidentStatus.Unknown
        }

    @Test
    fun `a failed re-read reports the original error`() =
        runTest {
            val env = Env()
            env.budgets.failures["resolve"] = DomainError.Timeout
            env.budgets.failures["incident"] = DomainError.Unreachable

            env.resolve()(env.context(), incident, raise) shouldBe MutationResult.Failed(DomainError.Timeout)
        }
}
