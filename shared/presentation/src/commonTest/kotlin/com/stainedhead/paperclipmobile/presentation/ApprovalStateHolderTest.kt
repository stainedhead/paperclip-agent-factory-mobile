@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalPayload
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.ApprovalType
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

internal fun TestScope.approvalEnv(role: Role = Role.Owner): Env {
    val env = Env(listOf(TestData.instance())) { testScheduler.currentTime }
    env.auth.meResult = DomainResult.Success(TestData.access(role))
    return env
}

internal fun TestScope.approvalHolder(env: Env) =
    ApprovalStateHolder(
        env.approval,
        env.connectivity,
        env.clock,
        CoroutineScope(StandardTestDispatcher(testScheduler)),
        CompanyTarget("inst-1", TestData.acme.value),
        "ap-1",
    )

class ApprovalLoadingTest {
    @Test
    fun `FR-050 the approval its requester linked issues and comments are shown`() =
        runTest {
            val env = approvalEnv()
            val holder = approvalHolder(env)
            advanceUntilIdle()

            val state = holder.state.value
            state.phase shouldBe DetailPhase.Content
            state.type shouldBe ApprovalTypeUi.HireAgent
            state.status shouldBe ApprovalStatusUi.Pending
            state.requestedByAgentName shouldBe "CEO"
            state.issues shouldBe
                listOf(LinkedIssueUi("iss-1182", "PAP-1182", "Ship the billing migration", "in_review"))
            state.comments.single().body shouldBe "Looks fine to me."
            state.comments.single().isFromAgent shouldBe true
            state.updatedAtEpochMillis shouldBe env.clock.now().toEpochMilliseconds()
        }

    private val hire =
        ApprovalPayload.HireAgent(
            "Designer",
            "designer",
            "Product Designer",
            "claude_local",
            "agent-cto",
            Money.usdCents(20_000),
            listOf("figma"),
            null,
        )
    private val budgetOverride =
        ApprovalPayload.BudgetOverride(
            "Engineer 2",
            "calendar_month_utc",
            Money.usdCents(10_000),
            Money.usdCents(12_000),
            "Raise it",
        )
    private val boardRequest =
        ApprovalPayload.BoardRequest("Rollout", "Ship wide", "Approve", "Roll out", listOf("risk"), "LGTM")

    private val payloadCases =
        listOf(
            Triple(
                ApprovalType.HireAgent,
                hire,
                ApprovalPayloadUi.HireAgent(
                    "Designer",
                    "designer",
                    "Product Designer",
                    "claude_local",
                    "agent-cto",
                    MoneyUi(20_000, "USD"),
                    listOf("figma"),
                    null,
                ),
            ),
            Triple(
                ApprovalType.ApproveCeoStrategy,
                ApprovalPayload.CeoStrategy("Q4 plan", "Grow carefully"),
                ApprovalPayloadUi.CeoStrategy("Q4 plan", "Grow carefully"),
            ),
            Triple(
                ApprovalType.BudgetOverrideRequired,
                budgetOverride,
                ApprovalPayloadUi.BudgetOverride(
                    "Engineer 2",
                    "calendar_month_utc",
                    MoneyUi(10_000, "USD"),
                    MoneyUi(12_000, "USD"),
                    "Raise it",
                ),
            ),
            Triple(
                ApprovalType.RequestBoardApproval,
                boardRequest,
                ApprovalPayloadUi.BoardRequest("Rollout", "Ship wide", "Approve", "Roll out", listOf("risk"), "LGTM"),
            ),
            Triple(
                ApprovalType.Unsupported("sign_contract"),
                ApprovalPayload.Unsupported("sign_contract"),
                ApprovalPayloadUi.Unsupported("sign_contract"),
            ),
        )

    @Test
    fun `FR-050 each approval type has its own payload model`() =
        runTest {
            payloadCases.forEach { (type, payload, expected) ->
                val env = approvalEnv()
                env.approvals.store[ApprovalId("ap-1")] = AttentionFixtures.approval(type = type, payload = payload)
                val holder = approvalHolder(env)
                advanceUntilIdle()

                holder.state.value.payload shouldBe expected
            }
        }

    @Test
    fun `FR-051 a pending approval offers every action and a revision request offers approve and reject`() =
        runTest {
            val env = approvalEnv()
            val holder = approvalHolder(env)
            advanceUntilIdle()
            holder.state.value.actions shouldBe
                listOf(
                    ApprovalActionUi.Approve,
                    ApprovalActionUi.Reject,
                    ApprovalActionUi.RequestRevision,
                    ApprovalActionUi.Comment,
                )
            holder.state.value.rejectNeedsDeviceAuth shouldBe true
            holder.state.value.isResolved shouldBe false

            env.approvals.store[ApprovalId("ap-1")] =
                AttentionFixtures.approval(status = ApprovalStatus.RevisionRequested)
            holder.onIntent(ApprovalIntent.Refresh)
            advanceUntilIdle()
            holder.state.value.actions shouldBe
                listOf(ApprovalActionUi.Approve, ApprovalActionUi.Reject, ApprovalActionUi.Comment)
        }

    @Test
    fun `FR-044 an approval resolved elsewhere shows its final state with only comment left`() =
        runTest {
            val env = approvalEnv()
            env.approvals.store[ApprovalId("ap-1")] =
                AttentionFixtures.approval(status = ApprovalStatus.Rejected).copy(decisionNote = "Too costly")
            val holder = approvalHolder(env)
            advanceUntilIdle()

            holder.state.value.status shouldBe ApprovalStatusUi.Rejected
            holder.state.value.isResolved shouldBe true
            holder.state.value.decisionNote shouldBe "Too costly"
            holder.state.value.actions shouldBe listOf(ApprovalActionUi.Comment)
        }

    @Test
    fun `a missing approval is a failed screen with an error key`() =
        runTest {
            val env = approvalEnv()
            env.approvals.failures["get"] = DomainError.NotFound
            val holder = approvalHolder(env)
            advanceUntilIdle()

            holder.state.value.phase shouldBe DetailPhase.Failed
            holder.state.value.error shouldBe ErrorUi(ErrorKey.NotFound)
        }

    @Test
    fun `offline there is nothing to show and no call is made`() =
        runTest {
            val env = approvalEnv()
            env.connectivity.setOnline(false)
            val holder = approvalHolder(env)
            advanceUntilIdle()

            holder.state.value.phase shouldBe DetailPhase.Failed
            holder.state.value.isOffline shouldBe true
            env.approvals.count("get") shouldBe 0
        }
}

class ApprovalActionsTest {
    @Test
    fun `FR-051 approve sends once without device authentication and shows the new status`() =
        runTest {
            val env = approvalEnv(Role.Operator)
            val holder = approvalHolder(env)
            advanceUntilIdle()

            holder.onIntent(ApprovalIntent.ApproveTapped)
            advanceUntilIdle()

            env.approvals.count("approve") shouldBe 1
            env.biometrics.calls shouldBe 0
            holder.state.value.status shouldBe ApprovalStatusUi.Approved
            holder.state.value.actions shouldBe listOf(ApprovalActionUi.Comment)
            holder.state.value.isSubmitting shouldBe false
        }

    @Test
    fun `FR-051 reject asks for a note then the device then sends once`() =
        runTest {
            val env = approvalEnv()
            val holder = approvalHolder(env)
            advanceUntilIdle()

            holder.onIntent(ApprovalIntent.RejectTapped)
            runCurrent()
            holder.state.value.notePrompt shouldBe NotePromptKind.Reject

            holder.onIntent(ApprovalIntent.NoteConfirmed(" "))
            advanceUntilIdle()
            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.NoteRequired)
            env.biometrics.calls shouldBe 0
            holder.state.value.notePrompt shouldBe NotePromptKind.Reject

            holder.onIntent(ApprovalIntent.NoteConfirmed("too expensive"))
            advanceUntilIdle()
            env.biometrics.calls shouldBe 1
            env.approvals.count("reject") shouldBe 1
            holder.state.value.status shouldBe ApprovalStatusUi.Rejected
            holder.state.value.notePrompt shouldBe null
            holder.state.value.feedback shouldBe null
        }

    @Test
    fun `FR-014 a refused device prompt sends nothing and keeps the note prompt`() =
        runTest {
            val env = approvalEnv()
            env.biometrics.result = BiometricResult.Failed
            val holder = approvalHolder(env)
            advanceUntilIdle()

            holder.onIntent(ApprovalIntent.RejectTapped)
            holder.onIntent(ApprovalIntent.NoteConfirmed("no"))
            advanceUntilIdle()

            env.approvals.count("reject") shouldBe 0
            holder.state.value.feedback shouldBe
                FeedbackUi(FeedbackKind.DeviceAuth, deviceAuth = DeviceAuthIssue.Failed)
            holder.state.value.notePrompt shouldBe NotePromptKind.Reject

            holder.onIntent(ApprovalIntent.NoteCancelled)
            runCurrent()
            holder.state.value.notePrompt shouldBe null
        }

    @Test
    fun `FR-051 request revision needs a note and no device authentication`() =
        runTest {
            val env = approvalEnv(Role.Operator)
            val holder = approvalHolder(env)
            advanceUntilIdle()

            holder.onIntent(ApprovalIntent.RequestRevisionTapped)
            runCurrent()
            holder.state.value.notePrompt shouldBe NotePromptKind.RequestRevision
            holder.onIntent(ApprovalIntent.NoteConfirmed(""))
            advanceUntilIdle()
            holder.state.value.feedback
                ?.invalid shouldBe InvalidKey.NoteRequired

            holder.onIntent(ApprovalIntent.NoteConfirmed("add a budget"))
            advanceUntilIdle()

            env.biometrics.calls shouldBe 0
            holder.state.value.status shouldBe ApprovalStatusUi.RevisionRequested
            holder.state.value.decisionNote shouldBe "add a budget"
            holder.state.value.notePrompt shouldBe null
        }

    @Test
    fun `FR-051 a comment is added to the list and a blank one is refused`() =
        runTest {
            val env = approvalEnv()
            val holder = approvalHolder(env)
            advanceUntilIdle()

            holder.onIntent(ApprovalIntent.CommentSubmitted("  "))
            advanceUntilIdle()
            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.BodyRequired)

            holder.onIntent(ApprovalIntent.CommentSubmitted("Ship it"))
            advanceUntilIdle()

            holder.state.value.comments
                .map { it.body } shouldBe listOf("Looks fine to me.", "Ship it")
            holder.state.value.feedback shouldBe null
        }

    @Test
    fun `acceptance 12 an approval rejected elsewhere shows its final state after the tap`() =
        runTest {
            val env = approvalEnv()
            val holder = approvalHolder(env)
            advanceUntilIdle()
            env.approvals.store[ApprovalId("ap-1")] = AttentionFixtures.approval(status = ApprovalStatus.Rejected)

            holder.onIntent(ApprovalIntent.ApproveTapped)
            advanceUntilIdle()

            holder.state.value.status shouldBe ApprovalStatusUi.Rejected
            holder.state.value.actions shouldBe listOf(ApprovalActionUi.Comment)
            holder.state.value.feedback
                ?.kind shouldBe FeedbackKind.Reconciled
        }

    @Test
    fun `acceptance 10 a timeout is sent once and the approval is read again`() =
        runTest {
            val env = approvalEnv()
            env.approvals.failures["approve"] = DomainError.Timeout
            val holder = approvalHolder(env)
            advanceUntilIdle()

            holder.onIntent(ApprovalIntent.ApproveTapped)
            advanceUntilIdle()

            env.approvals.count("approve") shouldBe 1
            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Reconciled, error = ErrorUi(ErrorKey.Timeout))
            holder.state.value.status shouldBe ApprovalStatusUi.Pending
        }

    @Test
    fun `a second tap while an action is in flight is ignored`() =
        runTest {
            val env = approvalEnv()
            val holder = approvalHolder(env)
            advanceUntilIdle()

            holder.onIntent(ApprovalIntent.ApproveTapped)
            holder.onIntent(ApprovalIntent.ApproveTapped)
            advanceUntilIdle()

            env.approvals.count("approve") shouldBe 1
        }

    @Test
    fun `a viewer sees no actions and nothing reaches the gateway`() =
        runTest {
            val env = approvalEnv(Role.Viewer)
            val holder = approvalHolder(env)
            advanceUntilIdle()
            holder.state.value.actions shouldBe emptyList()
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole

            holder.onIntent(ApprovalIntent.ApproveTapped)
            holder.onIntent(ApprovalIntent.RejectTapped)
            holder.onIntent(ApprovalIntent.RequestRevisionTapped)
            holder.onIntent(ApprovalIntent.CommentSubmitted("hi"))
            advanceUntilIdle()

            env.approvals.count("approve") shouldBe 0
            env.approvals.count("addComment") shouldBe 0
            holder.state.value.notePrompt shouldBe null
        }

    @Test
    fun `FR-095 offline the actions are disabled with a reason and ignored`() =
        runTest {
            val env = approvalEnv()
            val holder = approvalHolder(env)
            advanceUntilIdle()
            env.connectivity.setOnline(false)
            advanceUntilIdle()

            holder.onIntent(ApprovalIntent.ApproveTapped)
            holder.onIntent(ApprovalIntent.RejectTapped)
            advanceUntilIdle()

            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.Offline
            holder.state.value.actions shouldBe emptyList()
            env.approvals.count("approve") shouldBe 0
            holder.state.value.notePrompt shouldBe null
        }

    @Test
    fun `refresh dismissing a message and closing behave`() =
        runTest {
            val env = approvalEnv()
            env.biometrics.result = BiometricResult.Cancelled
            val holder = approvalHolder(env)
            advanceUntilIdle()
            holder.onIntent(ApprovalIntent.RejectTapped)
            holder.onIntent(ApprovalIntent.NoteConfirmed("no"))
            advanceUntilIdle()
            holder.state.value.feedback
                ?.kind shouldBe FeedbackKind.DeviceAuth

            holder.onIntent(ApprovalIntent.MessageDismissed)
            runCurrent()
            holder.state.value.feedback shouldBe null

            holder.close()
            holder.onIntent(ApprovalIntent.Refresh)
            advanceUntilIdle()
            env.approvals.count("get") shouldBe 1
        }
}

class ApprovalCommentTimeoutTest {
    @Test
    fun `comment_timeout_with_own_comment_is_appended_once_and_reported_reconciled`() =
        runTest {
            val env = approvalEnv()
            env.approvals.failures["addComment"] = DomainError.Timeout
            env.approvals.storesCommentOnFailure = true
            val holder = approvalHolder(env)
            advanceUntilIdle()

            holder.onIntent(ApprovalIntent.CommentSubmitted("Please check"))
            advanceUntilIdle()

            holder.state.value.comments
                .map { it.body } shouldBe listOf("Looks fine to me.", "Please check")
            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Reconciled, error = ErrorUi(ErrorKey.Timeout))
            env.approvals.count("addComment") shouldBe 1
        }

    @Test
    fun `a comment timeout with nothing new in the thread says unknown and reloads the thread`() =
        runTest {
            val env = approvalEnv()
            env.approvals.failures["addComment"] = DomainError.Timeout
            val holder = approvalHolder(env)
            advanceUntilIdle()

            holder.onIntent(ApprovalIntent.CommentSubmitted("Please check"))
            advanceUntilIdle()

            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Unknown, error = ErrorUi(ErrorKey.Timeout))
            env.approvals.count("get") shouldBe 2
            holder.state.value.comments.size shouldBe 1
        }
}

/** F-B-7: a read that was answered before an action finished must not replace the action's result. */
class ApprovalStaleLoadTest {
    @Test
    fun `staleLoadDoesNotOverwriteAfterApprove`() =
        runTest {
            val env = approvalEnv()
            val holder = approvalHolder(env)
            advanceUntilIdle()
            env.approvals.afterRead = { delay(5_000) }
            holder.onIntent(ApprovalIntent.Refresh)
            runCurrent()
            env.approvals.afterRead = null

            holder.onIntent(ApprovalIntent.ApproveTapped)
            advanceTimeBy(100)
            holder.state.value.status shouldBe ApprovalStatusUi.Approved

            advanceUntilIdle()

            holder.state.value.status shouldBe ApprovalStatusUi.Approved
            holder.state.value.actions shouldBe listOf(ApprovalActionUi.Comment)
        }
}
