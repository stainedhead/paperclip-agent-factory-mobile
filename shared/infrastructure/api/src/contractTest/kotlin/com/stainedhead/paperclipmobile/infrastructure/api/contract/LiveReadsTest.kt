package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.IssueFilter
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssueQuery
import com.stainedhead.paperclipmobile.domain.port.FeedQuery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** AD-050 (c): one read per gateway against the seeded company; the DTOs must decode and map without Malformed. */
class LiveReadsTest {
    @Test
    fun `the company dashboard and the window spend decode`() =
        live {
            val summary = dashboard.dashboard(scope).verified("DashboardGateway.dashboard")
            LiveReport.note(
                "dashboard",
                "agents=${summary.agents} tasks=${summary.tasks} pending=${summary.pendingApprovals}",
            )
            val spend = dashboard.windowSpend(scope).verified("DashboardGateway.windowSpend", "(${"rows"})")
            LiveReport.note("window spend rows", spend.size.toString())
        }

    @Test
    fun `the attention feed decodes with its badge and its counts by source kind`() =
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
            if (approval != null) {
                val item = feed.items.firstOrNull { it.subject.id == approval.id }
                LiveReport.note(
                    "attention approval card",
                    item?.let { "${it.sourceKind.serverName} ${it.severity}" } ?: "not in the feed",
                )
                if (item != null) assertEquals(AttentionSourceKind.Approval, item.sourceKind)
            }
        }

    @Test
    fun `issues list, get, comments, attachments and interactions decode`() =
        live {
            val first = seeder.issue(scope.companyId.value, "Contract read issue")
            val review = seeder.issue(scope.companyId.value, "Contract review issue", status = "in_review")
            val open = issues.list(scope, IssueQuery(IssueFilter.AllOpen)).verified("IssueGateway.list")
            assertTrue(open.any { it.id.value == first.id }, "The seeded issue is missing from the open list")
            val inReview =
                issues
                    .list(
                        scope,
                        IssueQuery(IssueFilter.InReview),
                    ).verified("IssueGateway.list", "(in review)")
            assertTrue(inReview.any { it.id.value == review.id })
            val searched =
                issues
                    .list(
                        scope,
                        IssueQuery(search = "Contract review"),
                    ).verified("IssueGateway.list", "(search)")
            assertTrue(searched.any { it.id.value == review.id })

            val detail = issues.get(scope, IssueId(first.id)).verified("IssueGateway.get")
            assertEquals("Contract read issue", detail.issue.title)
            assertNotNull(first.identifier)
            LiveReport.note(
                "issue identifier",
                "${detail.issue.identifier} priority=${detail.issue.priority} status=${detail.issue.status}",
            )
            issues.comments(scope, IssueId(first.id)).verified("IssueGateway.comments")
            issues.attachments(scope, IssueId(first.id)).verified("IssueGateway.attachments")
            interactions.list(scope, IssueId(first.id)).verified("InteractionGateway.list")
        }

    @Test
    fun `approvals read with their linked issues and comments`() =
        live {
            val seeded = seeder.approval(scope.companyId.value, "Contract approval read") ?: return@live
            val id = ApprovalId(seeded.id)
            val approval = approvals.get(scope, id).verified("ApprovalGateway.get")
            assertEquals(ApprovalStatus.Pending, approval.status)
            approvals.linkedIssues(scope, id).verified("ApprovalGateway.linkedIssues")
            approvals.comments(scope, id).verified("ApprovalGateway.comments")
        }

    @Test
    fun `agents and runs decode`() =
        live {
            val agent = seeder.agent(scope.companyId.value, "Contract Engineer")
            val list = agents.list(scope).verified("AgentGateway.list")
            LiveReport.note("agents listed", list.joinToString { "${it.name}:${it.status}" })
            if (agent != null) {
                assertTrue(list.any { it.id.value == agent.id }, "The seeded agent is missing from the list")
                agents.get(scope, AgentId(agent.id)).verified("AgentGateway.get")
                runs.forAgent(scope, AgentId(agent.id)).verified("RunGateway.forAgent")
            }
            runs.live(scope).verified("RunGateway.live")
            val issue = seeder.issue(scope.companyId.value, "Contract runs issue")
            runs.forIssue(scope, IssueId(issue.id)).verified("RunGateway.forIssue")
        }
}
