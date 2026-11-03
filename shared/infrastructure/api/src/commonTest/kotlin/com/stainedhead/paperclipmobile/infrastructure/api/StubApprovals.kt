package com.stainedhead.paperclipmobile.infrastructure.api

import io.ktor.http.HttpMethod

internal data class StubApproval(
    val id: String,
    val company: String,
    val type: String = "hire_agent",
    var status: String = "pending",
    val payload: String = ApprovalFixtures.hirePayload,
    var decisionNote: String? = null,
    val requestedByAgentId: String? = "agent-ceo",
    val issues: MutableList<String> = mutableListOf(),
)

internal data class StubComment(
    val id: String,
    val approvalId: String,
    val company: String,
    val body: String,
    val createdAt: String = "2026-11-01T10:00:00.000Z",
)

/**
 * The approval endpoints as the pinned server has them (`routes/approvals.ts:224-543`, state machine
 * `lib/approval-records.ts:7-60`, `services/approvals.ts:87-91`). They carry no company in the path: a resource of a
 * company the key cannot see is a 404 (`authz.ts:182-195`), and a reject or approve from the wrong state is a 422.
 */
internal class StubApprovals {
    val approvals = mutableListOf<StubApproval>()
    val comments = mutableListOf<StubComment>()
    private var commentCounter = 0

    fun handle(request: StubRequest): StubReply? {
        val s = request.segments
        if (s.size < MIN || s[0] != "approvals") return null
        val approval = find(s[1]) ?: return absent(s[1])
        request.resourceRefusal(approval.company, NOT_FOUND)?.let { return it }
        return when {
            s.size == MIN && request.method == HttpMethod.Get -> StubReply.ok(ApprovalFixtures.approval(approval))
            s.size == SUB && request.method == HttpMethod.Get -> read(approval, s[2])
            s.size == SUB && request.method == HttpMethod.Post -> write(request, approval, s[2])
            else -> null
        }
    }

    private fun find(id: String): StubApproval? = if (isStubUuid(id)) approvals.firstOrNull { it.id == id } else null

    /** `router.param("id")` runs a uuid query first: a non-uuid id is a database error, 500 (`approvals.js:30`). */
    private fun absent(id: String) =
        if (isStubUuid(id)) StubReply.notFound(NOT_FOUND) else StubReply.error(500, "Internal server error")

    private fun read(
        approval: StubApproval,
        what: String,
    ): StubReply? =
        when (what) {
            "issues" -> {
                StubReply.ok(approval.issues.joinToString(",", "[", "]"))
            }

            "comments" -> {
                StubReply.ok(
                    comments.filter { it.approvalId == approval.id }.joinToString(",", "[", "]") {
                        ApprovalFixtures.comment(it)
                    },
                )
            }

            else -> {
                null
            }
        }

    private fun write(
        request: StubRequest,
        approval: StubApproval,
        action: String,
    ): StubReply? {
        val body = request.jsonBody() ?: return StubReply.invalid()
        return when (action) {
            "approve" -> resolve(approval, body.string("decisionNote"), "approved")
            "reject" -> resolve(approval, body.string("decisionNote"), "rejected")
            "request-revision" -> requestRevision(approval, body.string("decisionNote"))
            "comments" -> addComment(approval, body.string("body"))
            else -> null
        }
    }

    private fun resolve(
        approval: StubApproval,
        note: String?,
        target: String,
    ): StubReply {
        if (approval.status == target) return StubReply.ok(ApprovalFixtures.approval(approval))
        if (approval.status !in RESOLVABLE) {
            return StubReply.unprocessable("Only pending or revision requested approvals can be $target")
        }
        approval.status = target
        approval.decisionNote = note
        return StubReply.ok(ApprovalFixtures.approval(approval))
    }

    private fun requestRevision(
        approval: StubApproval,
        note: String?,
    ): StubReply {
        if (approval.status != "pending") return StubReply.unprocessable("Only pending approvals can request revision")
        approval.status = "revision_requested"
        approval.decisionNote = note
        return StubReply.ok(ApprovalFixtures.approval(approval))
    }

    private fun addComment(
        approval: StubApproval,
        text: String?,
    ): StubReply {
        if (text.isNullOrEmpty()) return StubReply.invalid()
        commentCounter += 1
        val comment =
            StubComment(
                "c0000000-0000-4000-8000-" + commentCounter.toString().padStart(ID_TAIL, '0'),
                approval.id,
                approval.company,
                text,
            )
        comments += comment
        return StubReply.created(ApprovalFixtures.comment(comment))
    }

    private companion object {
        const val MIN = 2
        const val ID_TAIL = 12
        const val SUB = 3
        const val NOT_FOUND = "Approval not found"
        val RESOLVABLE = setOf("pending", "revision_requested")
    }
}
