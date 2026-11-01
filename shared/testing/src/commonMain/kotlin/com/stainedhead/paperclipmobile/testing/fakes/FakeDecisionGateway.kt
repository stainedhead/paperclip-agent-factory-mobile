package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.Decision
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.DecisionOption
import com.stainedhead.paperclipmobile.domain.DecisionOutcome
import com.stainedhead.paperclipmobile.domain.DecisionStatus
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.EffectResult
import com.stainedhead.paperclipmobile.domain.EffectStatus
import com.stainedhead.paperclipmobile.domain.ExecutionStatus
import com.stainedhead.paperclipmobile.domain.port.DecisionGateway
import com.stainedhead.paperclipmobile.testing.AttentionFixtures

/**
 * A decision endpoint with the server's rules (research Q4/Q8): deciding a non-open decision is a Conflict, unknown
 * options and bad inputs are Rejected, and the response carries per-effect results. [runningReads] makes the first
 * N reads of a decided decision report `running`, so polling can be tested. [failures] and [afterFailureHook] script
 * errors. A replay of `decide` with the key that decided it returns the decision (`svc/decisions.ts:690-696`). A
 * `decide` failure can still change the server state through [serverChangesOnFailure].
 */
class FakeDecisionGateway(
    decisions: List<Decision> = listOf(AttentionFixtures.decision()),
) : DecisionGateway {
    private val store = decisions.associateBy { it.id }.toMutableMap()
    val calls = mutableMapOf<String, Int>()
    val failures = mutableMapOf<String, DomainError>()
    val decidedWith = mutableListOf<DecideCall>()
    var effectStatuses: List<EffectStatus>? = null
    var runningReads = 0
    private val settling = mutableMapOf<DecisionId, DecisionOutcome>()
    private val decidedKeys = mutableMapOf<DecisionId, String>()
    var serverChangesOnFailure: ((Decision) -> Decision)? = null

    data class DecideCall(
        val id: DecisionId,
        val optionId: String,
        val inputValues: Map<String, String>,
        val idempotencyKey: String,
    )

    fun count(name: String): Int = calls[name] ?: 0

    fun put(decision: Decision) {
        store[decision.id] = decision
    }

    private fun record(name: String): DomainError? {
        calls[name] = count(name) + 1
        return failures[name]
    }

    override suspend fun get(
        scope: CompanyScope,
        id: DecisionId,
    ): DomainResult<Decision> {
        record("get")?.let { return DomainResult.Failure(it) }
        val decision = store[id] ?: return DomainResult.Failure(DomainError.NotFound)
        return DomainResult.Success(readWithSettling(id, decision))
    }

    /** What a read sees while the effects are still running, and the settled decision afterwards. */
    private fun readWithSettling(
        id: DecisionId,
        decision: Decision,
    ): Decision {
        val final = settling[id]
        val running = DecisionOutcome(ExecutionStatus.Running, emptyList())
        return when {
            final != null && runningReads > 0 -> {
                decision.also { runningReads -= 1 }
            }

            final != null -> {
                decision.copy(outcome = final).also {
                    settling.remove(id)
                    store[id] = it
                }
            }

            decision.status == DecisionStatus.Decided && runningReads > 0 && decision.outcome?.isTerminal == true -> {
                decision.copy(outcome = running).also { runningReads -= 1 }
            }

            else -> {
                decision
            }
        }
    }

    override suspend fun decide(
        scope: CompanyScope,
        id: DecisionId,
        optionId: String,
        inputValues: Map<String, String>,
        idempotencyKey: String,
    ): DomainResult<Decision> {
        decidedWith += DecideCall(id, optionId, inputValues, idempotencyKey)
        val scripted = record("decide")
        val current = store[id] ?: return DomainResult.Failure(DomainError.NotFound)
        if (scripted != null) {
            serverChangesOnFailure?.let { store[id] = it(current) }
            return DomainResult.Failure(scripted)
        }
        if (!current.isOpen && decidedKeys[id] == idempotencyKey) return DomainResult.Success(current)
        return applyDecision(current, optionId, inputValues, idempotencyKey)
    }

    private fun applyDecision(
        current: Decision,
        optionId: String,
        inputValues: Map<String, String>,
        idempotencyKey: String,
    ): DomainResult<Decision> {
        val id = current.id
        val option = current.options.firstOrNull { it.id == optionId }
        val problem = problem(current, option, inputValues)
        if (problem != null || option == null) return DomainResult.Failure(problem ?: DomainError.NotFound)
        val decided =
            current.copy(
                status = DecisionStatus.Decided,
                chosenOptionId = optionId,
                inputValues = inputValues,
                outcome = outcomeFor(option),
            )
        val stored =
            if (runningReads > 0) {
                settling[id] = decided.outcome ?: DecisionOutcome(ExecutionStatus.Succeeded, emptyList())
                decided.copy(outcome = DecisionOutcome(ExecutionStatus.Running, emptyList()))
            } else {
                decided
            }
        store[id] = stored
        decidedKeys[id] = idempotencyKey
        return DomainResult.Success(stored)
    }

    private fun problem(
        current: Decision,
        option: DecisionOption?,
        inputValues: Map<String, String>,
    ): DomainError? =
        when {
            !current.isOpen -> DomainError.Conflict
            option == null -> DomainError.Rejected("Unknown optionId")
            else -> current.validateInputs(inputValues)?.let { DomainError.Rejected("Input ${it.inputId} is invalid") }
        }

    private fun outcomeFor(option: DecisionOption): DecisionOutcome {
        val statuses = effectStatuses ?: option.effects.map { EffectStatus.Executed }
        val executed = statuses.count { it == EffectStatus.Executed }
        val execution =
            when {
                statuses.all { it == EffectStatus.Executed } -> ExecutionStatus.Succeeded
                executed > 0 -> ExecutionStatus.Partial
                else -> ExecutionStatus.Failed
            }
        val results =
            statuses.mapIndexed { index, status ->
                EffectResult(index, "effect", status, "target_changed".takeIf { status == EffectStatus.Skipped })
            }
        return DecisionOutcome(execution, results)
    }

    override suspend fun dismiss(
        scope: CompanyScope,
        id: DecisionId,
        reason: String?,
    ): DomainResult<Decision> {
        val scripted = record("dismiss")
        val current = store[id] ?: return DomainResult.Failure(DomainError.NotFound)
        if (scripted != null) return DomainResult.Failure(scripted)
        if (!current.isOpen) return DomainResult.Failure(DomainError.Conflict)
        val dismissed =
            current.copy(
                status = DecisionStatus.Decided,
                chosenOptionId = "dismissed",
                dismissed = true,
                outcome = DecisionOutcome(ExecutionStatus.Succeeded, emptyList()),
            )
        store[id] = dismissed
        return DomainResult.Success(dismissed)
    }
}
