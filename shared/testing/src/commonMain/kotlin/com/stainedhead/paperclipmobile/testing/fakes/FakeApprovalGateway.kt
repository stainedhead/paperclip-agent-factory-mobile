package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.Approval
import com.stainedhead.paperclipmobile.domain.ApprovalComment
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.LinkedIssue
import com.stainedhead.paperclipmobile.domain.port.ApprovalGateway
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import com.stainedhead.paperclipmobile.testing.TestData

/**
 * An approvals endpoint with the server's state machine (research, approvals): approve and reject work from pending and
 * revision requested, repeating the same decision is an idempotent success, anything else is a 422 (`Rejected`), and
 * request revision works only from pending. [failures] scripts errors by operation name.
 */
class FakeApprovalGateway(
    approvals: List<Approval> = listOf(AttentionFixtures.approval()),
) : ApprovalGateway {
    /** The approvals the "server" holds. Tests change them to model something resolved elsewhere. */
    val store = approvals.associateBy { it.id }.toMutableMap()
    val calls = mutableMapOf<String, Int>()
    val failures = mutableMapOf<String, DomainError>()
    val notes = mutableListOf<String?>()
    var issues: List<LinkedIssue> = listOf(AttentionFixtures.linkedIssue())
    var commentList: MutableList<ApprovalComment> = mutableListOf(AttentionFixtures.approvalComment())

    /** Runs after a scripted failure so a test can model "resolved elsewhere while the call was in flight". */
    var serverChangesOnFailure: ((Approval) -> Approval)? = null

    /** The server stores a comment even though the answer is a scripted failure (a timeout after the write). */
    var storesCommentOnFailure = false

    fun count(name: String): Int = calls[name] ?: 0

    private fun record(name: String): DomainError? {
        calls[name] = count(name) + 1
        return failures[name]
    }

    override suspend fun get(
        scope: CompanyScope,
        id: ApprovalId,
    ): DomainResult<Approval> {
        record("get")?.let { return DomainResult.Failure(it) }
        return store[id]?.let { DomainResult.Success(it) } ?: DomainResult.Failure(DomainError.NotFound)
    }

    override suspend fun linkedIssues(
        scope: CompanyScope,
        id: ApprovalId,
    ): DomainResult<List<LinkedIssue>> {
        record("linkedIssues")?.let { return DomainResult.Failure(it) }
        return DomainResult.Success(issues)
    }

    override suspend fun comments(
        scope: CompanyScope,
        id: ApprovalId,
    ): DomainResult<List<ApprovalComment>> {
        record("comments")?.let { return DomainResult.Failure(it) }
        return DomainResult.Success(commentList.toList())
    }

    override suspend fun approve(
        scope: CompanyScope,
        id: ApprovalId,
        note: String?,
    ) = resolve("approve", id, note, ApprovalStatus.Approved)

    override suspend fun reject(
        scope: CompanyScope,
        id: ApprovalId,
        note: String?,
    ) = resolve("reject", id, note, ApprovalStatus.Rejected)

    override suspend fun requestRevision(
        scope: CompanyScope,
        id: ApprovalId,
        note: String?,
    ): DomainResult<Approval> {
        notes += note
        val scripted = record("requestRevision")
        val current = store[id] ?: return DomainResult.Failure(DomainError.NotFound)
        if (scripted != null) {
            serverChangesOnFailure?.let { store[id] = it(current) }
            return DomainResult.Failure(scripted)
        }
        if (current.status != ApprovalStatus.Pending) {
            return DomainResult.Failure(DomainError.Rejected("Only pending approvals can request revision"))
        }
        return DomainResult.Success(
            current.copy(status = ApprovalStatus.RevisionRequested, decisionNote = note).also {
                store[id] =
                    it
            },
        )
    }

    override suspend fun addComment(
        scope: CompanyScope,
        id: ApprovalId,
        body: String,
    ): DomainResult<ApprovalComment> {
        val scripted = record("addComment")
        if (scripted != null && !storesCommentOnFailure) return DomainResult.Failure(scripted)
        if (id !in store) return DomainResult.Failure(DomainError.NotFound)
        val comment = ApprovalComment("c-${commentList.size + 1}", body, TestData.epoch, authorUserId = "user-1")
        commentList.add(comment)
        return scripted?.let { DomainResult.Failure(it) } ?: DomainResult.Success(comment)
    }

    private fun resolve(
        name: String,
        id: ApprovalId,
        note: String?,
        target: ApprovalStatus,
    ): DomainResult<Approval> {
        notes += note
        val scripted = record(name)
        val current = store[id] ?: return DomainResult.Failure(DomainError.NotFound)
        if (scripted != null) {
            serverChangesOnFailure?.let { store[id] = it(current) }
            return DomainResult.Failure(scripted)
        }
        return when {
            current.status.isResolvable -> {
                DomainResult.Success(current.copy(status = target, decisionNote = note).also { store[id] = it })
            }

            current.status == target -> {
                DomainResult.Success(current)
            }

            else -> {
                DomainResult.Failure(
                    DomainError.Rejected("Only pending or revision requested approvals can be resolved"),
                )
            }
        }
    }
}
