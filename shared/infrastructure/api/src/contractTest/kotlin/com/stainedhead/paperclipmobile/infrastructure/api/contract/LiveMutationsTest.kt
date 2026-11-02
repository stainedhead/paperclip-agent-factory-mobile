package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.InvokeOutcome
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.NewIssue
import com.stainedhead.paperclipmobile.domain.port.FeedQuery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** AD-050 (d): each mutation is exercised once through the adapters, on data this run seeded. */
class LiveMutationsTest {
    private companion object {
        const val NEW_BUDGET_CENTS = 1000L
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
            if (agent != null) {
                val assigned =
                    issues
                        .assign(
                            scope,
                            id,
                            Assignee.AgentAssignee(AgentId(agent.id)),
                        ).verified("IssueGateway.assign")
                assertEquals(agent.id, assigned.assigneeAgentId?.value)
                issues.assign(scope, id, Assignee.None).verified("IssueGateway.assign", "(clear)")
            }
        }

    @Test
    fun `approvals are approved, rejected, sent back for revision and commented on`() =
        live {
            val company = scope.companyId.value
            val toApprove = seeder.approval(company, "Contract approve") ?: return@live
            val toReject = seeder.approval(company, "Contract reject") ?: return@live
            val toRevise = seeder.approval(company, "Contract revise") ?: return@live

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
            val seeded = seeder.approval(scope.companyId.value, "Contract attention approval") ?: return@live
            val item =
                attention
                    .feed(scope, FeedQuery())
                    .verified("AttentionGateway.feed")
                    .items
                    .firstOrNull { it.subject.id == seeded.id }
            if (item == null) {
                LiveReport.note("attention mutations", "the seeded approval has no card in the feed")
                return@live
            }
            soft(
                "AttentionGateway.dismiss",
            ) { attention.dismiss(scope, item.dismissalKey).verified("AttentionGateway.dismiss") }
            soft(
                "AttentionGateway.restore",
            ) { attention.restore(scope, item.dismissalKey).verified("AttentionGateway.restore") }
            soft("AttentionGateway.snooze") {
                attention
                    .snooze(
                        scope,
                        item.dismissalKey,
                        Clock.System.now() + 1.days,
                    ).verified("AttentionGateway.snooze")
            }
            soft(
                "AttentionGateway.restore",
            ) { attention.restore(scope, item.dismissalKey).verified("AttentionGateway.restore") }
            soft("AttentionGateway.setDecideBy") {
                attention
                    .setDecideBy(scope, AttentionSourceKind.Approval, seeded.id, DecideBy.ThisWeek)
                    .verified("AttentionGateway.setDecideBy")
            }
        }

    @Test
    fun `an agent is paused, resumed, woken and given a budget, and its run is read and cancelled`() =
        live {
            val seeded = seeder.agent(scope.companyId.value, "Contract Operator")
            if (seeded == null) {
                LiveReport.note("agent mutations", "no agent could be seeded")
                return@live
            }
            val id = AgentId(seeded.id)
            soft("AgentGateway.pause") { agents.pause(scope, id).verified("AgentGateway.pause") }
            soft("AgentGateway.resume") { agents.resume(scope, id).verified("AgentGateway.resume") }
            soft("AgentGateway.setBudget") {
                agents.setBudget(scope, id, Money.usdCents(NEW_BUDGET_CENTS)).verified("AgentGateway.setBudget")
            }
            val outcome =
                soft(
                    "AgentGateway.invokeHeartbeat",
                ) { agents.invokeHeartbeat(scope, id).verified("AgentGateway.invokeHeartbeat") }
            val run = (outcome as? InvokeOutcome.Started)?.run ?: return@live
            soft("RunGateway.get") { runs.get(scope, run.id).verified("RunGateway.get") }
            soft("RunGateway.events") { runs.events(scope, run.id, 0).verified("RunGateway.events") }
            soft("RunGateway.log") { runs.log(scope, run.id, 0).verified("RunGateway.log") }
            soft("RunGateway.cancel") { runs.cancel(scope, run.id).verified("RunGateway.cancel") }
        }
}
