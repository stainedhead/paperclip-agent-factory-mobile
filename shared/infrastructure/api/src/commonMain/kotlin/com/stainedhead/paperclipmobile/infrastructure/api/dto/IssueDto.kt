package com.stainedhead.paperclipmobile.infrastructure.api.dto

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.Attachment
import com.stainedhead.paperclipmobile.domain.Comment
import com.stainedhead.paperclipmobile.domain.CommentAuthorType
import com.stainedhead.paperclipmobile.domain.Issue
import com.stainedhead.paperclipmobile.domain.IssueDetail
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssuePriority
import com.stainedhead.paperclipmobile.domain.IssueRef
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.PlanDocument
import com.stainedhead.paperclipmobile.infrastructure.api.parseInstantOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

// Shapes from the pinned release 2026.1023.0 (compiled dist): the compact list row (`server/dist/routes/issues.js:
// 1760-1830`), the detail (`:6423-6502`), `summarizeIssueRelationRow` (`services/issues.js:2129`), the plan documents
// (`services/documents.js:38-113`), a comment row (`db/dist/schema/issue_comments.js`) and an attachment
// (`routes/issues.js:2813-2821`). Anything not declared here is never read.

@Serializable
internal data class IssueRefDto(
    val id: String? = null,
    val identifier: String? = null,
    val title: String? = null,
    val status: String? = null,
)

@Serializable
internal data class ProjectNameDto(
    val name: String? = null,
)

@Serializable
internal data class PlanDocumentDto(
    val title: String? = null,
    val body: String? = null,
    val updatedAt: String? = null,
)

/** An issue as the list, the detail and the writes send it; the extra detail fields are null on a list row. */
@Serializable
internal data class IssueDto(
    val id: String? = null,
    val companyId: String? = null,
    val identifier: String? = null,
    val title: String? = null,
    val description: String? = null,
    val status: String? = null,
    val priority: String? = null,
    val assigneeAgentId: String? = null,
    val assigneeUserId: String? = null,
    val projectId: String? = null,
    val parentId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val deduplicated: Boolean? = null,
    val ancestors: List<IssueRefDto>? = null,
    val blockedBy: List<IssueRefDto>? = null,
    val project: ProjectNameDto? = null,
    val planDocument: PlanDocumentDto? = null,
    val legacyPlanDocument: PlanDocumentDto? = null,
)

/**
 * Null (so Malformed) without an id, a title, a status, a priority and both times. An unknown status or priority is
 * `Unknown`. A row without an identifier (an older issue) shows the start of its id. A list row's description is the
 * server's preview of at most 1200 characters, so [withDescription] is false for lists: only the detail and the
 * replies of writes carry the whole text.
 */
internal fun IssueDto.toIssue(withDescription: Boolean): Issue? {
    val issueId = id?.takeIf { it.isNotBlank() } ?: return null
    val label = title ?: return null
    if (status == null || priority == null) return null
    return Issue(
        id = IssueId(issueId),
        identifier = identifier?.takeIf { it.isNotBlank() } ?: issueId.take(ID_PREFIX),
        title = label,
        status = IssueStatus.fromServer(status),
        priority = IssuePriority.fromServer(priority),
        assigneeAgentId = assigneeAgentId?.takeIf { it.isNotBlank() }?.let(::AgentId),
        assigneeUserId = assigneeUserId?.takeIf { it.isNotBlank() },
        projectId = projectId?.takeIf { it.isNotBlank() },
        parentId = parentId?.takeIf { it.isNotBlank() }?.let(::IssueId),
        createdAt = parseInstantOrNull(createdAt) ?: return null,
        updatedAt = parseInstantOrNull(updatedAt) ?: return null,
        description = if (withDescription) description else null,
    )
}

private const val ID_PREFIX = 8

internal fun IssueRefDto.toDomain(): IssueRef? {
    val refId = id?.takeIf { it.isNotBlank() } ?: return null
    return IssueRef(IssueId(refId), identifier.orEmpty(), title.orEmpty(), IssueStatus.fromServer(status.orEmpty()))
}

/**
 * The detail screen's data: the issue, its nearest ancestor as the parent, its blockers, the project name and the plan
 * (`planDocument`, else the legacy plan from the description). [subtasks] come from a second call by `parentId`.
 */
internal fun IssueDto.toDetail(subtasks: List<Issue>): IssueDetail? {
    val issue = toIssue(withDescription = true) ?: return null
    val parents = ancestors.orEmpty().map { it.toDomain() }
    val blockers = blockedBy.orEmpty().map { it.toDomain() }
    if (parents.any { it == null } || blockers.any { it == null }) return null
    return IssueDetail(
        issue = issue,
        projectName = project?.name?.takeIf { it.isNotBlank() },
        parent = parents.firstOrNull(),
        blockedBy = blockers.filterNotNull(),
        subtasks = subtasks,
        plan = planDocument?.toPlan() ?: legacyPlanDocument?.toPlan(),
    )
}

private fun PlanDocumentDto.toPlan(): PlanDocument? {
    val text = body?.takeIf { it.isNotBlank() } ?: return null
    return PlanDocument(title?.takeIf { it.isNotBlank() }, text, parseInstantOrNull(updatedAt))
}

@Serializable
internal data class CommentDto(
    val id: String? = null,
    val companyId: String? = null,
    val authorType: String? = null,
    val authorAgentId: String? = null,
    val derivedAuthorAgentId: String? = null,
    val authorUserId: String? = null,
    val body: String? = null,
    val deletedAt: String? = null,
    val createdAt: String? = null,
)

/** A comment that was deleted has an empty body and nothing to decide on, so it is not shown. */
internal fun CommentDto.isDeleted() = !deletedAt.isNullOrBlank()

internal fun CommentDto.toDomain(): Comment? {
    val commentId = id?.takeIf { it.isNotBlank() } ?: return null
    val text = body ?: return null
    return Comment(
        id = commentId,
        body = text,
        authorType =
            when (authorType) {
                "user" -> CommentAuthorType.User
                "agent" -> CommentAuthorType.Agent
                else -> CommentAuthorType.System
            },
        authorAgentId = (authorAgentId ?: derivedAuthorAgentId)?.takeIf { it.isNotBlank() }?.let(::AgentId),
        authorUserId = authorUserId?.takeIf { it.isNotBlank() },
        createdAt = parseInstantOrNull(createdAt) ?: return null,
    )
}

@Serializable
internal data class AttachmentDto(
    val id: String? = null,
    val companyId: String? = null,
    val contentType: String? = null,
    val byteSize: JsonPrimitive? = null,
    val originalFilename: String? = null,
    val createdAt: String? = null,
    val contentPath: String? = null,
)

internal fun AttachmentDto.toDomain(): Attachment? {
    val attachmentId = id?.takeIf { it.isNotBlank() } ?: return null
    val size = byteSize?.takeIf { !it.isString }?.longOrNull?.takeIf { it >= 0 } ?: return null
    return Attachment(
        id = attachmentId,
        fileName = originalFilename?.takeIf { it.isNotBlank() },
        contentType = contentType ?: "application/octet-stream",
        byteSize = size,
        createdAt = parseInstantOrNull(createdAt) ?: return null,
        contentPath = contentPath?.takeIf { it.isNotBlank() } ?: "/api/attachments/$attachmentId/content",
    )
}
