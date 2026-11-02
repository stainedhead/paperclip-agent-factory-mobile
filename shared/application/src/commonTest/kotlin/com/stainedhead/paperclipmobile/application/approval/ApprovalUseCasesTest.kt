package com.stainedhead.paperclipmobile.application.approval

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.domain.Approval
import com.stainedhead.paperclipmobile.domain.ApprovalComment
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private val pending = AttentionFixtures.approval()
private val id = ApprovalId("ap-1")

class LoadApprovalTest {
    @Test
    fun `FR-050 the approval its linked issues and its comments are loaded together`() =
        runTest {
            val env = Env()

            val result = LoadApproval(env.approvals)(env.scope, id)

            val detail = (result as DomainResult.Success).value
            detail.approval.id shouldBe id
            detail.issues.single().identifier shouldBe "PAP-1182"
            detail.comments.single().body shouldBe "Looks fine to me."
        }

    @Test
    fun `FR-096 a transient failure is retried three times`() =
        runTest {
            val env = Env()
            env.approvals.failures["get"] = DomainError.Unreachable

            LoadApproval(env.approvals)(env.scope, id)

            env.approvals.count("get") shouldBe 4
        }

    @Test
    fun `a missing approval is not retried and the other reads are skipped`() =
        runTest {
            val env = Env()

            val result = LoadApproval(env.approvals)(env.scope, ApprovalId("nope"))

            (result as DomainResult.Failure).error shouldBe DomainError.NotFound
            env.approvals.count("get") shouldBe 1
            env.approvals.count("linkedIssues") shouldBe 0
        }

    @Test
    fun `a failing comments read fails the whole load`() =
        runTest {
            val env = Env()
            env.approvals.failures["comments"] = DomainError.Forbidden

            (LoadApproval(env.approvals)(env.scope, id) as DomainResult.Failure).error shouldBe DomainError.Forbidden
        }
}

class ApproveApprovalTest {
    @Test
    fun `FR-051 an operator approves without device authentication`() =
        runTest {
            val env = Env()

            val result = ApproveApproval(env.approvals)(env.context(Role.Operator), pending.id, pending.status, null)

            (result as MutationResult.Done).value.status shouldBe ApprovalStatus.Approved
            env.biometrics.calls shouldBe 0
            env.approvals.count("approve") shouldBe 1
        }

    @Test
    fun `an approval waiting on revision can be approved and the note is passed on`() =
        runTest {
            val env = Env()
            val revising = pending.copy(status = ApprovalStatus.RevisionRequested)
            env.approvals.store[id] = revising

            ApproveApproval(env.approvals)(env.context(), revising.id, revising.status, "ok now")

            env.approvals.notes shouldBe listOf("ok now")
        }

    @Test
    fun `a viewer never reaches the gateway`() =
        runTest {
            val env = Env()

            ApproveApproval(env.approvals)(env.context(Role.Viewer), pending.id, pending.status, null) shouldBe
                MutationResult.Denied
            env.approvals.count("approve") shouldBe 0
        }

    @Test
    fun `FR-044 an approval that is already resolved is refused without a gateway call`() =
        runTest {
            val env = Env()

            val result =
                ApproveApproval(
                    env.approvals,
                )(
                    env.context(),
                    pending.copy(status = ApprovalStatus.Approved).id,
                    pending.copy(status = ApprovalStatus.Approved).status,
                    null,
                )

            result shouldBe MutationResult.Invalid(InvalidInput.WrongStatus)
            env.approvals.count("approve") shouldBe 0
        }

    @Test
    fun `acceptance 10 a timeout is attempted once then the approval is read again`() =
        runTest {
            val env = Env()
            env.approvals.failures["approve"] = DomainError.Timeout
            env.approvals.serverChangesOnFailure = { it.copy(status = ApprovalStatus.Approved) }

            val result = ApproveApproval(env.approvals)(env.context(), pending.id, pending.status, null)

            val reconciled = result as MutationResult.Reconciled<Approval>
            reconciled.current.status shouldBe ApprovalStatus.Approved
            env.approvals.count("approve") shouldBe 1
            env.approvals.count("get") shouldBe 1
        }

    @Test
    fun `acceptance 12 an approval rejected elsewhere comes back as its final state`() =
        runTest {
            val env = Env()
            env.approvals.store[id] = pending.copy(status = ApprovalStatus.Rejected)

            val result = ApproveApproval(env.approvals)(env.context(), pending.id, pending.status, null)

            val reconciled = result as MutationResult.Reconciled<Approval>
            reconciled.current.status shouldBe ApprovalStatus.Rejected
            reconciled.error.shouldBeInstanceOf<DomainError.Rejected>()
        }

    @Test
    fun `other failures are reported once without a re-read`() =
        runTest {
            val env = Env()
            env.approvals.failures["approve"] = DomainError.Forbidden

            ApproveApproval(env.approvals)(env.context(), pending.id, pending.status, null) shouldBe
                MutationResult.Failed(DomainError.Forbidden)
            env.approvals.count("get") shouldBe 0
        }
}

class RejectApprovalTest {
    private fun Env.reject() = RejectApproval(approvals, deviceAuth)

    @Test
    fun `FR-051 reject needs a note and asks the device then sends once`() =
        runTest {
            val env = Env()

            val result = env.reject()(env.context(), pending.id, pending.status, "too expensive")

            (result as MutationResult.Done).value.status shouldBe ApprovalStatus.Rejected
            env.biometrics.calls shouldBe 1
            env.approvals.notes shouldBe listOf("too expensive")
        }

    @Test
    fun `a blank note is refused before the device prompt`() =
        runTest {
            val env = Env()

            env.reject()(env.context(), pending.id, pending.status, "   ") shouldBe
                MutationResult.Invalid(InvalidInput.NoteRequired)
            env.reject()(env.context(), pending.id, pending.status, null) shouldBe
                MutationResult.Invalid(InvalidInput.NoteRequired)

            env.biometrics.calls shouldBe 0
            env.approvals.count("reject") shouldBe 0
        }

    @Test
    fun `FR-014 cancel failure lockout and unavailable make zero gateway calls`() =
        runTest {
            listOf(
                BiometricResult.Cancelled,
                BiometricResult.Failed,
                BiometricResult.LockedOut,
                BiometricResult.Unavailable,
            ).forEach { denied ->
                val env = Env()
                env.biometrics.result = denied

                env.reject()(env.context(), pending.id, pending.status, "no") shouldBe
                    MutationResult.NotAuthorized(denied)

                env.approvals.count("reject") shouldBe 0
            }
        }

    @Test
    fun `a viewer never reaches biometrics or the gateway`() =
        runTest {
            val env = Env()

            env.reject()(env.context(Role.Viewer), pending.id, pending.status, "no") shouldBe MutationResult.Denied

            env.biometrics.calls shouldBe 0
            env.approvals.count("reject") shouldBe 0
        }

    @Test
    fun `a resolved approval cannot be rejected`() =
        runTest {
            val env = Env()

            val result =
                env.reject()(
                    env.context(),
                    pending.copy(status = ApprovalStatus.Cancelled).id,
                    pending.copy(status = ApprovalStatus.Cancelled).status,
                    "no",
                )

            result shouldBe MutationResult.Invalid(InvalidInput.WrongStatus)
            env.biometrics.calls shouldBe 0
        }

    @Test
    fun `a timeout is attempted once then the approval is read again`() =
        runTest {
            val env = Env()
            env.approvals.failures["reject"] = DomainError.Timeout

            val result = env.reject()(env.context(), pending.id, pending.status, "no")

            (result as MutationResult.Reconciled<Approval>).current.status shouldBe ApprovalStatus.Pending
            env.approvals.count("reject") shouldBe 1
            env.approvals.count("get") shouldBe 1
        }
}

class RequestRevisionTest {
    @Test
    fun `FR-051 request revision needs a note and works from pending only`() =
        runTest {
            val env = Env()

            val result =
                RequestRevision(env.approvals)(env.context(Role.Operator), pending.id, pending.status, "add a budget")

            (result as MutationResult.Done).value.status shouldBe ApprovalStatus.RevisionRequested
            env.biometrics.calls shouldBe 0
        }

    @Test
    fun `a blank note is refused`() =
        runTest {
            val env = Env()

            RequestRevision(env.approvals)(env.context(), pending.id, pending.status, " ") shouldBe
                MutationResult.Invalid(InvalidInput.NoteRequired)
            env.approvals.count("requestRevision") shouldBe 0
        }

    @Test
    fun `from any other status it is refused without a gateway call`() =
        runTest {
            val env = Env()

            ApprovalStatus.entries.filter { it != ApprovalStatus.Pending }.forEach {
                RequestRevision(
                    env.approvals,
                )(env.context(), pending.copy(status = it).id, pending.copy(status = it).status, "note") shouldBe
                    MutationResult.Invalid(InvalidInput.WrongStatus)
            }
            env.approvals.count("requestRevision") shouldBe 0
        }

    @Test
    fun `a viewer never reaches the gateway`() =
        runTest {
            val env = Env()

            RequestRevision(env.approvals)(env.context(Role.Viewer), pending.id, pending.status, "note") shouldBe
                MutationResult.Denied
            env.approvals.count("requestRevision") shouldBe 0
        }

    @Test
    fun `a conflict comes back as the final state`() =
        runTest {
            val env = Env()
            env.approvals.store[id] = pending.copy(status = ApprovalStatus.Approved)

            val result = RequestRevision(env.approvals)(env.context(), pending.id, pending.status, "note")

            (result as MutationResult.Reconciled<Approval>).current.status shouldBe ApprovalStatus.Approved
        }
}

class CommentOnApprovalTest {
    @Test
    fun `FR-051 a comment is added`() =
        runTest {
            val env = Env()

            val result = CommentOnApproval(env.approvals)(env.context(Role.Operator), id, "Looks good")

            (result as MutationResult.Done<ApprovalComment>).value.body shouldBe "Looks good"
        }

    @Test
    fun `a blank comment is refused`() =
        runTest {
            val env = Env()

            CommentOnApproval(env.approvals)(env.context(), id, "  ") shouldBe
                MutationResult.Invalid(InvalidInput.BodyRequired)
            env.approvals.count("addComment") shouldBe 0
        }

    @Test
    fun `a viewer never reaches the gateway`() =
        runTest {
            val env = Env()

            CommentOnApproval(env.approvals)(env.context(Role.Viewer), id, "hi") shouldBe MutationResult.Denied
            env.approvals.count("addComment") shouldBe 0
        }

    @Test
    fun `a timeout is reported once, never retried, and not claimed as posted`() =
        runTest {
            val env = Env()
            env.approvals.failures["addComment"] = DomainError.Timeout

            CommentOnApproval(env.approvals)(env.context(), id, "hi") shouldBe
                MutationResult.Unknown(DomainError.Timeout)
            env.approvals.count("addComment") shouldBe 1
        }
}
