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
import com.stainedhead.paperclipmobile.domain.DecisionOption
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
 * Chooses an option (PRD FR-043). Order (ADR-019): permission, then the decision and option exist and inputs are valid,
 * then device authentication for a destructive option, then ONE gateway call carrying an idempotency key. A timeout or
 * a conflict reads the decision again so the screen shows what really happened. Never retried.
 */
class DecideDecision(
    private val gateway: DecisionGateway,
    private val deviceAuth: DeviceAuthGuard,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        decision: Decision,
        optionId: String,
        inputValues: Map<String, String>,
    ): MutationResult<Decision> {
        val option = decision.options.firstOrNull { it.id == optionId }
        val action = if (option?.isDestructive == true) Action.DecideDestructiveOption else Action.DecideOption
        if (!Permissions.allows(context.role, action)) return MutationResult.Denied
        val problem = problem(decision, option, inputValues)
        if (problem != null) return MutationResult.Invalid(problem)
        val auth = deviceAuth(action)
        if (auth != BiometricResult.Approved) return MutationResult.NotAuthorized(auth)
        return when (val result = gateway.decide(context.scope, decision.id, optionId, inputValues, ids.generate())) {
            is DomainResult.Success -> MutationResult.Done(result.value)
            is DomainResult.Failure -> failed(result.error) { gateway.get(context.scope, decision.id) }
        }
    }

    private fun problem(
        decision: Decision,
        option: DecisionOption?,
        inputValues: Map<String, String>,
    ): InvalidInput? =
        when {
            !decision.isOpen -> InvalidInput.WrongStatus
            option == null -> InvalidInput.UnknownOption
            else -> decision.validateInputs(inputValues)?.let { InvalidInput.Input(it) }
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
