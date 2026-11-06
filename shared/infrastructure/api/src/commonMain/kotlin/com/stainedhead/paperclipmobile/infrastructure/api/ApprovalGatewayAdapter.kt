package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.Approval
import com.stainedhead.paperclipmobile.domain.ApprovalComment
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.LinkedIssue
import com.stainedhead.paperclipmobile.domain.port.ApprovalGateway
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.api.dto.ApprovalCommentDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.ApprovalDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.LinkedIssueDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.toDomain
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * `/api/approvals/{id}` and its children (AD-012). They carry no company in the path, so every call first proves the
 * approval belongs to the scope's company: a read checks the row it got, a write reads the approval first and only then
 * posts, once (ADR-057). A write is never retried.
 */
class ApprovalGatewayAdapter(
    client: PaperclipHttpClient,
    instances: InstanceRepository,
    secrets: SecretStore,
    keyReadReason: String = "",
) : ApprovalGateway {
    private val calls = CompanyCalls(client, instances, secrets, keyReadReason)

    override suspend fun get(
        scope: CompanyScope,
        id: ApprovalId,
    ): DomainResult<Approval> = fetch(scope, id).map { it.toDomain() }

    override suspend fun linkedIssues(
        scope: CompanyScope,
        id: ApprovalId,
    ): DomainResult<List<LinkedIssue>> {
        if (!isServerId(id.value)) return DomainResult.Failure(DomainError.NotFound)
        val rows = calls.send(scope.instanceId, HttpMethod.Get, path(id, "issues")).decode<List<LinkedIssueDto>>()
        return provingOwned(rows.noneOfOtherCompanies(scope) { it.companyId }, { fetch(scope, id) }) { list ->
            list.map { it.toDomain() }.allOrNull()
        }
    }

    override suspend fun comments(
        scope: CompanyScope,
        id: ApprovalId,
    ): DomainResult<List<ApprovalComment>> {
        if (!isServerId(id.value)) return DomainResult.Failure(DomainError.NotFound)
        val rows = calls.send(scope.instanceId, HttpMethod.Get, path(id, "comments")).decode<List<ApprovalCommentDto>>()
        return provingOwned(rows.noneOfOtherCompanies(scope) { it.companyId }, { fetch(scope, id) }) { list ->
            list.map { it.toDomain() }.allOrNull()
        }
    }

    override suspend fun approve(
        scope: CompanyScope,
        id: ApprovalId,
        note: String?,
    ): DomainResult<Approval> = resolve(scope, id, "approve", note)

    override suspend fun reject(
        scope: CompanyScope,
        id: ApprovalId,
        note: String?,
    ): DomainResult<Approval> = resolve(scope, id, "reject", note)

    override suspend fun requestRevision(
        scope: CompanyScope,
        id: ApprovalId,
        note: String?,
    ): DomainResult<Approval> = resolve(scope, id, "request-revision", note)

    override suspend fun addComment(
        scope: CompanyScope,
        id: ApprovalId,
        body: String,
    ): DomainResult<ApprovalComment> {
        val guard = fetch(scope, id)
        if (guard is DomainResult.Failure) return guard
        val json = buildJsonObject { put("body", body) }.toString()
        return calls
            .send(scope.instanceId, HttpMethod.Post, path(id, "comments"), jsonBody = json)
            .decode<ApprovalCommentDto>()
            .ownedBy(scope) { it.companyId }
            .map { it.toDomain() }
    }

    private suspend fun resolve(
        scope: CompanyScope,
        id: ApprovalId,
        action: String,
        note: String?,
    ): DomainResult<Approval> {
        val guard = fetch(scope, id)
        if (guard is DomainResult.Failure) return guard
        val json = buildJsonObject { note?.takeIf { it.isNotBlank() }?.let { put("decisionNote", it) } }.toString()
        return calls
            .send(scope.instanceId, HttpMethod.Post, path(id, action), jsonBody = json)
            .decode<ApprovalDto>()
            .ownedBy(scope) { it.companyId }
            .map { it.toDomain() }
    }

    private suspend fun fetch(
        scope: CompanyScope,
        id: ApprovalId,
    ): DomainResult<ApprovalDto> {
        if (!isServerId(id.value)) return DomainResult.Failure(DomainError.NotFound)
        return calls
            .send(scope.instanceId, HttpMethod.Get, path(id))
            .decode<ApprovalDto>()
            .ownedBy(scope) { it.companyId }
    }

    private fun path(
        id: ApprovalId,
        vararg more: String,
    ) = listOf("approvals", id.value) + more
}

/** A list is NotFound under [scope] when any row names another company; rows that do not say are not checked. */
internal fun <T> DomainResult<List<T>>.noneOfOtherCompanies(
    scope: CompanyScope,
    companyOf: (T) -> String?,
): DomainResult<List<T>> {
    if (this !is DomainResult.Success) return this
    val foreign = value.any { row -> companyOf(row)?.let { it != scope.companyId.value } == true }
    return if (foreign) DomainResult.Failure(DomainError.NotFound) else this
}

/** The mapped rows, or null (so Malformed) when any one could not be mapped. */
internal fun <T> List<T?>.allOrNull(): List<T>? = if (any { it == null }) null else map { it!! }

/**
 * Rows that name their company prove whose approval it is; an empty list proves nothing, so the approval itself is
 * read once through [owned]: another company's approval is NotFound here, as an issue's empty thread is (review1b
 * F-A-10).
 */
private suspend fun <D, T> provingOwned(
    rows: DomainResult<List<D>>,
    owned: suspend () -> DomainResult<*>,
    map: (List<D>) -> List<T>?,
): DomainResult<List<T>> {
    val mapped = rows.map(map)
    if (mapped !is DomainResult.Success || mapped.value.isNotEmpty()) return mapped
    val read = owned()
    return if (read is DomainResult.Failure) read else mapped
}
