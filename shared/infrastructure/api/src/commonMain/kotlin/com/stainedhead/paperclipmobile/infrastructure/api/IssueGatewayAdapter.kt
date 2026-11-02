package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.Attachment
import com.stainedhead.paperclipmobile.domain.Comment
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Issue
import com.stainedhead.paperclipmobile.domain.IssueDetail
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssueQuery
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.NewIssue
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.IssueGateway
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.api.dto.AttachmentDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.CommentDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.IssueDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.isDeleted
import com.stainedhead.paperclipmobile.infrastructure.api.dto.toDetail
import com.stainedhead.paperclipmobile.infrastructure.api.dto.toDomain
import com.stainedhead.paperclipmobile.infrastructure.api.dto.toIssue
import io.ktor.http.HttpMethod

/**
 * Issues and their thread (AD-013). The list and the creation have the company in their path; every other call is
 * addressed by the issue id alone, so a read that proves the issue belongs to the scope's company comes first and a
 * reply that names another company is not trusted (ADR-061). Comments and attachments name their company in each
 * row, which is checked instead. Mutations are sent once and never retried. An id that is not a uuid is NotFound
 * without a request. See ADR-063 for the request bodies and ADR-064 for the attachment content call.
 */
class IssueGatewayAdapter(
    client: PaperclipHttpClient,
    instances: InstanceRepository,
    secrets: SecretStore,
) : IssueGateway {
    private val calls = CompanyCalls(client, instances, secrets)

    /** A slice of an attachment: [start] is the offset of the first byte and [total] the whole size when known. */
    class AttachmentSlice(
        val bytes: ByteArray,
        val start: Long,
        val total: Long?,
        val contentType: String?,
    )

    override suspend fun list(
        scope: CompanyScope,
        query: IssueQuery,
    ): DomainResult<List<Issue>> =
        calls
            .send(scope.instanceId, HttpMethod.Get, inCompany(scope), IssueRequests.listParams(query))
            .decode<List<IssueDto>>()
            .map { rows -> allOrNone(rows) { it.toIssue(withDescription = false) } }

    override suspend fun get(
        scope: CompanyScope,
        id: IssueId,
    ): DomainResult<IssueDetail> {
        if (!isServerId(id.value)) return DomainResult.Failure(DomainError.NotFound)
        val issue =
            calls
                .send(scope.instanceId, HttpMethod.Get, listOf("issues", id.value))
                .decode<IssueDto>()
                .ownedBy(scope) { it.companyId }
        if (issue is DomainResult.Failure) return issue
        val children =
            calls
                .send(
                    scope.instanceId,
                    HttpMethod.Get,
                    inCompany(scope),
                    listOf(
                        "parentId" to id.value,
                        "view" to "compact",
                    ),
                ).decode<List<IssueDto>>()
                .map { rows -> allOrNone(rows) { it.toIssue(withDescription = false) } }
        return when (children) {
            is DomainResult.Failure -> children
            is DomainResult.Success -> issue.map { it.toDetail(children.value) }
        }
    }

    override suspend fun comments(
        scope: CompanyScope,
        id: IssueId,
    ): DomainResult<List<Comment>> {
        if (!isServerId(id.value)) return DomainResult.Failure(DomainError.NotFound)
        return calls
            .send(scope.instanceId, HttpMethod.Get, listOf("issues", id.value, "comments"), listOf("order" to "asc"))
            .decode<List<CommentDto>>()
            .let { rows -> rows.ofCompany(scope) { it.companyId } }
            .map { rows -> allOrNone(rows.filterNot { it.isDeleted() }) { it.toDomain() } }
    }

    override suspend fun addComment(
        scope: CompanyScope,
        id: IssueId,
        body: String,
        options: CommentOptions,
    ): DomainResult<Comment> {
        val owned = calls.requireIssue(scope, id)
        if (owned is DomainResult.Failure) return owned
        return calls
            .send(
                scope.instanceId,
                HttpMethod.Post,
                listOf("issues", id.value, "comments"),
                jsonBody = IssueRequests.comment(body, options),
            ).decode<CommentDto>()
            .ownedBy(scope) { it.companyId }
            .map { it.toDomain() }
    }

    override suspend fun setStatus(
        scope: CompanyScope,
        id: IssueId,
        status: IssueStatus,
    ): DomainResult<Issue> =
        if (status == IssueStatus.Unknown) {
            DomainResult.Failure(DomainError.Rejected("That status is not one the server has"))
        } else {
            patch(scope, id, IssueRequests.status(status.serverName))
        }

    override suspend fun assign(
        scope: CompanyScope,
        id: IssueId,
        assignee: Assignee,
    ): DomainResult<Issue> =
        if (assignee is Assignee.AgentAssignee && !isServerId(assignee.id.value)) {
            DomainResult.Failure(DomainError.Rejected("That agent does not exist"))
        } else {
            patch(scope, id, IssueRequests.assignee(assignee))
        }

    override suspend fun create(
        scope: CompanyScope,
        issue: NewIssue,
    ): DomainResult<Issue> {
        val agent = (issue.assignee as? Assignee.AgentAssignee)?.id?.value
        if (agent != null &&
            !isServerId(agent)
        ) {
            return DomainResult.Failure(DomainError.Rejected("That agent does not exist"))
        }
        val parent = issue.parentId?.let { calls.requireIssue(scope, it) }
        if (parent is DomainResult.Failure) return parent
        val reply =
            calls
                .send(scope.instanceId, HttpMethod.Post, inCompany(scope), jsonBody = IssueRequests.create(issue))
                .decode<IssueDto>()
        // `allowDuplicate` is sent, so a reply that says "deduplicated" is not the issue the person asked for.
        if (reply is DomainResult.Success &&
            reply.value.deduplicated == true
        ) {
            return DomainResult.Failure(DomainError.Conflict)
        }
        return reply.ownedBy(scope) { it.companyId }.map { it.toIssue(withDescription = true) }
    }

    override suspend fun attachments(
        scope: CompanyScope,
        id: IssueId,
    ): DomainResult<List<Attachment>> {
        if (!isServerId(id.value)) return DomainResult.Failure(DomainError.NotFound)
        return calls
            .send(scope.instanceId, HttpMethod.Get, listOf("issues", id.value, "attachments"))
            .decode<List<AttachmentDto>>()
            .let { rows -> rows.ofCompany(scope) { it.companyId } }
            .map { rows -> allOrNone(rows) { it.toDomain() } }
    }

    /**
     * Reads bytes [start] to [endInclusive] (to the end when null) of an attachment of [issueId] with an HTTP Range
     * request, so a large file is never pulled whole. The attachment must be in the issue's own list, which proves it
     * belongs to this company before any byte is requested. A 416 is a [DomainError.Rejected]. This call is not on the
     * port: nothing above infrastructure can reach it yet (ADR-064).
     */
    suspend fun attachmentContent(
        scope: CompanyScope,
        issueId: IssueId,
        attachmentId: String,
        start: Long,
        endInclusive: Long?,
    ): DomainResult<AttachmentSlice> {
        if (start < 0 || (endInclusive != null && endInclusive < start)) {
            return DomainResult.Failure(DomainError.Rejected("The byte range is not valid"))
        }
        if (!isServerId(attachmentId)) return DomainResult.Failure(DomainError.NotFound)
        val listed = attachments(scope, issueId)
        if (listed is DomainResult.Failure) return listed
        if ((listed as DomainResult.Success).value.none { it.id == attachmentId }) {
            return DomainResult.Failure(DomainError.NotFound)
        }
        val range = "bytes=$start-${endInclusive ?: ""}"
        val reply = calls.sendBytes(scope.instanceId, listOf("attachments", attachmentId, "content"), range)
        return when (reply) {
            is DomainResult.Success -> AttachmentRanges.slice(reply.value, start)
            is DomainResult.Failure -> reply
        }
    }

    private suspend fun patch(
        scope: CompanyScope,
        id: IssueId,
        body: String,
    ): DomainResult<Issue> {
        val owned = calls.requireIssue(scope, id)
        if (owned is DomainResult.Failure) return owned
        return calls
            .send(scope.instanceId, HttpMethod.Patch, listOf("issues", id.value), jsonBody = body)
            .decode<IssueDto>()
            .ownedBy(scope) { it.companyId }
            .map { it.toIssue(withDescription = true) }
    }
}

private fun inCompany(scope: CompanyScope) = listOf("companies", scope.companyId.value, "issues")
