package com.stainedhead.paperclipmobile.application.issue

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.approval.CommentOnApproval
import com.stainedhead.paperclipmobile.domain.ApprovalComment
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.Comment
import com.stainedhead.paperclipmobile.domain.CommentAuthorType
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** F-A-6: after a timeout a comment is reported posted only when this person's new comment is in the thread. */
class CommentOnIssueReconcileTest {
    private val issue = IssueId("pap-1182")

    private suspend fun Env.comment() = CommentOnIssue(issues)(context(), issue, "Looks good", CommentOptions())

    private fun foreign(id: String) =
        Comment(id, "Looks good", CommentAuthorType.User, null, "user-someone-else", TestData.epoch)

    @Test
    fun `comment_timeout_with_own_comment_is_reported_posted`() =
        runTest {
            val env = Env()
            env.issues.failures["addComment"] = DomainError.Timeout
            env.issues.storesCommentOnFailure = true

            val result = env.comment().shouldBeInstanceOf<MutationResult.Reconciled<Comment>>()

            result.current.body shouldBe "Looks good"
            result.current.authorUserId shouldBe "user-1"
        }

    @Test
    fun `comment_timeout_with_foreign_newest_comment_is_not_reported_as_own`() =
        runTest {
            val env = Env()
            env.issues.failures["addComment"] = DomainError.Timeout
            env.issues.commentStore.getValue(issue) += foreign("c-foreign")

            env.comment() shouldBe MutationResult.Unknown(DomainError.Timeout)
        }

    @Test
    fun `a comment of mine that existed before the tap is not mistaken for the new one`() =
        runTest {
            val env = Env()
            env.issues.failures["addComment"] = DomainError.Timeout
            env.issues.commentStore.getValue(issue) +=
                foreign("c-mine").copy(authorUserId = "user-1")

            env.comment() shouldBe MutationResult.Unknown(DomainError.Timeout)
        }

    @Test
    fun `a timeout whose thread cannot be read is the original failure`() =
        runTest {
            val env = Env()
            env.issues.failures["addComment"] = DomainError.Timeout
            env.issues.failures["comments"] = DomainError.Unreachable

            env.comment() shouldBe MutationResult.Failed(DomainError.Timeout)
        }
}

class CommentOnApprovalReconcileTest {
    private val id = ApprovalId("ap-1")

    private suspend fun Env.comment() = CommentOnApproval(approvals)(context(), id, "Please check")

    @Test
    fun `comment_timeout_with_own_comment_is_reported_posted`() =
        runTest {
            val env = Env()
            env.approvals.failures["addComment"] = DomainError.Timeout
            env.approvals.storesCommentOnFailure = true

            val result = env.comment().shouldBeInstanceOf<MutationResult.Reconciled<ApprovalComment>>()

            result.current.body shouldBe "Please check"
        }

    @Test
    fun `comment_timeout_with_foreign_newest_comment_is_not_reported_as_own`() =
        runTest {
            val env = Env()
            env.approvals.failures["addComment"] = DomainError.Timeout
            env.approvals.commentList +=
                ApprovalComment("c-foreign", "Please check", TestData.epoch, authorUserId = "user-someone-else")

            env.comment() shouldBe MutationResult.Unknown(DomainError.Timeout)
        }

    @Test
    fun `a non-timeout failure is not reconciled`() =
        runTest {
            val env = Env()
            env.approvals.failures["addComment"] = DomainError.Forbidden

            env.comment() shouldBe MutationResult.Failed(DomainError.Forbidden)
        }
}
