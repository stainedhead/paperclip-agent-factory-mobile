package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Interaction
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.InteractionId
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.UnsupportedInteraction
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.InteractionGateway
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.api.dto.companyOfRow
import com.stainedhead.paperclipmobile.infrastructure.api.dto.toInteraction
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonObject

/**
 * Issue-thread interactions (AD-013). The list is addressed by the issue id alone, so each row names its company and
 * a row of another company makes the thread NotFound under this scope (ADR-061). An answer first reads the thread:
 * the kind decides which endpoint the answer may use, an unsupported interaction (`connection_intent`, a kind from a
 * newer server, a tool or secret confirmation) is never sent anything, an answer that does not fit the kind is
 * refused here, and one for an interaction that is no longer pending is a Conflict without a request (ADR-065). The
 * answer itself is sent once and never retried.
 */
class InteractionGatewayAdapter(
    client: PaperclipHttpClient,
    instances: InstanceRepository,
    secrets: SecretStore,
) : InteractionGateway {
    private val calls = CompanyCalls(client, instances, secrets)

    override suspend fun list(
        scope: CompanyScope,
        issueId: IssueId,
    ): DomainResult<List<Interaction>> {
        if (!isServerId(issueId.value)) return DomainResult.Failure(DomainError.NotFound)
        return calls
            .send(scope.instanceId, HttpMethod.Get, listOf("issues", issueId.value, "interactions"))
            .decode<List<JsonObject>>()
            .ofCompany(scope) { it.companyOfRow() }
            .map { rows -> allOrNone(rows) { it.toInteraction() } }
    }

    override suspend fun answer(
        scope: CompanyScope,
        issueId: IssueId,
        interactionId: InteractionId,
        answer: InteractionAnswer,
    ): DomainResult<Interaction> {
        val thread = list(scope, issueId)
        if (thread is DomainResult.Failure) return thread
        val interaction = (thread as DomainResult.Success).value.firstOrNull { it.meta.id == interactionId }
        val refusal = interaction?.let { refusalOf(it, answer) }
        if (interaction == null) return DomainResult.Failure(DomainError.NotFound)
        if (refusal != null) return DomainResult.Failure(refusal)
        val request = InteractionRequests.of(answer)
        val path = listOf("issues", issueId.value, "interactions", interactionId.value, request.action)
        return calls
            .send(scope.instanceId, HttpMethod.Post, path, jsonBody = request.body)
            .decode<JsonObject>()
            .ownedBy(scope) { it.companyOfRow() }
            .map { it.toInteraction() }
    }

    /** Why the answer is not sent, or null: unsupported, wrong for the kind, nothing chosen, or not pending. */
    private fun refusalOf(
        interaction: Interaction,
        answer: InteractionAnswer,
    ): DomainError? =
        when {
            interaction is UnsupportedInteraction -> {
                DomainError.Rejected(
                    "This request can only be answered in the web app",
                )
            }

            !InteractionRequests.fits(
                interaction,
                answer,
            ) -> {
                DomainError.Rejected("That answer does not fit this request")
            }

            answer is InteractionAnswer.AcceptTasks && answer.clientKeys.isEmpty() -> {
                DomainError.Rejected(
                    "Select at least one task",
                )
            }

            !interaction.meta.status.isOpen -> {
                DomainError.Conflict
            }

            else -> {
                null
            }
        }
}
