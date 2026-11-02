package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.Agent
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.AgentStatus
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.InteractionId
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.NewIssue
import com.stainedhead.paperclipmobile.domain.PauseReason
import com.stainedhead.paperclipmobile.domain.QuestionAnswer
import com.stainedhead.paperclipmobile.domain.port.FeedQuery
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** AD-050 (d): each mutation is exercised once through the adapters, on data this run seeded. A refused seed fails. */
class LiveMutationsTest {
    private companion object {
        const val NEW_BUDGET_CENTS = 1000L
        const val SETTLE_ATTEMPTS = 30
        const val SETTLE_DELAY_MS = 1000L
    }

    @Test
    fun `an issue is created, commented on, moved through its statuses and assigned`() =
        live {
            val created =
                issues
                    .create(
                        scope,
                        NewIssue("Contract created issue", "Made by the adapter"),
                    ).verified("IssueGateway.create")
            assertEquals("Contract created issue", created.title)
            val id = created.id

            val comment =
                issues
                    .addComment(
                        scope,
                        id,
                        "Contract comment",
                        CommentOptions(),
                    ).verified("IssueGateway.addComment")
            assertEquals("Contract comment", comment.body)
            assertTrue(issues.comments(scope, id).verified("IssueGateway.comments").any { it.id == comment.id })

            assertEquals(
                IssueStatus.InReview,
                issues.setStatus(scope, id, IssueStatus.InReview).verified("IssueGateway.setStatus").status,
            )
            assertEquals(
                IssueStatus.Done,
                issues.setStatus(scope, id, IssueStatus.Done).verified("IssueGateway.setStatus").status,
            )
            issues
                .addComment(
                    scope,
                    id,
                    "Reopening",
                    CommentOptions(reopen = true),
                ).verified("IssueGateway.addComment", "(reopen)")
            assertEquals(
                IssueStatus.Todo,
                issues
                    .get(scope, id)
                    .verified("IssueGateway.get")
                    .issue.status,
            )

            val agent = seeder.agent(scope.companyId.value, "Contract Assignee")
            val assigned =
                issues
                    .assign(scope, id, Assignee.AgentAssignee(AgentId(agent.id)))
                    .verified("IssueGateway.assign")
            assertEquals(agent.id, assigned.assigneeAgentId?.value)
            issues.assign(scope, id, Assignee.None).verified("IssueGateway.assign", "(clear)")
        }

    @Test
    fun `approvals are approved, rejected, sent back for revision and commented on`() =
        live {
            val company = scope.companyId.value
            val toApprove = seeder.approval(company, "Contract approve")
            val toReject = seeder.approval(company, "Contract reject")
            val toRevise = seeder.approval(company, "Contract revise")

            val approved =
                approvals
                    .approve(
                        scope,
                        ApprovalId(toApprove.id),
                        "Looks good",
                    ).verified("ApprovalGateway.approve")
            assertEquals(ApprovalStatus.Approved, approved.status)
            val rejected =
                approvals
                    .reject(
                        scope,
                        ApprovalId(toReject.id),
                        "Not now",
                    ).verified("ApprovalGateway.reject")
            assertEquals(ApprovalStatus.Rejected, rejected.status)
            val revision = ApprovalId(toRevise.id)
            val revised =
                approvals
                    .requestRevision(
                        scope,
                        revision,
                        "Please adjust",
                    ).verified("ApprovalGateway.requestRevision")
            assertEquals(ApprovalStatus.RevisionRequested, revised.status)
            val note = approvals.addComment(scope, revision, "Contract comment").verified("ApprovalGateway.addComment")
            assertTrue(
                approvals.comments(scope, revision).verified("ApprovalGateway.comments").any { it.id == note.id },
            )
        }

    @Test
    fun `an attention card is dismissed, snoozed, restored and given a decide by date`() =
        live {
            val seeded = seeder.approval(scope.companyId.value, "Contract attention approval")
            val item =
                attention
                    .feed(scope, FeedQuery())
                    .verified("AttentionGateway.feed")
                    .items
                    .firstOrNull { it.subject.id == seeded.id }
            assertNotNull(item, "The seeded approval has no card in the feed")
            val key = checkNotNull(item).dismissalKey
            val dismissed = attention.dismiss(scope, key).verified("AttentionGateway.dismiss")
            observe("attention dismiss value") {
                check(dismissed.isActive) { "A dismissal should be active: $dismissed" }
                "${dismissed.kind} active=${dismissed.isActive}"
            }
            attention.restore(scope, key).verified("AttentionGateway.restore")
            val snoozed =
                attention.snooze(scope, key, Clock.System.now() + 1.days).verified("AttentionGateway.snooze")
            observe("attention snooze value") {
                check(snoozed.isActive && snoozed.snoozedUntil != null) { "A snooze should be active: $snoozed" }
                "${snoozed.kind} active=${snoozed.isActive} until=${snoozed.snoozedUntil != null}"
            }
            attention.restore(scope, key).verified("AttentionGateway.restore", "(after snooze)")
            attention
                .setDecideBy(scope, AttentionSourceKind.Approval, seeded.id, DecideBy.ThisWeek)
                .verified("AttentionGateway.setDecideBy")
        }

    @Test
    fun `a pending question is answered through the interaction gateway`() =
        live {
            val issue = seeder.issue(scope.companyId.value, "Contract question issue")
            val interactionId = seeder.question(issue.id)
            val answered =
                interactions
                    .answer(
                        scope,
                        IssueId(issue.id),
                        InteractionId(interactionId),
                        InteractionAnswer.Respond(listOf(QuestionAnswer("go", listOf("yes")))),
                    ).verified("InteractionGateway.answer")
            assertEquals(interactionId, answered.meta.id.value)
            assertFalse(answered.meta.status.isOpen, "The answered question is still open: ${answered.meta.status}")
            val again =
                interactions.answer(
                    scope,
                    IssueId(issue.id),
                    InteractionId(interactionId),
                    InteractionAnswer.Respond(listOf(QuestionAnswer("go", listOf("no")))),
                )
            again.verifiedError("InteractionGateway.answer", DomainError.Conflict)
        }

    @Test
    fun `an agent is paused, resumed and given a budget`() =
        live {
            val seeded = seeder.agent(scope.companyId.value, "Contract Operator")
            val id = AgentId(seeded.id)
            val paused = agents.pause(scope, id).verified("AgentGateway.pause")
            assertEquals(AgentStatus.Paused, paused.status)
            assertEquals(PauseReason.Manual, paused.pauseReason)
            LiveReport.note("pause reply", describe(paused))
            // A resume right after a pause meets a pause that is still being applied (lifecycle "pausing").
            val resumed = agents.resume(scope, id).verified("AgentGateway.resume")
            assertMidTransition(resumed)
            LiveReport.note("resume right after pause, reply", describe(resumed))
            assertSettled(id)
            // A resume of an agent whose pause has settled.
            agents.pause(scope, id).verified("AgentGateway.pause", "(again)")
            LiveReport.note("pause settled", settledWhile(id) { it.lifecycleState != "paused" })
            val resumedLater = agents.resume(scope, id).verified("AgentGateway.resume", "(after the pause settled)")
            assertMidTransition(resumedLater)
            LiveReport.note("resume of a settled pause, reply", describe(resumedLater))
            assertSettled(id)
            val budgeted =
                agents.setBudget(scope, id, Money.usdCents(NEW_BUDGET_CENTS)).verified("AgentGateway.setBudget")
            assertEquals(Money.usdCents(NEW_BUDGET_CENTS), budgeted.monthlyBudget)
        }

    /** The pinned server answers a resume mid-transition; the settled agent is idle on the next read. */
    private fun assertMidTransition(reply: Agent) {
        assertEquals(AgentStatus.Paused, reply.status)
        assertEquals(null, reply.pauseReason)
        assertEquals("resuming", reply.lifecycleState)
    }

    private fun LiveContext.assertSettled(id: AgentId) {
        val outcome = settledWhile(id) { it.status == AgentStatus.Paused }
        LiveReport.note("agent after resume", outcome)
        assertTrue(outcome.contains("status=Idle"), "The agent did not settle to idle: $outcome")
    }

    private fun describe(agent: Agent): String =
        "status=${agent.status} reason=${agent.pauseReason} lifecycle=${agent.lifecycleState}"

    /** Polls the agent until [stillWaiting] is false (at most 30 s) and describes where it ended. */
    private fun LiveContext.settledWhile(
        id: AgentId,
        stillWaiting: (Agent) -> Boolean,
    ): String =
        runBlocking {
            var last: Agent? = null
            repeat(SETTLE_ATTEMPTS) { attempt ->
                last = agents.get(scope, id).verified("AgentGateway.get", "(settle)")
                if (!stillWaiting(
                        checkNotNull(last),
                    )
                ) {
                    return@runBlocking "after ${attempt}s: ${describe(checkNotNull(last))}"
                }
                delay(SETTLE_DELAY_MS)
            }
            "not settled after ${SETTLE_ATTEMPTS}s: ${last?.let(::describe)}"
        }
}
