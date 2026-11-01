package com.stainedhead.paperclipmobile.application.issue

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.IssuePriority
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.NewIssue
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.testing.WorkFixtures
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CommentOnIssueTest {
    private val id = WorkFixtures.pap1182

    @Test
    fun `FR-062 an operator comments and the options reach the gateway`() =
        runTest {
            val env = Env()

            val result =
                CommentOnIssue(
                    env.issues,
                )(env.context(Role.Operator), id, "  looks good  ", CommentOptions(interrupt = true))

            (result as MutationResult.Done).value.body shouldBe "looks good"
            env.issues.commentOptions shouldBe listOf(CommentOptions(interrupt = true))
            env.biometrics.calls shouldBe 0
        }

    @Test
    fun `a viewer never reaches the gateway`() =
        runTest {
            val env = Env()

            val result = CommentOnIssue(env.issues)(env.context(Role.Viewer), id, "hi", CommentOptions())

            result shouldBe MutationResult.Denied
            env.issues.count("addComment") shouldBe 0
        }

    @Test
    fun `an empty comment is refused before any call`() =
        runTest {
            val env = Env()

            val result = CommentOnIssue(env.issues)(env.context(), id, "   ", CommentOptions())

            result shouldBe MutationResult.Invalid(InvalidInput.BodyRequired)
            env.issues.count("addComment") shouldBe 0
        }

    @Test
    fun `FR-096 a timeout is sent once and the thread is read again`() =
        runTest {
            val env = Env()
            env.issues.failures["addComment"] = DomainError.Timeout

            val result = CommentOnIssue(env.issues)(env.context(), id, "hi", CommentOptions())

            (result as MutationResult.Reconciled).error shouldBe DomainError.Timeout
            result.current.id shouldBe "c2"
            env.issues.count("addComment") shouldBe 1
            env.issues.count("comments") shouldBe 1
        }

    @Test
    fun `a forbidden answer is reported and not retried or re-read`() =
        runTest {
            val env = Env()
            env.issues.failures["addComment"] = DomainError.Forbidden

            val result = CommentOnIssue(env.issues)(env.context(), id, "hi", CommentOptions())

            result shouldBe MutationResult.Failed(DomainError.Forbidden)
            env.issues.count("addComment") shouldBe 1
            env.issues.count("comments") shouldBe 0
        }
}

class ChangeIssueStatusTest {
    private val issue = WorkFixtures.issues().first()

    @Test
    fun `FR-062 an operator changes the status`() =
        runTest {
            val env = Env()

            val result = ChangeIssueStatus(env.issues)(env.context(Role.Operator), issue, IssueStatus.InReview)

            (result as MutationResult.Done).value.status shouldBe IssueStatus.InReview
        }

    @Test
    fun `a viewer is denied and the gateway is untouched`() =
        runTest {
            val env = Env()

            ChangeIssueStatus(env.issues)(env.context(Role.Viewer), issue, IssueStatus.Done) shouldBe
                MutationResult.Denied
            env.issues.count("setStatus") shouldBe 0
        }

    @Test
    fun `the same status and an unknown status are refused`() =
        runTest {
            val env = Env()

            ChangeIssueStatus(env.issues)(env.context(), issue, issue.status) shouldBe
                MutationResult.Invalid(InvalidInput.WrongStatus)
            ChangeIssueStatus(env.issues)(env.context(), issue, IssueStatus.Unknown) shouldBe
                MutationResult.Invalid(InvalidInput.WrongStatus)
            env.issues.count("setStatus") shouldBe 0
        }

    @Test
    fun `FR-096 a conflict is sent once and the issue is read again`() =
        runTest {
            val env = Env()
            env.issues.failures["setStatus"] = DomainError.Conflict

            val result = ChangeIssueStatus(env.issues)(env.context(), issue, IssueStatus.Done)

            (result as MutationResult.Reconciled).current.status shouldBe IssueStatus.InProgress
            env.issues.count("setStatus") shouldBe 1
            env.issues.count("get") shouldBe 1
        }
}

class AssignIssueTest {
    private val issue = WorkFixtures.issues().first()

    @Test
    fun `FR-062 an issue is assigned to an agent or cleared`() =
        runTest {
            val env = Env()

            val to = AssignIssue(env.issues)(env.context(), issue, Assignee.UserAssignee("user-1"))
            (to as MutationResult.Done).value.assigneeUserId shouldBe "user-1"
            val cleared = AssignIssue(env.issues)(env.context(), issue, Assignee.None)
            (cleared as MutationResult.Done).value.assigneeAgentId shouldBe null
        }

    @Test
    fun `a viewer is denied and a timeout re-reads once`() =
        runTest {
            val env = Env()
            AssignIssue(env.issues)(env.context(Role.Viewer), issue, Assignee.None) shouldBe MutationResult.Denied
            env.issues.count("assign") shouldBe 0

            env.issues.failures["assign"] = DomainError.Timeout
            val result = AssignIssue(env.issues)(env.context(), issue, Assignee.None)
            (result as MutationResult.Reconciled).current.id shouldBe issue.id
            env.issues.count("assign") shouldBe 1
            env.issues.count("get") shouldBe 1
        }
}

class CreateIssueTest {
    @Test
    fun `FR-062 a new issue and a subtask are created with the trimmed title`() =
        runTest {
            val env = Env()

            val created =
                CreateIssue(env.issues)(env.context(), NewIssue("  Plan the rollout ", priority = IssuePriority.High))
            (created as MutationResult.Done).value.title shouldBe "Plan the rollout"
            created.value.priority shouldBe IssuePriority.High

            val sub = CreateIssue(env.issues)(env.context(), NewIssue("Sub", parentId = WorkFixtures.pap1182))
            (sub as MutationResult.Done).value.parentId shouldBe WorkFixtures.pap1182
        }

    @Test
    fun `a viewer is denied and a blank title is refused with no call`() =
        runTest {
            val env = Env()

            CreateIssue(env.issues)(env.context(Role.Viewer), NewIssue("x")) shouldBe MutationResult.Denied
            CreateIssue(env.issues)(env.context(), NewIssue("  ")) shouldBe
                MutationResult.Invalid(InvalidInput.TitleRequired)
            env.issues.count("create") shouldBe 0
        }

    @Test
    fun `a timeout is reported once because a create has no entity to read again`() =
        runTest {
            val env = Env()
            env.issues.failures["create"] = DomainError.Timeout

            val result = CreateIssue(env.issues)(env.context(), NewIssue("x"))

            result shouldBe MutationResult.Failed(DomainError.Timeout)
            env.issues.count("create") shouldBe 1
        }
}
