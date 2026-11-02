package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.Decision
import com.stainedhead.paperclipmobile.domain.DecisionEffect
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
 * errors. `decide` on a decided decision replays it for the same key, or for the same option and inputs whatever
 * the key, and is a Conflict for another option or other inputs (`svc/decisions.ts:683-691`). `dismiss` is `decide` on
 * the effect-less option when the decision has one (so a second dismiss replays), else a direct update (a second
 * dismiss conflicts). A `decide` failure can still change the server state through [serverChangesOnFailure].
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

    /** Called with the decision's id once a decide or dismiss took effect, so a demo feed can drop its card. */
    var onResolved: ((String) -> Unit)? = null

    /** Runs after `get` has read the decision and before it answers: the answer is then older than what follows. */
    var afterRead: (suspend () -> Unit)? = null

    data class DecideCall(
        val id: DecisionId,
        val optionId: String,
        val inputValues: Map<String, String>,
        val idempotencyKey: String,
    )

    /** What the "server" holds now, for tests and demo controls. */
    val stored: Map<DecisionId, Decision> get() = store

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
        val snapshot = DomainResult.Success(readWithSettling(id, decision))
        afterRead?.invoke()
        return snapshot
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
        if (!current.isOpen && isReplay(current, optionId, inputValues, idempotencyKey, decidedKeys[id])) {
            return DomainResult.Success(current)
        }
        return applyDecision(current, optionId, inputValues, idempotencyKey)
    }

    private fun applyDecision(
        current: Decision,
        optionId: String,
        inputValues: Map<String, String>,
        idempotencyKey: String?,
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
                outcome = outcomeFor(option, effectStatuses),
            )
        val stored =
            if (runningReads > 0) {
                settling[id] = decided.outcome ?: DecisionOutcome(ExecutionStatus.Succeeded, emptyList())
                decided.copy(outcome = DecisionOutcome(ExecutionStatus.Running, emptyList()))
            } else {
                decided
            }
        store[id] = stored
        if (idempotencyKey != null) decidedKeys[id] = idempotencyKey
        onResolved?.invoke(id.value)
        return DomainResult.Success(stored)
    }

    override suspend fun dismiss(
        scope: CompanyScope,
        id: DecisionId,
        reason: String?,
    ): DomainResult<Decision> {
        val scripted = record("dismiss")
        val current = store[id] ?: return DomainResult.Failure(DomainError.NotFound)
        if (scripted != null) return DomainResult.Failure(scripted)
        val effectLess = current.options.firstOrNull { it.effects.isEmpty() }
        return if (effectLess != null) dismissThroughOption(current, effectLess) else dismissDirectly(current)
    }

    /** `decisions.ts:740-743`: the direct update only matches an open decision. */
    private fun dismissDirectly(current: Decision): DomainResult<Decision> {
        if (!current.isOpen) return DomainResult.Failure(DomainError.Conflict)
        val dismissed =
            current.copy(
                status = DecisionStatus.Decided,
                chosenOptionId = "dismissed",
                dismissed = true,
                outcome = DecisionOutcome(ExecutionStatus.Succeeded, emptyList()),
            )
        store[current.id] = dismissed
        onResolved?.invoke(current.id.value)
        return DomainResult.Success(dismissed)
    }

    /** `decisions.ts:738-739`: a decision with an effect-less option is dismissed by deciding that option. */
    private fun dismissThroughOption(
        current: Decision,
        option: DecisionOption,
    ): DomainResult<Decision> {
        if (!current.isOpen) {
            return if (isReplay(current, option.id, emptyMap(), null, null)) {
                DomainResult.Success(current)
            } else {
                DomainResult.Failure(DomainError.Conflict)
            }
        }
        return when (val decided = applyDecision(current, option.id, emptyMap(), null)) {
            is DomainResult.Success -> {
                val dismissed = decided.value.copy(dismissed = true)
                store[current.id] = dismissed
                DomainResult.Success(dismissed)
            }

            is DomainResult.Failure -> {
                decided
            }
        }
    }
}

/** `decisions.ts:683-690`: the same key, or the same option with the same inputs, replays the stored decision. */
private fun isReplay(
    current: Decision,
    optionId: String,
    inputValues: Map<String, String>,
    idempotencyKey: String?,
    decidedKey: String?,
): Boolean =
    (idempotencyKey != null && decidedKey == idempotencyKey) ||
        (current.chosenOptionId == optionId && current.inputValues == inputValues)

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

internal fun outcomeFor(
    option: DecisionOption,
    effectStatuses: List<EffectStatus>?,
): DecisionOutcome {
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
            EffectResult(
                index,
                option.effects.getOrNull(index)?.serverType() ?: "effect",
                status,
                "target_changed".takeIf {
                    status ==
                        EffectStatus.Skipped
                },
            )
        }
    return DecisionOutcome(execution, results)
}

/** Someone else decided [id] with [optionId]: the next read shows it decided, with its effects done. */
fun FakeDecisionGateway.resolveElsewhere(
    id: DecisionId,
    optionId: String,
) {
    val current = stored[id] ?: return
    val option = current.options.firstOrNull { it.id == optionId } ?: return
    put(
        current.copy(
            status = DecisionStatus.Decided,
            chosenOptionId = optionId,
            outcome = outcomeFor(option, null),
        ),
    )
}

/** The server's name for the effect, as it appears in each effect result. */
private fun DecisionEffect.serverType(): String =
    when (this) {
        is DecisionEffect.CommentOnIssue -> "comment_on_issue"
        is DecisionEffect.CreateIssue -> "create_issue"
        is DecisionEffect.UpdateIssueStatus -> "update_issue_status"
        is DecisionEffect.AssignIssue -> "assign_issue"
        is DecisionEffect.CancelIssueTree -> "cancel_issue_tree"
        is DecisionEffect.ResolveBlocker -> "resolve_blockers"
        is DecisionEffect.Unsupported -> raw
    }
