@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.Decision
import com.stainedhead.paperclipmobile.domain.DecisionEffect
import com.stainedhead.paperclipmobile.domain.DecisionInput
import com.stainedhead.paperclipmobile.domain.DecisionStatus
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.EffectStaleness
import com.stainedhead.paperclipmobile.domain.EffectStatus
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
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

private val instance = TestData.instance()

private fun TestScope.decisionEnv(role: Role = Role.Owner): Env {
    val env = Env(listOf(instance)) { testScheduler.currentTime }
    env.auth.meResult = DomainResult.Success(TestData.access(role))
    return env
}

private fun TestScope.decisionHolder(
    env: Env,
    agentHint: String? = null,
    decideByHint: String? = null,
) = DecisionStateHolder(
    env.decision,
    env.connectivity,
    env.clock,
    CoroutineScope(StandardTestDispatcher(testScheduler)),
    DecisionTarget(CompanyTarget("inst-1", TestData.acme.value), "dec-1", agentHint, decideByHint),
)

class DecisionLoadingTest {
    @Test
    fun `FR-040 the question origin agent and every option with its effects are shown before choosing`() =
        runTest {
            val env = decisionEnv()
            val holder = decisionHolder(env)
            advanceUntilIdle()

            val state = holder.state.value
            state.phase shouldBe DetailPhase.Content
            state.title shouldBe "Ship the billing migration?"
            state.originAgentName shouldBe "Engineer 1"
            state.options.map { it.id } shouldBe listOf("ship", "hold", "cancel")
            state.options[0].effects shouldBe
                listOf(
                    EffectSummaryUi(EffectKindUi.CommentOnIssue, "iss-1182", "Go ahead with the migration.", false),
                )
            state.options[1].effects shouldBe emptyList()
            state.options[2]
                .effects
                .single()
                .kind shouldBe EffectKindUi.CancelIssueTree
            state.options[2]
                .effects
                .single()
                .isStrict shouldBe true
            state.updatedAtEpochMillis shouldBe env.clock.now().toEpochMilliseconds()
            state.finalState shouldBe null
        }

    @Test
    fun `FR-041 destructive options are flagged and need device authentication`() =
        runTest {
            val env = decisionEnv()
            val holder = decisionHolder(env)
            advanceUntilIdle()

            holder.state.value.options
                .map { it.isDestructive } shouldBe listOf(false, false, true)
            holder.state.value.options
                .map { it.needsDeviceAuth } shouldBe listOf(false, false, true)
        }

    @Test
    fun `every effect type has a summary key and parameters`() =
        runTest {
            val env = decisionEnv()
            val effects =
                listOf(
                    DecisionEffect.CreateIssue("i1", EffectStaleness.Lenient, "Follow-up"),
                    DecisionEffect.UpdateIssueStatus("i1", EffectStaleness.Strict, "done"),
                    DecisionEffect.AssignIssue("i1", EffectStaleness.Lenient, "QA agent"),
                    DecisionEffect.ResolveBlocker("i1", EffectStaleness.Lenient, 2),
                    DecisionEffect.Unsupported("teleport", "i1"),
                )
            val base = AttentionFixtures.decision()
            env.decisions.put(base.copy(options = listOf(base.options[1].copy(effects = effects))))
            val holder = decisionHolder(env)
            advanceUntilIdle()

            holder.state.value.options
                .single()
                .effects shouldBe
                listOf(
                    EffectSummaryUi(EffectKindUi.CreateIssue, "i1", "Follow-up", false),
                    EffectSummaryUi(EffectKindUi.UpdateIssueStatus, "i1", "done", true),
                    EffectSummaryUi(EffectKindUi.AssignIssue, "i1", "QA agent", false),
                    EffectSummaryUi(EffectKindUi.ResolveBlocker, "i1", "2", false),
                    EffectSummaryUi(EffectKindUi.Unsupported, "i1", "teleport", true),
                )
        }

    @Test
    fun `the feed item's agent name and decide-by are used when the decision has none`() =
        runTest {
            val env = decisionEnv()
            env.decisions.put(AttentionFixtures.decision().copy(originAgentName = null))
            val holder = decisionHolder(env, agentHint = "CTO", decideByHint = "2026-11-09")
            advanceUntilIdle()

            holder.state.value.originAgentName shouldBe "CTO"
            holder.state.value.decideBy shouldBe DecideByUi(DecideByKindUi.OnDate, "2026-11-09")
        }

    @Test
    fun `FR-044 a decision that is not open shows its final state and no options`() =
        runTest {
            val cases =
                mapOf(
                    DecisionStatus.Expired to DecisionFinalKind.Expired,
                    DecisionStatus.Cancelled to DecisionFinalKind.Cancelled,
                    DecisionStatus.Unknown to DecisionFinalKind.Unknown,
                )
            cases.forEach { (status, kind) ->
                val env = decisionEnv()
                env.decisions.put(AttentionFixtures.decision(status = status))
                val holder = decisionHolder(env)
                advanceUntilIdle()

                holder.state.value.finalState
                    ?.kind shouldBe kind
                holder.state.value.options shouldBe emptyList()
                holder.state.value.canConfirm shouldBe false
            }
        }

    @Test
    fun `FR-044 a decision made elsewhere shows which option was chosen and what ran`() =
        runTest {
            val env = decisionEnv()
            env.decisions.decide(
                env.scope,
                com.stainedhead.paperclipmobile.domain
                    .DecisionId("dec-1"),
                "hold",
                emptyMap(),
                "k",
            )
            val holder = decisionHolder(env)
            advanceUntilIdle()

            val final = holder.state.value.finalState
            final?.kind shouldBe DecisionFinalKind.Decided
            final?.chosenOptionLabel shouldBe "Hold"
            holder.state.value.options shouldBe emptyList()
        }

    @Test
    fun `a missing decision is a failed screen with an error key`() =
        runTest {
            val env = decisionEnv()
            env.decisions.failures["get"] = DomainError.NotFound
            val holder = decisionHolder(env)
            advanceUntilIdle()

            holder.state.value.phase shouldBe DetailPhase.Failed
            holder.state.value.error shouldBe ErrorUi(ErrorKey.NotFound)
        }

    @Test
    fun `offline there is nothing to show and no call is made`() =
        runTest {
            val env = decisionEnv()
            env.connectivity.setOnline(false)
            val holder = decisionHolder(env)
            advanceUntilIdle()

            holder.state.value.phase shouldBe DetailPhase.Failed
            holder.state.value.isOffline shouldBe true
            env.decisions.count("get") shouldBe 0
        }

    @Test
    fun `refresh reads the decision again`() =
        runTest {
            val env = decisionEnv()
            env.decisions.failures["get"] = DomainError.Forbidden
            val holder = decisionHolder(env)
            advanceUntilIdle()
            env.decisions.failures.clear()

            holder.onIntent(DecisionIntent.Refresh)
            advanceUntilIdle()

            holder.state.value.phase shouldBe DetailPhase.Content
            holder.state.value.error shouldBe null
        }
}

class DecisionActionsTest {
    @Test
    fun `FR-043 choosing and confirming decides once and shows the per-effect results`() =
        runTest {
            val env = decisionEnv()
            val holder = decisionHolder(env)
            advanceUntilIdle()

            holder.onIntent(DecisionIntent.OptionSelected("ship"))
            runCurrent()
            holder.state.value.selectedOptionId shouldBe "ship"
            holder.state.value.canConfirm shouldBe true
            holder.onIntent(DecisionIntent.ConfirmTapped)
            advanceUntilIdle()

            env.decisions.count("decide") shouldBe 1
            val state = holder.state.value
            state.finalState?.kind shouldBe DecisionFinalKind.Decided
            state.finalState?.chosenOptionLabel shouldBe "Ship it"
            state.execution shouldBe
                ExecutionUi(
                    ExecutionStatusUi.Succeeded,
                    listOf(EffectResultUi(0, "effect", EffectStatusUi.Executed, null)),
                    false,
                )
            state.options shouldBe emptyList()
            state.isSubmitting shouldBe false
        }

    @Test
    fun `FR-043 a skipped effect is reported per effect and is not an overall failure`() =
        runTest {
            val env = decisionEnv()
            env.decisions.effectStatuses = listOf(EffectStatus.Skipped)
            val holder = decisionHolder(env)
            advanceUntilIdle()

            holder.onIntent(DecisionIntent.OptionSelected("ship"))
            holder.onIntent(DecisionIntent.ConfirmTapped)
            advanceUntilIdle()

            holder.state.value.execution
                ?.results
                ?.single()
                ?.status shouldBe EffectStatusUi.Skipped
            holder.state.value.execution
                ?.results
                ?.single()
                ?.reason shouldBe "target_changed"
            holder.state.value.feedback shouldBe null
        }

    @Test
    fun `FR-043 polling shows running until the effects finish`() =
        runTest {
            val env = decisionEnv()
            env.decisions.runningReads = 2
            val holder = decisionHolder(env)
            advanceUntilIdle()

            holder.onIntent(DecisionIntent.OptionSelected("ship"))
            holder.onIntent(DecisionIntent.ConfirmTapped)
            advanceUntilIdle()

            holder.state.value.execution
                ?.status shouldBe ExecutionStatusUi.Succeeded
            holder.state.value.execution
                ?.isPolling shouldBe false
        }

    @Test
    fun `polling that gives up leaves the execution running and not polling`() =
        runTest {
            val env = decisionEnv()
            env.decisions.runningReads = 100
            val holder = decisionHolder(env)
            advanceUntilIdle()

            holder.onIntent(DecisionIntent.OptionSelected("ship"))
            holder.onIntent(DecisionIntent.ConfirmTapped)
            advanceUntilIdle()

            holder.state.value.execution
                ?.status shouldBe ExecutionStatusUi.Running
            holder.state.value.execution
                ?.isPolling shouldBe false
        }

    @Test
    fun `FR-042 inputs honour required and maxLength before anything is sent`() =
        runTest {
            val env = decisionEnv()
            env.decisions.put(
                AttentionFixtures.decision(inputs = listOf(DecisionInput("why", "Why", "Because...", true, 10))),
            )
            val holder = decisionHolder(env)
            advanceUntilIdle()
            holder.state.value.inputs shouldBe listOf(InputUi("why", "Why", "Because...", true, 10, ""))

            holder.onIntent(DecisionIntent.OptionSelected("ship"))
            holder.onIntent(DecisionIntent.ConfirmTapped)
            advanceUntilIdle()
            holder.state.value.feedback shouldBe
                FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.InputRequired, inputId = "why")

            holder.onIntent(DecisionIntent.InputChanged("why", "x".repeat(11)))
            holder.onIntent(DecisionIntent.ConfirmTapped)
            advanceUntilIdle()
            holder.state.value.feedback
                ?.invalid shouldBe InvalidKey.InputTooLong
            env.decisions.count("decide") shouldBe 0

            holder.onIntent(DecisionIntent.InputChanged("why", "ready"))
            holder.onIntent(DecisionIntent.ConfirmTapped)
            advanceUntilIdle()
            env.decisions.decidedWith
                .single()
                .inputValues shouldBe mapOf("why" to "ready")
            holder.state.value.feedback shouldBe null
        }

    @Test
    fun `FR-041 a destructive option asks the device and a refusal sends nothing`() =
        runTest {
            val env = decisionEnv()
            env.biometrics.result = BiometricResult.LockedOut
            val holder = decisionHolder(env)
            advanceUntilIdle()

            holder.onIntent(DecisionIntent.OptionSelected("cancel"))
            runCurrent()
            holder.state.value.confirmNeedsDeviceAuth shouldBe true
            holder.onIntent(DecisionIntent.ConfirmTapped)
            advanceUntilIdle()

            env.biometrics.calls shouldBe 1
            env.decisions.count("decide") shouldBe 0
            holder.state.value.feedback shouldBe
                FeedbackUi(FeedbackKind.DeviceAuth, deviceAuth = DeviceAuthIssue.LockedOut)
            holder.state.value.finalState shouldBe null
        }

    @Test
    fun `acceptance 12 a decision resolved elsewhere shows its final state after the tap`() =
        runTest {
            val env = decisionEnv()
            val holder = decisionHolder(env)
            advanceUntilIdle()
            env.decisions.put(
                AttentionFixtures.decision().copy(status = DecisionStatus.Decided, chosenOptionId = "hold"),
            )

            holder.onIntent(DecisionIntent.OptionSelected("ship"))
            holder.onIntent(DecisionIntent.ConfirmTapped)
            advanceUntilIdle()

            holder.state.value.finalState
                ?.chosenOptionLabel shouldBe "Hold"
            holder.state.value.options shouldBe emptyList()
            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Reconciled, error = ErrorUi(ErrorKey.Conflict))
        }

    @Test
    fun `acceptance 10 a timeout is sent once and the decision is read again`() =
        runTest {
            val env = decisionEnv()
            env.decisions.failures["decide"] = DomainError.Timeout
            val holder = decisionHolder(env)
            advanceUntilIdle()

            holder.onIntent(DecisionIntent.OptionSelected("ship"))
            holder.onIntent(DecisionIntent.ConfirmTapped)
            advanceUntilIdle()

            env.decisions.count("decide") shouldBe 1
            env.decisions.count("get") shouldBe 2
            holder.state.value.feedback
                ?.kind shouldBe FeedbackKind.Reconciled
            holder.state.value.finalState shouldBe null
            holder.state.value.options.size shouldBe 3
        }

    @Test
    fun `a second tap while the decision is in flight is ignored`() =
        runTest {
            val env = decisionEnv()
            val holder = decisionHolder(env)
            advanceUntilIdle()

            holder.onIntent(DecisionIntent.OptionSelected("ship"))
            holder.onIntent(DecisionIntent.ConfirmTapped)
            holder.onIntent(DecisionIntent.ConfirmTapped)
            advanceUntilIdle()

            env.decisions.count("decide") shouldBe 1
        }

    @Test
    fun `confirming with no option selected does nothing`() =
        runTest {
            val env = decisionEnv()
            val holder = decisionHolder(env)
            advanceUntilIdle()

            holder.onIntent(DecisionIntent.ConfirmTapped)
            advanceUntilIdle()

            env.decisions.count("decide") shouldBe 0
            holder.state.value.canConfirm shouldBe false
        }

    @Test
    fun `FR-043 dismiss sends once and shows the dismissed final state`() =
        runTest {
            val env = decisionEnv()
            val holder = decisionHolder(env)
            advanceUntilIdle()

            holder.onIntent(DecisionIntent.DismissTapped)
            advanceUntilIdle()

            env.decisions.count("dismiss") shouldBe 1
            holder.state.value.finalState
                ?.kind shouldBe DecisionFinalKind.Dismissed
            holder.state.value.options shouldBe emptyList()
        }

    @Test
    fun `a viewer sees the decision read-only and no action reaches the gateway`() =
        runTest {
            val env = decisionEnv(Role.Viewer)
            val holder = decisionHolder(env)
            advanceUntilIdle()

            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
            holder.state.value.canConfirm shouldBe false
            holder.onIntent(DecisionIntent.OptionSelected("ship"))
            holder.onIntent(DecisionIntent.ConfirmTapped)
            holder.onIntent(DecisionIntent.DismissTapped)
            advanceUntilIdle()

            env.decisions.count("decide") shouldBe 0
            env.decisions.count("dismiss") shouldBe 0
        }

    @Test
    fun `FR-095 offline the actions are disabled with a reason and ignored`() =
        runTest {
            val env = decisionEnv()
            val holder = decisionHolder(env)
            advanceUntilIdle()
            env.connectivity.setOnline(false)
            advanceUntilIdle()

            holder.onIntent(DecisionIntent.OptionSelected("ship"))
            holder.onIntent(DecisionIntent.ConfirmTapped)
            holder.onIntent(DecisionIntent.DismissTapped)
            advanceUntilIdle()

            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.Offline
            holder.state.value.canConfirm shouldBe false
            env.decisions.count("decide") shouldBe 0
            env.decisions.count("dismiss") shouldBe 0
        }

    @Test
    fun `an unknown option or message intent changes nothing and messages can be dismissed`() =
        runTest {
            val env = decisionEnv()
            env.biometrics.result = BiometricResult.Cancelled
            val holder = decisionHolder(env)
            advanceUntilIdle()
            holder.onIntent(DecisionIntent.OptionSelected("teleport"))
            runCurrent()
            holder.state.value.selectedOptionId shouldBe null

            holder.onIntent(DecisionIntent.OptionSelected("cancel"))
            holder.onIntent(DecisionIntent.ConfirmTapped)
            advanceUntilIdle()
            holder.state.value.feedback
                ?.kind shouldBe FeedbackKind.DeviceAuth
            holder.onIntent(DecisionIntent.MessageDismissed)
            runCurrent()
            holder.state.value.feedback shouldBe null

            holder.close()
            holder.onIntent(DecisionIntent.Refresh)
            advanceUntilIdle()
            env.decisions.count("get") shouldBe 1
        }
}
