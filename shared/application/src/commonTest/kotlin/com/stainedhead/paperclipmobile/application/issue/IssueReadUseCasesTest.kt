package com.stainedhead.paperclipmobile.application.issue

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.IssueFilter
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssueQuery
import com.stainedhead.paperclipmobile.testing.WorkFixtures
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private fun <T> DomainResult<T>.value(): T = (this as DomainResult.Success).value

class ListIssuesTest {
    @Test
    fun `FR-060 critical and high is filtered on the device after the server returns every open issue`() =
        runTest {
            val env = Env()

            val page =
                ListIssues(
                    env.issues,
                )(env.scope, IssueQuery(IssueFilter.CriticalAndHigh), WorkFixtures.ME).value()

            page.issues.map { it.identifier } shouldBe listOf("PAP-1160", "PAP-1182", "PAP-1190")
            page.hasMore shouldBe false
        }

    @Test
    fun `a full page says there may be more even when the device filter drops rows`() =
        runTest {
            val env = Env()

            val page =
                ListIssues(
                    env.issues,
                )(env.scope, IssueQuery(IssueFilter.CriticalAndHigh, limit = 2), WorkFixtures.ME).value()

            page.hasMore shouldBe true
        }

    @Test
    fun `other filters are not filtered again on the device`() =
        runTest {
            val env = Env()

            val page = ListIssues(env.issues)(env.scope, IssueQuery(IssueFilter.AssignedToMe), WorkFixtures.ME).value()

            page.issues.map { it.identifier } shouldBe listOf("PAP-1150")
            env.issues.queries
                .single()
                .filter shouldBe IssueFilter.AssignedToMe
        }

    @Test
    fun `FR-096 a transient failure is retried three times then reported`() =
        runTest {
            val env = Env()
            env.issues.failures["list"] = DomainError.Timeout

            val result = ListIssues(env.issues)(env.scope, IssueQuery(), null)

            (result as DomainResult.Failure).error shouldBe DomainError.Timeout
            env.issues.count("list") shouldBe 4
        }
}

class SearchIssuesTest {
    @Test
    fun `FR-060 search sends the trimmed text and keeps the saved filter`() =
        runTest {
            val env = Env()

            val page = SearchIssues(ListIssues(env.issues))(env.scope, IssueFilter.AllOpen, "  pap1182 ", null).value()

            page.issues.map { it.identifier } shouldBe listOf("PAP-1182")
            env.issues.queries
                .single()
                .search shouldBe "pap1182"
        }

    @Test
    fun `a blank search is the plain list`() =
        runTest {
            val env = Env()

            SearchIssues(
                ListIssues(env.issues),
            )(env.scope, IssueFilter.AllOpen, "   ", null).value().issues.size shouldBe
                5
            env.issues.queries
                .single()
                .search shouldBe null
        }
}

class LoadIssueTest {
    @Test
    fun `FR-061 the issue comes with comments oldest first and names joined from the agent list`() =
        runTest {
            val env = Env()

            val view = LoadIssue(env.issues, env.agents)(env.scope, WorkFixtures.pap1182).value()

            view.detail.issue.identifier shouldBe "PAP-1182"
            view.detail.subtasks.size shouldBe 2
            view.comments.map { it.id } shouldBe listOf("c1", "c2")
            view.comments.first().authorName shouldBe "Engineer 1"
            view.agentNames[AgentId("agent-eng-1")] shouldBe "Engineer 1"
            view.detail.plan
                ?.body
                ?.startsWith("1.") shouldBe true
        }

    @Test
    fun `names are best effort so a failing agent list still shows the issue`() =
        runTest {
            val env = Env()
            env.agents.failures["list"] = DomainError.Forbidden

            val view = LoadIssue(env.issues, env.agents)(env.scope, WorkFixtures.pap1182).value()

            view.agentNames shouldBe emptyMap()
            view.comments.size shouldBe 2
        }

    @Test
    fun `a missing issue is not found and is not retried`() =
        runTest {
            val env = Env()

            val result = LoadIssue(env.issues, env.agents)(env.scope, IssueId("nope"))

            (result as DomainResult.Failure).error shouldBe DomainError.NotFound
            env.issues.count("get") shouldBe 1
        }

    @Test
    fun `a failing thread fails the load`() =
        runTest {
            val env = Env()
            env.issues.failures["comments"] = DomainError.ServerError

            val result = LoadIssue(env.issues, env.agents)(env.scope, WorkFixtures.pap1182)

            (result is DomainResult.Failure) shouldBe true
        }
}

class PlanAndFilesTest {
    @Test
    fun `FR-061 the plan is read only and absent for an issue without one`() =
        runTest {
            val env = Env()

            LoadIssuePlan(env.issues)(env.scope, WorkFixtures.pap1182).value()?.title shouldBe "Plan"
            LoadIssuePlan(env.issues)(env.scope, IssueId("pap-1190")).value() shouldBe null
        }

    @Test
    fun `FR-061 attachments are listed`() =
        runTest {
            val env = Env()

            ListIssueAttachments(env.issues)(env.scope, WorkFixtures.pap1182).value().map { it.id } shouldBe
                listOf("att-1", "att-2")
        }
}
