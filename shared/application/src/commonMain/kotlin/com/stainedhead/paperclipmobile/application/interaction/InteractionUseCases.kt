package com.stainedhead.paperclipmobile.application.interaction

import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.failed
import com.stainedhead.paperclipmobile.application.retryingRead
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Interaction
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.Permissions
import com.stainedhead.paperclipmobile.domain.port.InteractionGateway
import com.stainedhead.paperclipmobile.domain.validate

/** The interactions of an issue's thread (PRD FR-063). Unknown kinds arrive as unsupported interactions. */
class LoadInteractions(
    private val gateway: InteractionGateway,
) {
    suspend operator fun invoke(
        scope: CompanyScope,
        issueId: IssueId,
    ): DomainResult<List<Interaction>> = retryingRead { gateway.list(scope, issueId) }
}

/**
 * Answers an interaction (PRD FR-063). Order (ADR-019, ADR-023): permission, addressee, the answer against the
 * interaction (an unsupported one is never sent), ONE gateway call for the answer's kind. No device authentication:
 * the destructive list of FR-014 has no interaction answer. After a timeout or conflict the thread's interactions are
 * read once and the answered one is returned as the truth.
 */
class RespondToInteraction(
    private val gateway: InteractionGateway,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        issueId: IssueId,
        interaction: Interaction,
        answer: InteractionAnswer,
    ): MutationResult<Interaction> {
        if (!Permissions.allows(context.role, Action.RespondToInteraction)) return MutationResult.Denied
        if (!interaction.meta.audience.allowsUser(context.userId)) {
            return MutationResult.Invalid(InvalidInput.NotAddressedToYou)
        }
        interaction.validate(answer)?.let { return MutationResult.Invalid(InvalidInput.Answer(it)) }
        return when (val result = gateway.answer(context.scope, issueId, interaction.meta.id, answer)) {
            is DomainResult.Success -> MutationResult.Done(result.value)
            is DomainResult.Failure -> failed(result.error) { readAgain(context, issueId, interaction) }
        }
    }

    private suspend fun readAgain(
        context: CompanyContext,
        issueId: IssueId,
        interaction: Interaction,
    ): DomainResult<Interaction> =
        when (val list = gateway.list(context.scope, issueId)) {
            is DomainResult.Failure -> {
                list
            }

            is DomainResult.Success -> {
                list.value
                    .firstOrNull { it.meta.id == interaction.meta.id }
                    ?.let { DomainResult.Success(it) }
                    ?: DomainResult.Failure(DomainError.NotFound)
            }
        }
}
