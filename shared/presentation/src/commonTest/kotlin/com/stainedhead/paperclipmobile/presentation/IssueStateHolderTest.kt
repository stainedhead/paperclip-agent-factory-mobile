@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

internal fun TestScope.issueHolder(
    env: Env,
    issueId: String = "pap-1182",
) = IssueStateHolder(
    env.issue,
    env.connectivity,
    env.clock,
    CoroutineScope(StandardTestDispatcher(testScheduler)),
    CompanyTarget("inst-1", TestData.acme.value),
    issueId,
)

class IssueLoadTest {
    @Test
    fun `FR-061 the header shows status priority assignee project parent and subtasks`() =
        runTest {
            val env = workEnv()
            val holder = issueHolder(env)
            advanceUntilIdle()

            val state = holder.state.value
            state.phase shouldBe DetailPhase.Content
            state.header?.identifier shouldBe "PAP-1182"
            state.header?.title shouldBe "Ship the billing migration"
            state.header?.status shouldBe IssueStatusUi.InProgress
            state.header?.priority shouldBe IssuePriorityUi.High
            state.header?.assigneeName shouldBe "Engineer 1"
            state.header?.projectName shouldBe "Billing"
            state.subtasks.map { it.identifier } shouldBe listOf("PAP-1190", "PAP-1191")
            state.tab shouldBe IssueTab.Thread
            state.updatedAtEpochMillis shouldBe env.clock.now().toEpochMilliseconds()
            state.plan?.body?.startsWith("1.") shouldBe true
        }

    @Test
    fun `FR-061 a subtask shows its parent`() =
        runTest {
            val env = workEnv()
            val holder = issueHolder(env, "pap-1190")
            advanceUntilIdle()

            holder.state.value.header
                ?.parent
                ?.identifier shouldBe "PAP-1182"
            holder.state.value.plan shouldBe null
        }

    @Test
    fun `FR-061 the thread has comments with author names and interactions in time order`() =
        runTest {
            val env = workEnv()
            val holder = issueHolder(env)
            advanceUntilIdle()

            val thread = holder.state.value.thread
            thread.map { it.id } shouldBe listOf("c1", "c2", "int-confirm", "int-questions")
            thread[0].comment?.authorName shouldBe "Engineer 1"
            thread[0].comment?.authorKind shouldBe CommentAuthorUi.Agent
            thread[1].comment?.isMine shouldBe true
            thread[2].interaction?.kind shouldBe InteractionKindUi.RequestConfirmation
        }

    @Test
    fun `a failed interactions read keeps the comments and says interactions are unavailable`() =
        runTest {
            val env = workEnv()
            env.interactions.failures["list"] = DomainError.ServerError
            val holder = issueHolder(env)
            advanceUntilIdle()

            holder.state.value.phase shouldBe DetailPhase.Content
            holder.state.value.thread
                .map { it.id } shouldBe listOf("c1", "c2")
            holder.state.value.interactionsUnavailable shouldBe true
        }

    @Test
    fun `a missing issue is a failed screen and offline with nothing loaded makes no call`() =
        runTest {
            val env = workEnv()
            val missing = issueHolder(env, "nope")
            advanceUntilIdle()
            missing.state.value.phase shouldBe DetailPhase.Failed
            missing.state.value.error shouldBe ErrorUi(ErrorKey.NotFound)

            val offline = workEnv()
            offline.connectivity.setOnline(false)
            val cold = issueHolder(offline)
            advanceUntilIdle()
            cold.state.value.phase shouldBe DetailPhase.Failed
            cold.state.value.isOffline shouldBe true
            offline.issues.count("get") shouldBe 0
        }
}

class IssueTabsTest {
    @Test
    fun `FR-061 the runs and files tabs load when first opened and the plan tab refreshes`() =
        runTest {
            val env = workEnv()
            val holder = issueHolder(env)
            advanceUntilIdle()
            env.runs.count("forIssue") shouldBe 0
            env.issues.count("attachments") shouldBe 0

            holder.onIntent(IssueIntent.TabSelected(IssueTab.Runs))
            advanceUntilIdle()
            holder.state.value.tab shouldBe IssueTab.Runs
            holder.state.value.runs
                .map { it.runId } shouldBe listOf("run-live", "run-done")
            holder.state.value.runsLoaded shouldBe true

            holder.onIntent(IssueIntent.TabSelected(IssueTab.Files))
            advanceUntilIdle()
            holder.state.value.files
                .map { it.id } shouldBe listOf("att-1", "att-2")
            holder.state.value.files
                .first()
                .name shouldBe "migration.sql"

            holder.onIntent(IssueIntent.TabSelected(IssueTab.Runs))
            advanceUntilIdle()
            env.runs.count("forIssue") shouldBe 1

            val getsBefore = env.issues.count("get")
            holder.onIntent(IssueIntent.TabSelected(IssueTab.Plan))
            advanceUntilIdle()
            env.issues.count("get") shouldBe getsBefore + 1
            holder.state.value.plan
                ?.title shouldBe "Plan"
        }

    @Test
    fun `a failed tab read is shown on that tab and the rest stays`() =
        runTest {
            val env = workEnv()
            env.runs.failures["forIssue"] = DomainError.Forbidden
            val holder = issueHolder(env)
            advanceUntilIdle()

            holder.onIntent(IssueIntent.TabSelected(IssueTab.Runs))
            advanceUntilIdle()

            holder.state.value.tabError shouldBe ErrorUi(ErrorKey.NoPermission)
            holder.state.value.phase shouldBe DetailPhase.Content
            holder.onIntent(IssueIntent.TabSelected(IssueTab.Thread))
            advanceUntilIdle()
            holder.state.value.tabError shouldBe null
        }
}

class IssueEditsTest {
    @Test
    fun `FR-062 an operator comments with options and the thread shows it`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = issueHolder(env)
            advanceUntilIdle()
            holder.state.value.canComment shouldBe true

            holder.onIntent(IssueIntent.CommentSubmitted("Ship it", reopen = false, interrupt = true))
            advanceUntilIdle()

            env.issues.commentOptions
                .single()
                .interrupt shouldBe true
            holder.state.value.thread
                .last { it.comment != null }
                .comment
                ?.body shouldBe "Ship it"
            holder.state.value.feedback shouldBe null
        }

    @Test
    fun `an empty comment is refused with a key and no call`() =
        runTest {
            val env = workEnv()
            val holder = issueHolder(env)
            advanceUntilIdle()

            holder.onIntent(IssueIntent.CommentSubmitted("  ", reopen = false, interrupt = false))
            advanceUntilIdle()

            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.BodyRequired)
            env.issues.count("addComment") shouldBe 0
        }

    @Test
    fun `acceptance 8 a viewer cannot comment change status assign or add a subtask`() =
        runTest {
            val env = workEnv(Role.Viewer)
            val holder = issueHolder(env)
            advanceUntilIdle()
            val state = holder.state.value
            listOf(state.canComment, state.canChangeStatus, state.canAssign, state.canCreateSubtask) shouldBe
                listOf(false, false, false, false)
            state.statusOptions shouldBe emptyList()
            state.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole

            holder.onIntent(IssueIntent.CommentSubmitted("x", reopen = false, interrupt = false))
            holder.onIntent(IssueIntent.StatusSelected(IssueStatusUi.Done))
            holder.onIntent(IssueIntent.AssigneeSelected(AssigneeKindUi.None, null))
            holder.onIntent(IssueIntent.SubtaskSubmitted("sub"))
            advanceUntilIdle()

            listOf("addComment", "setStatus", "assign", "create").all { env.issues.count(it) == 0 } shouldBe true
        }

    @Test
    fun `acceptance 10 a timeout on a comment is sent once, reported unknown and the thread is reloaded`() =
        runTest {
            val env = workEnv()
            env.issues.failures["addComment"] = DomainError.Timeout
            val holder = issueHolder(env)
            advanceUntilIdle()

            holder.onIntent(IssueIntent.CommentSubmitted("maybe", reopen = false, interrupt = false))
            advanceUntilIdle()

            env.issues.count("addComment") shouldBe 1
            // Nothing new by this person is in the thread, so the screen says it cannot tell (F-A-6) and reloads.
            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Unknown, error = ErrorUi(ErrorKey.Timeout))
            env.issues.count("comments") shouldBe 4
        }

    @Test
    fun `reopen is offered on a closed issue and interrupt on one in progress`() =
        runTest {
            val env = workEnv()
            val holder = issueHolder(env)
            advanceUntilIdle()
            holder.state.value.canInterrupt shouldBe true
            holder.state.value.canReopen shouldBe false

            holder.onIntent(IssueIntent.StatusSelected(IssueStatusUi.Done))
            advanceUntilIdle()
            holder.state.value.canReopen shouldBe true
            holder.state.value.canInterrupt shouldBe false
        }

    @Test
    fun `FR-062 status change updates the header and the options exclude the current status`() =
        runTest {
            val env = workEnv()
            val holder = issueHolder(env)
            advanceUntilIdle()
            holder.state.value.statusOptions
                .contains(IssueStatusUi.InProgress) shouldBe false
            holder.state.value.statusOptions
                .contains(IssueStatusUi.Unknown) shouldBe false

            holder.onIntent(IssueIntent.StatusSelected(IssueStatusUi.InReview))
            advanceUntilIdle()

            holder.state.value.header
                ?.status shouldBe IssueStatusUi.InReview
            env.issues.store
                .getValue(IssueId("pap-1182"))
                .status shouldBe IssueStatus.InReview
            holder.onIntent(IssueIntent.StatusSelected(IssueStatusUi.InReview))
            advanceUntilIdle()
            env.issues.count("setStatus") shouldBe 1
        }

    @Test
    fun `FR-062 assign to an agent to me and to nobody`() =
        runTest {
            val env = workEnv()
            val holder = issueHolder(env)
            advanceUntilIdle()
            holder.state.value.assigneeChoices
                .map { it.kind }
                .toSet() shouldBe
                setOf(AssigneeKindUi.None, AssigneeKindUi.Me, AssigneeKindUi.Agent)

            holder.onIntent(IssueIntent.AssigneeSelected(AssigneeKindUi.Agent, "agent-eng-2"))
            advanceUntilIdle()
            holder.state.value.header
                ?.assigneeName shouldBe "Engineer 2"

            holder.onIntent(IssueIntent.AssigneeSelected(AssigneeKindUi.Me, null))
            advanceUntilIdle()
            holder.state.value.header
                ?.assigneeIsMe shouldBe true

            holder.onIntent(IssueIntent.AssigneeSelected(AssigneeKindUi.None, null))
            advanceUntilIdle()
            holder.state.value.header
                ?.assigneeName shouldBe null
            holder.state.value.header
                ?.assigneeIsMe shouldBe false
        }

    @Test
    fun `acceptance 10 a conflict on status shows the server's truth after one call`() =
        runTest {
            val env = workEnv()
            env.issues.failures["setStatus"] = DomainError.Conflict
            val holder = issueHolder(env)
            advanceUntilIdle()

            holder.onIntent(IssueIntent.StatusSelected(IssueStatusUi.Done))
            advanceUntilIdle()

            env.issues.count("setStatus") shouldBe 1
            holder.state.value.header
                ?.status shouldBe IssueStatusUi.InProgress
            holder.state.value.feedback
                ?.kind shouldBe FeedbackKind.Reconciled
        }

    @Test
    fun `FR-062 a subtask is created under this issue and listed`() =
        runTest {
            val env = workEnv()
            val holder = issueHolder(env)
            advanceUntilIdle()

            holder.onIntent(IssueIntent.SubtaskSubmitted("Write the rollback"))
            advanceUntilIdle()

            env.issues.count("create") shouldBe 1
            holder.state.value.subtasks
                .map { it.title }
                .contains("Write the rollback") shouldBe true
            holder.onIntent(IssueIntent.SubtaskSubmitted(" "))
            advanceUntilIdle()
            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.TitleRequired)
            env.issues.count("create") shouldBe 1
        }

    @Test
    fun `acceptance 11 offline disables every edit with a reason and a second submit in flight is ignored`() =
        runTest {
            val env = workEnv()
            val holder = issueHolder(env)
            advanceUntilIdle()
            holder.onIntent(IssueIntent.CommentSubmitted("one", reopen = false, interrupt = false))
            holder.onIntent(IssueIntent.CommentSubmitted("two", reopen = false, interrupt = false))
            advanceUntilIdle()
            env.issues.count("addComment") shouldBe 1

            env.connectivity.setOnline(false)
            advanceUntilIdle()
            val state = holder.state.value
            listOf(state.canComment, state.canChangeStatus, state.canAssign, state.canCreateSubtask) shouldBe
                listOf(false, false, false, false)
            state.actionsDisabledReason shouldBe ActionsDisabledReason.Offline
            holder.onIntent(IssueIntent.CommentSubmitted("three", reopen = false, interrupt = false))
            advanceUntilIdle()
            env.issues.count("addComment") shouldBe 1
            holder.onIntent(IssueIntent.MessageDismissed)
        }
}
