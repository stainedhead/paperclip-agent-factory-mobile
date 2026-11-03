package com.stainedhead.paperclipmobile.infrastructure.api.dto

import com.stainedhead.paperclipmobile.domain.Approval
import com.stainedhead.paperclipmobile.domain.ApprovalComment
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.ApprovalType
import com.stainedhead.paperclipmobile.domain.LinkedIssue
import com.stainedhead.paperclipmobile.infrastructure.api.parseInstantOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Shapes from server/src/routes/approvals.ts and packages/db/src/schema/approvals.ts (research, approvals).
// Re-verified in 1b-4b against the pinned dist: server/dist/routes/approvals.js:180-476, db/dist/schema/approvals.js.

/** An approval row. `payload` is redacted by the server and its shape depends on `type`, so it stays open. */
@Serializable
internal data class ApprovalDto(
    val id: String? = null,
    val companyId: String? = null,
    val type: String? = null,
    val status: String? = null,
    val payload: JsonElement? = null,
    val requestedByAgentId: String? = null,
    val decisionNote: String? = null,
    val decidedAt: String? = null,
    val createdAt: String? = null,
)

@Serializable
internal data class ApprovalCommentDto(
    val id: String? = null,
    val companyId: String? = null,
    val body: String? = null,
    val authorAgentId: String? = null,
    val authorUserId: String? = null,
    val createdAt: String? = null,
)

@Serializable
internal data class LinkedIssueDto(
    val id: String? = null,
    val companyId: String? = null,
    val identifier: String? = null,
    val title: String? = null,
    val status: String? = null,
)

/** Null (so Malformed) when the id, the type or the creation time is missing or unreadable. */
internal fun ApprovalDto.toDomain(): Approval? {
    val created = parseInstantOrNull(createdAt)
    if (id.isNullOrBlank() || type.isNullOrBlank() || created == null) return null
    return Approval(
        id = ApprovalId(id),
        type = ApprovalType.fromServer(type),
        status = status?.let(ApprovalStatus::fromServer) ?: ApprovalStatus.Unknown,
        payload = approvalPayload(type, payload.asObject()),
        createdAt = created,
        requestedByAgentId = requestedByAgentId?.takeIf { it.isNotBlank() },
        decisionNote = decisionNote?.takeIf { it.isNotBlank() },
        decidedAt = parseInstantOrNull(decidedAt),
    )
}

internal fun ApprovalCommentDto.toDomain(): ApprovalComment? {
    val created = parseInstantOrNull(createdAt)
    if (id.isNullOrBlank() || body == null || created == null) return null
    return ApprovalComment(
        id = id,
        body = body,
        createdAt = created,
        authorAgentId = authorAgentId?.takeIf { it.isNotBlank() },
        authorUserId = authorUserId?.takeIf { it.isNotBlank() },
    )
}

internal fun LinkedIssueDto.toDomain(): LinkedIssue? {
    if (id.isNullOrBlank() || title == null) return null
    return LinkedIssue(id, identifier?.takeIf { it.isNotBlank() }, title, status?.takeIf { it.isNotBlank() })
}
