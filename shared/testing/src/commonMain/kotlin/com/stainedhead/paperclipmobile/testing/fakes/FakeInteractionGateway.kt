package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.AskUserQuestions
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Interaction
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.InteractionId
import com.stainedhead.paperclipmobile.domain.InteractionMeta
import com.stainedhead.paperclipmobile.domain.InteractionStatus
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.RequestCheckboxConfirmation
import com.stainedhead.paperclipmobile.domain.RequestConfirmation
import com.stainedhead.paperclipmobile.domain.RequestItemVerdicts
import com.stainedhead.paperclipmobile.domain.SuggestTasks
import com.stainedhead.paperclipmobile.domain.UnsupportedInteraction
import com.stainedhead.paperclipmobile.domain.port.InteractionGateway
import com.stainedhead.paperclipmobile.testing.WorkFixtures

/**
 * Interactions with the server's rules (research W7): only a pending interaction can be answered (else 409), an answer
 * of the wrong kind is a 422 `Rejected`, accept/reject give accepted/rejected, respond and verdicts give answered, skip
 * gives cancelled. [failures] scripts errors by operation name.
 */
class FakeInteractionGateway(
    interactions: List<Interaction> = WorkFixtures.interactions(),
    otherIssues: Map<IssueId, List<Interaction>> = emptyMap(),
) : InteractionGateway {
    val store =
        (mapOf(WorkFixtures.pap1182 to interactions) + otherIssues)
            .mapValues { it.value.toMutableList() }
            .toMutableMap()
    val calls = mutableMapOf<String, Int>()
    val failures = mutableMapOf<String, DomainError>()
    val answers = mutableListOf<InteractionAnswer>()

    fun count(name: String): Int = calls[name] ?: 0

    private fun record(name: String): DomainError? {
        calls[name] = count(name) + 1
        return failures[name]
    }

    override suspend fun list(
        scope: CompanyScope,
        issueId: IssueId,
    ): DomainResult<List<Interaction>> {
        record("list")?.let { return DomainResult.Failure(it) }
        return DomainResult.Success(store[issueId].orEmpty().toList())
    }

    override suspend fun answer(
        scope: CompanyScope,
        issueId: IssueId,
        interactionId: InteractionId,
        answer: InteractionAnswer,
    ): DomainResult<Interaction> {
        answers += answer
        record("answer")?.let { return DomainResult.Failure(it) }
        val list = store[issueId] ?: return DomainResult.Failure(DomainError.NotFound)
        val index = list.indexOfFirst { it.meta.id == interactionId }
        if (index < 0) return DomainResult.Failure(DomainError.NotFound)
        val current = list[index]
        val status = statusFor(current, answer)
        return when {
            !current.meta.status.isOpen -> DomainResult.Failure(DomainError.Conflict)
            status == null -> DomainResult.Failure(DomainError.Rejected("This interaction cannot take that answer"))
            else -> DomainResult.Success(current.withStatus(status).also { list[index] = it })
        }
    }

    private fun statusFor(
        current: Interaction,
        answer: InteractionAnswer,
    ): InteractionStatus? =
        when {
            current is UnsupportedInteraction -> null
            answer is InteractionAnswer.Skip -> InteractionStatus.Cancelled
            answer is InteractionAnswer.Reject -> InteractionStatus.Rejected.takeIf { current.canReject() }
            else -> InteractionStatus.Answered.takeIf { current.takes(answer) }?.let { acceptedOrAnswered(answer) }
        }

    private fun acceptedOrAnswered(answer: InteractionAnswer) =
        if (answer is InteractionAnswer.Respond || answer is InteractionAnswer.SubmitVerdicts) {
            InteractionStatus.Answered
        } else {
            InteractionStatus.Accepted
        }
}

private fun Interaction.canReject() =
    this is SuggestTasks || this is RequestConfirmation || this is RequestCheckboxConfirmation

private fun Interaction.takes(answer: InteractionAnswer) =
    when (this) {
        is SuggestTasks -> answer is InteractionAnswer.AcceptTasks
        is RequestConfirmation -> answer is InteractionAnswer.Accept
        is RequestCheckboxConfirmation -> answer is InteractionAnswer.AcceptOptions
        is AskUserQuestions -> answer is InteractionAnswer.Respond
        is RequestItemVerdicts -> answer is InteractionAnswer.SubmitVerdicts
        is UnsupportedInteraction -> false
    }

private fun Interaction.withStatus(status: InteractionStatus): Interaction {
    val updated: InteractionMeta = meta.copy(status = status)
    return when (this) {
        is SuggestTasks -> copy(meta = updated)
        is AskUserQuestions -> copy(meta = updated)
        is RequestConfirmation -> copy(meta = updated)
        is RequestCheckboxConfirmation -> copy(meta = updated)
        is RequestItemVerdicts -> copy(meta = updated)
        is UnsupportedInteraction -> copy(meta = updated)
    }
}
