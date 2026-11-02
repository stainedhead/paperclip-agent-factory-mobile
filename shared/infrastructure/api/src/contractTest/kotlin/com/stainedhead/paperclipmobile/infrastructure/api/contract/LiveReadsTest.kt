package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.AskUserQuestions
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.IssueFilter
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssueQuery
import com.stainedhead.paperclipmobile.domain.port.FeedQuery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AD-050 (c): one read per gateway against data this run seeded. A read counts as verified only when it decoded
 * something to compare (F-C-2), so every test seeds what its read returns.
 */
class LiveReadsTest {
    private companion object {
        const val SPEND_CENTS = 150
        const val ATTACHMENT_TEXT = "contract attachment text"
    }

    @Test
    fun `the company dashboard decodes and the window spend shows a seeded cost event`() =
        live {
            val biller = seeder.agent(scope.companyId.value, "Contract Biller")
            seeder.costEvent(scope.companyId.value, biller.id, SPEND_CENTS)
            val summary = dashboard.dashboard(scope).verified("DashboardGateway.dashboard")
            LiveReport.note(
                "dashboard",
                "agents=${summary.agents} tasks=${summary.tasks} pending=${summary.pendingApprovals}",
            )
            val spend = dashboard.windowSpend(scope).verified("DashboardGateway.windowSpend", "(rows)")
            LiveReport.note("window spend rows", spend.joinToString { "${it.window}=${it.costCents}" })
            assertEquals(setOf("5h", "24h", "7d"), spend.map { it.window }.toSet())
            assertTrue(spend.all { it.costCents >= SPEND_CENTS }, "A window lost the seeded spend: $spend")
        }

    @Test
    fun `the attention feed decodes with its badge, its counts by source kind and the seeded approval card`() =
        live {
            val approval = seeder.approval(scope.companyId.value, "Contract read approval")
            val feed = attention.feed(scope, FeedQuery()).verified("AttentionGateway.feed")
            assertTrue(feed.deskBadgeCount >= 0 && feed.totalCount >= feed.items.size)
            LiveReport.note(
                "attention feed",
                "total=${feed.totalCount} badge=${feed.deskBadgeCount} counts=${feed.countsBySourceKind.mapKeys {
                    it.key.serverName
                }}",
            )
            val item = feed.items.firstOrNull { it.subject.id == approval.id }
            assertNotNull(item, "The seeded approval has no card in the attention feed")
            assertEquals(AttentionSourceKind.Approval, checkNotNull(item).sourceKind)
            LiveReport.note("attention approval card", "${item.sourceKind.serverName} ${item.severity}")
        }

    @Test
    fun `issues list, get, comments, attachments and interactions decode with seeded content`() =
        live {
            val first = seeder.issue(scope.companyId.value, "Contract read issue")
            val review = seeder.issue(scope.companyId.value, "Contract review issue", status = "in_review")
            val open = issues.list(scope, IssueQuery(IssueFilter.AllOpen)).verified("IssueGateway.list")
            assertTrue(open.any { it.id.value == first.id }, "The seeded issue is missing from the open list")
            val inReview =
                issues.list(scope, IssueQuery(IssueFilter.InReview)).verified("IssueGateway.list", "(in review)")
            assertTrue(inReview.any { it.id.value == review.id })
            val searched =
                issues.list(scope, IssueQuery(search = "Contract review")).verified("IssueGateway.list", "(search)")
            assertTrue(searched.any { it.id.value == review.id })

            val detail = issues.get(scope, IssueId(first.id)).verified("IssueGateway.get")
            assertEquals("Contract read issue", detail.issue.title)
            assertNotNull(first.identifier)
            LiveReport.note(
                "issue identifier",
                "${detail.issue.identifier} priority=${detail.issue.priority} status=${detail.issue.status}",
            )

            val attachmentId = seeder.attachment(scope.companyId.value, first.id, "contract.txt", ATTACHMENT_TEXT)
            val attachments = issues.attachments(scope, IssueId(first.id)).verified("IssueGateway.attachments")
            val stored = attachments.firstOrNull { it.id == attachmentId }
            assertNotNull(stored, "The uploaded attachment is missing from the list: $attachments")
            assertEquals("contract.txt", checkNotNull(stored).fileName)
            assertEquals(ATTACHMENT_TEXT.length.toLong(), stored.byteSize)

            val interactionId = seeder.question(first.id)
            val interaction =
                interactions
                    .list(scope, IssueId(first.id))
                    .verified("InteractionGateway.list")
                    .firstOrNull { it.meta.id.value == interactionId }
            assertTrue(interaction is AskUserQuestions, "The seeded question did not map: $interaction")
            assertEquals(
                listOf("go"),
                (interaction as AskUserQuestions).questions.map { it.id },
            )
            assertTrue(interaction.meta.status.isOpen, "A new question should be pending")
        }

    @Test
    fun `an approval reads with its linked issue and its comment`() =
        live {
            val linked = seeder.issue(scope.companyId.value, "Contract approval link")
            val seeded = seeder.approval(scope.companyId.value, "Contract approval read", listOf(linked.id))
            seeder.approvalComment(seeded.id, "Seeded approval comment")
            val id = ApprovalId(seeded.id)
            val approval = approvals.get(scope, id).verified("ApprovalGateway.get")
            assertEquals(ApprovalStatus.Pending, approval.status)
            val links = approvals.linkedIssues(scope, id).verified("ApprovalGateway.linkedIssues")
            assertTrue(links.any { it.id == linked.id }, "The linked issue is missing: $links")
            val comments = approvals.comments(scope, id).verified("ApprovalGateway.comments")
            assertTrue(comments.any { it.body == "Seeded approval comment" }, "The comment is missing: $comments")
        }

    @Test
    fun `agents decode`() =
        live {
            val agent = seeder.agent(scope.companyId.value, "Contract Engineer")
            val list = agents.list(scope).verified("AgentGateway.list")
            LiveReport.note("agents listed", list.joinToString { "${it.name}:${it.status}" })
            assertTrue(list.any { it.id.value == agent.id }, "The seeded agent is missing from the list")
            val read = agents.get(scope, AgentId(agent.id)).verified("AgentGateway.get")
            assertEquals("Contract Engineer", read.name)
        }
}
