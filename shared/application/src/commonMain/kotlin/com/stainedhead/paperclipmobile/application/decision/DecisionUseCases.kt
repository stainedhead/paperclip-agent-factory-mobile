package com.stainedhead.paperclipmobile.application.decision

import com.stainedhead.paperclipmobile.application.DeviceAuthGuard
import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.failed
import com.stainedhead.paperclipmobile.application.retryingRead
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.Decision
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Permissions
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.DecisionGateway
import com.stainedhead.paperclipmobile.domain.port.IdGenerator
import kotlinx.coroutines.delay

/** Loads one decision (PRD FR-040). Reads are retried (FR-096). */
class LoadDecision(
    private val gateway: DecisionGateway,
) {
    suspend operator fun invoke(
        scope: CompanyScope,
        id: DecisionId,
    ): DomainResult<Decision> = retryingRead { gateway.get(scope, id) }
}

/**
 * Chooses an option (PRD FR-043). It takes the decision's id, not an entity: the decision is read again first (ADR-032)
 * so that "is this option destructive?" and "is it still open?" come from the server's current copy, never from what
 * a screen loaded earlier. Order (ADR-019): the minimum permission, that read, the permission for the option's real
 * action, input validation, device authentication for a destructive option, then ONE gateway call carrying an
 * idempotency key. A decision that is no longer open is returned as [MutationResult.Reconciled] with a conflict and
 * no call is made. A timeout or a conflict reads the decision again. Never retried.
 */
class DecideDecision(
    private val gateway: DecisionGateway,
    private val deviceAuth: DeviceAuthGuard,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        id: DecisionId,
        optionId: String,
        inputValues: Map<String, String>,
    ): MutationResult<Decision> {
        val ready =
            when (val checked = check(context, id, optionId, inputValues)) {
                is Checked.Refused -> return checked.result
                is Checked.Ready -> checked
            }
        val auth = deviceAuth(ready.action)
        if (auth != BiometricResult.Approved) return MutationResult.NotAuthorized(auth)
        val scope = context.scope
        return when (val result = gateway.decide(scope, ready.decisionId, optionId, inputValues, ids.generate())) {
            is DomainResult.Success -> MutationResult.Done(result.value)
            is DomainResult.Failure -> failed(result.error) { gateway.get(scope, ready.decisionId) }
        }
    }

    private sealed interface Checked {
        data class Refused(
            val result: MutationResult<Decision>,
        ) : Checked

        data class Ready(
            val decisionId: DecisionId,
            val action: Action,
        ) : Checked
    }

    private suspend fun check(
        context: CompanyContext,
        id: DecisionId,
        optionId: String,
        inputValues: Map<String, String>,
    ): Checked {
        if (!Permissions.allows(context.role, Action.DecideOption)) return Checked.Refused(MutationResult.Denied)
        val decision =
            when (val read = gateway.get(context.scope, id)) {
                is DomainResult.Success -> read.value
                is DomainResult.Failure -> return Checked.Refused(MutationResult.Failed(read.error))
            }
        val option = decision.options.firstOrNull { it.id == optionId }
        val action = if (option?.isDestructive == true) Action.DecideDestructiveOption else Action.DecideOption
        val refusal =
            when {
                !Permissions.allows(context.role, action) -> MutationResult.Denied
                !decision.isOpen -> MutationResult.Reconciled(DomainError.Conflict, decision)
                option == null -> MutationResult.Invalid(InvalidInput.UnknownOption)
                else -> decision.validateInputs(inputValues)?.let { MutationResult.Invalid(InvalidInput.Input(it)) }
            }
        return refusal?.let { Checked.Refused(it) } ?: Checked.Ready(decision.id, action)
    }
}

/** Dismisses a decision without choosing an option (PRD FR-043). */
class DismissDecision(
    private val gateway: DecisionGateway,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        decision: Decision,
        reason: String?,
    ): MutationResult<Decision> {
        if (!Permissions.allows(context.role, Action.DismissDecision)) return MutationResult.Denied
        if (!decision.isOpen) return MutationResult.Invalid(InvalidInput.WrongStatus)
        return when (val result = gateway.dismiss(context.scope, decision.id, reason)) {
            is DomainResult.Success -> MutationResult.Done(result.value)
            is DomainResult.Failure -> failed(result.error) { gateway.get(context.scope, decision.id) }
        }
    }
}

/** Reads a decided decision until its effects have finished running (PRD FR-043, research Q4). */
class PollDecisionExecution(
    private val gateway: DecisionGateway,
) {
    suspend operator fun invoke(
        scope: CompanyScope,
        id: DecisionId,
    ): DomainResult<Decision> {
        var read = gateway.get(scope, id)
        var reads = 1
        while (reads < MAX_READS && read.isStillExecuting()) {
            delay(INTERVAL_MS)
            read = gateway.get(scope, id)
            reads += 1
        }
        return read
    }

    private fun DomainResult<Decision>.isStillExecuting(): Boolean =
        this is DomainResult.Success && value.isExecutionPending

    private companion object {
        const val MAX_READS = 10
        const val INTERVAL_MS = 1_000L
    }
}
