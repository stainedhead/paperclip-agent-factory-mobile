package com.stainedhead.paperclipmobile.application.decision

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.domain.Decision
import com.stainedhead.paperclipmobile.domain.DecisionEffect
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.DecisionInput
import com.stainedhead.paperclipmobile.domain.DecisionOption
import com.stainedhead.paperclipmobile.domain.DecisionStatus
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.EffectStaleness
import com.stainedhead.paperclipmobile.domain.ExecutionStatus
import com.stainedhead.paperclipmobile.domain.InputIssue
import com.stainedhead.paperclipmobile.domain.OptionStyle
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class LoadDecisionTest {
    private val id = DecisionId("dec-1")

    @Test
    fun `FR-040 a decision is loaded by id`() =
        runTest {
            val env = Env()

            val result = LoadDecision(env.decisions)(env.scope, id)

            (result as DomainResult.Success).value.title shouldBe "Ship the billing migration?"
        }

    @Test
    fun `FR-096 a transient failure is retried three times`() =
        runTest {
            val env = Env()
            env.decisions.failures["get"] = DomainError.Timeout

            LoadDecision(env.decisions)(env.scope, id)

            env.decisions.count("get") shouldBe 4
        }

    @Test
    fun `a missing decision is not retried`() =
        runTest {
            val env = Env()

            val result = LoadDecision(env.decisions)(env.scope, DecisionId("nope"))

            (result as DomainResult.Failure).error shouldBe DomainError.NotFound
            env.decisions.count("get") shouldBe 1
        }
}

class DecideDecisionTest {
    private val open = AttentionFixtures.decision()

    private fun Env.decide() = DecideDecision(decisions, deviceAuth, ids)

    @Test
    fun `FR-043 a plain option is decided once with an idempotency key and no device authentication`() =
        runTest {
            val env = Env()

            val result = env.decide()(env.context(Role.Operator), open.id, "ship", emptyMap())

            val decided = (result as MutationResult.Done).value
            decided.status shouldBe DecisionStatus.Decided
            decided.outcome?.executionStatus shouldBe ExecutionStatus.Succeeded
            env.biometrics.calls shouldBe 0
            env.decisions.count("decide") shouldBe 1
            env.decisions.decidedWith
                .single()
                .idempotencyKey
                .startsWith("inst-") shouldBe true
        }

    @Test
    fun `FR-041 a destructive style option asks the device first`() =
        runTest {
            val env = Env()

            val result = env.decide()(env.context(), open.id, "cancel", emptyMap())

            result.shouldBeInstanceOf<MutationResult.Done<*>>()
            env.biometrics.calls shouldBe 1
            env.decisions.count("decide") shouldBe 1
        }

    @Test
    fun `FR-041 a cancel_issue_tree effect asks the device even when the style is default`() =
        runTest {
            val env = Env()
            val sneaky =
                open.copy(
                    options =
                        listOf(
                            DecisionOption(
                                "stop",
                                "Stop",
                                null,
                                OptionStyle.Default,
                                listOf(DecisionEffect.CancelIssueTree("iss-1", "stop")),
                            ),
                        ),
                )
            env.decisions.put(sneaky)

            env.decide()(env.context(), sneaky.id, "stop", emptyMap())

            env.biometrics.calls shouldBe 1
        }

    @Test
    fun `decide_uses_server_side_option_destructiveness_for_device_auth even though a screen holds an older copy`() =
        runTest {
            val env = Env()
            val destructive =
                open.copy(
                    options =
                        listOf(
                            DecisionOption(
                                "stop",
                                "Stop",
                                null,
                                OptionStyle.Default,
                                listOf(DecisionEffect.CancelIssueTree("iss-1", "stop")),
                            ),
                        ),
                )
            env.decisions.put(destructive)
            env.biometrics.result = BiometricResult.Cancelled

            val result = env.decide()(env.context(), destructive.id, "stop", emptyMap())

            result shouldBe MutationResult.NotAuthorized(BiometricResult.Cancelled)
            env.biometrics.calls shouldBe 1
            env.decisions.count("decide") shouldBe 0
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

                val result = env.decide()(env.context(), open.id, "cancel", emptyMap())

                result shouldBe MutationResult.NotAuthorized(denied)
                env.decisions.count("decide") shouldBe 0
            }
        }

    @Test
    fun `a viewer never reaches biometrics or the gateway`() =
        runTest {
            val env = Env()

            val result = env.decide()(env.context(Role.Viewer), open.id, "cancel", emptyMap())

            result shouldBe MutationResult.Denied
            env.biometrics.calls shouldBe 0
            env.decisions.count("decide") shouldBe 0
        }

    @Test
    fun `FR-042 a missing required input is refused before the device prompt`() =
        runTest {
            val env = Env()
            val withInput = open.copy(inputs = listOf(DecisionInput("why", "Why", required = true)))
            env.decisions.put(withInput)

            val result = env.decide()(env.context(), withInput.id, "cancel", emptyMap())

            result shouldBe MutationResult.Invalid(InvalidInput.Input(InputIssue.Required("why")))
            env.biometrics.calls shouldBe 0
            env.decisions.count("decide") shouldBe 0
        }

    @Test
    fun `FR-042 input values reach the gateway`() =
        runTest {
            val env = Env()
            val withInput = AttentionFixtures.decision(inputs = listOf(DecisionInput("why", "Why", required = true)))
            env.decisions.put(withInput)

            env.decide()(env.context(), withInput.id, "ship", mapOf("why" to "ready"))

            env.decisions.decidedWith
                .single()
                .inputValues shouldBe mapOf("why" to "ready")
        }

    @Test
    fun `an unknown option is refused without a gateway call`() =
        runTest {
            val env = Env()

            env.decide()(env.context(), open.id, "teleport", emptyMap()) shouldBe
                MutationResult.Invalid(InvalidInput.UnknownOption)

            env.decisions.count("decide") shouldBe 0
        }

    @Test
    fun `FR-044 a decision that is not open is refused without a gateway call`() =
        runTest {
            val env = Env()
            val expired = open.copy(status = DecisionStatus.Expired)
            env.decisions.put(expired)

            val result = env.decide()(env.context(), expired.id, "ship", emptyMap())

            // The server's copy is not open: nothing is sent and the screen gets that copy as the truth (FR-044).
            result shouldBe MutationResult.Reconciled(DomainError.Conflict, expired)
            env.decisions.count("decide") shouldBe 0
        }

    @Test
    fun `acceptance 10 a timeout is attempted once then the decision is read again`() =
        runTest {
            val env = Env()
            env.decisions.failures["decide"] = DomainError.Timeout
            env.decisions.serverChangesOnFailure = { it.copy(status = DecisionStatus.Decided, chosenOptionId = "ship") }

            val result = env.decide()(env.context(), open.id, "ship", emptyMap())

            val reconciled = result as MutationResult.Reconciled<Decision>
            reconciled.error shouldBe DomainError.Timeout
            reconciled.current.status shouldBe DecisionStatus.Decided
            env.decisions.count("decide") shouldBe 1
            env.decisions.count("get") shouldBe 2
        }

    @Test
    fun `acceptance 12 a decision resolved elsewhere comes back as its final state`() =
        runTest {
            val env = Env()
            env.decisions.put(open.copy(status = DecisionStatus.Decided, chosenOptionId = "hold"))

            val result = env.decide()(env.context(), open.id, "ship", emptyMap())

            val reconciled = result as MutationResult.Reconciled<Decision>
            reconciled.error shouldBe DomainError.Conflict
            reconciled.current.chosenOptionId shouldBe "hold"
            reconciled.current.isOpen shouldBe false
            env.decisions.count("decide") shouldBe 0
        }

    @Test
    fun `other failures are reported once with no reconciling read and no retry`() =
        runTest {
            listOf(
                DomainError.Forbidden,
                DomainError.ServerError,
                DomainError.Rejected("Input why is required"),
            ).forEach {
                val env = Env()
                env.decisions.failures["decide"] = it

                env.decide()(env.context(), open.id, "ship", emptyMap()) shouldBe MutationResult.Failed(it)

                env.decisions.count("decide") shouldBe 1
                env.decisions.count("get") shouldBe 1
            }
        }

    @Test
    fun `a failed re-read reports the original error`() =
        runTest {
            val env = Env()
            env.decisions.failures["decide"] = DomainError.Timeout
            env.decisions.serverChangesOnFailure = {
                env.decisions.failures["get"] = DomainError.Unreachable
                it
            }

            env.decide()(env.context(), open.id, "ship", emptyMap()) shouldBe MutationResult.Failed(DomainError.Timeout)
        }
}

class DismissDecisionTest {
    private val open = AttentionFixtures.decision()

    @Test
    fun `FR-043 dismiss is sent once with the reason`() =
        runTest {
            val env = Env()

            val result = DismissDecision(env.decisions)(env.context(Role.Operator), open, "not now")

            (result as MutationResult.Done).value.dismissed shouldBe true
            env.decisions.count("dismiss") shouldBe 1
            env.biometrics.calls shouldBe 0
        }

    @Test
    fun `a viewer never reaches the gateway`() =
        runTest {
            val env = Env()

            DismissDecision(env.decisions)(env.context(Role.Viewer), open, null) shouldBe MutationResult.Denied
            env.decisions.count("dismiss") shouldBe 0
        }

    @Test
    fun `a decision that is not open is refused`() =
        runTest {
            val env = Env()

            val result =
                DismissDecision(env.decisions)(env.context(), open.copy(status = DecisionStatus.Cancelled), null)

            result shouldBe MutationResult.Invalid(InvalidInput.WrongStatus)
            env.decisions.count("dismiss") shouldBe 0
        }

    @Test
    fun `FR-044 a conflict comes back as the final state`() =
        runTest {
            val env = Env()
            env.decisions.put(open.copy(status = DecisionStatus.Decided, chosenOptionId = "hold"))

            val result = DismissDecision(env.decisions)(env.context(), open, null)

            (result as MutationResult.Reconciled<Decision>).current.chosenOptionId shouldBe "hold"
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PollDecisionExecutionTest {
    private val id = DecisionId("dec-1")

    private suspend fun Env.decided() {
        decisions.decide(scope, id, "ship", emptyMap(), "k")
    }

    @Test
    fun `FR-043 polling reads until the execution is terminal at one second intervals`() =
        runTest {
            val env = Env()
            env.decided()
            env.decisions.runningReads = 2

            val result = PollDecisionExecution(env.decisions)(env.scope, id)

            (result as DomainResult.Success).value.outcome?.executionStatus shouldBe ExecutionStatus.Succeeded
            env.decisions.count("get") shouldBe 3
            testScheduler.currentTime shouldBe 2_000
        }

    @Test
    fun `an already terminal decision is read once with no wait`() =
        runTest {
            val env = Env()
            env.decided()

            PollDecisionExecution(env.decisions)(env.scope, id)

            env.decisions.count("get") shouldBe 1
            testScheduler.currentTime shouldBe 0
        }

    @Test
    fun `polling gives up after ten reads and returns the decision still running`() =
        runTest {
            val env = Env()
            env.decided()
            env.decisions.runningReads = 100

            val result = PollDecisionExecution(env.decisions)(env.scope, id)

            (result as DomainResult.Success).value.isExecutionPending shouldBe true
            env.decisions.count("get") shouldBe 10
        }

    @Test
    fun `a failed read ends polling with the error`() =
        runTest {
            val env = Env()
            env.decisions.failures["get"] = DomainError.Timeout

            val result = PollDecisionExecution(env.decisions)(env.scope, id)

            (result as DomainResult.Failure).error shouldBe DomainError.Timeout
            env.decisions.count("get") shouldBe 1
        }
}
