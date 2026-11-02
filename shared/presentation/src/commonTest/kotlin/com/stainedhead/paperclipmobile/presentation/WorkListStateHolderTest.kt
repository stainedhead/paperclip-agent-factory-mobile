@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.IssuePriority
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.WorkFixtures
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

internal fun TestScope.listHolder(env: Env) =
    WorkListStateHolder(
        env.workList,
        env.connectivity,
        env.clock,
        CoroutineScope(StandardTestDispatcher(testScheduler)),
        CompanyTarget("inst-1", TestData.acme.value),
    )

class WorkListLoadTest {
    @Test
    fun `FR-060 the list starts on all open issues with assignee names joined`() =
        runTest {
            val env = workEnv()
            val holder = listHolder(env)
            advanceUntilIdle()

            val state = holder.state.value
            state.phase shouldBe DetailPhase.Content
            state.filter shouldBe IssueFilterUi.AllOpen
            state.rows.map { it.identifier } shouldBe listOf("PAP-1150", "PAP-1160", "PAP-1182", "PAP-1190", "PAP-1191")
            state.rows.first { it.identifier == "PAP-1182" }.assigneeName shouldBe "Engineer 1"
            state.rows.first { it.identifier == "PAP-1150" }.assignedToMe shouldBe true
            state.rows.first { it.identifier == "PAP-1182" }.priority shouldBe IssuePriorityUi.High
            state.rows.first { it.identifier == "PAP-1182" }.status shouldBe IssueStatusUi.InProgress
            state.updatedAtEpochMillis shouldBe env.clock.now().toEpochMilliseconds()
            state.refreshIntervalSeconds shouldBe 15
        }

    @Test
    fun `FR-060 each saved filter shows its issues`() =
        runTest {
            val env = workEnv()
            val holder = listHolder(env)
            advanceUntilIdle()

            val expected =
                mapOf(
                    IssueFilterUi.Blocked to listOf("PAP-1160"),
                    IssueFilterUi.CriticalAndHigh to listOf("PAP-1160", "PAP-1182", "PAP-1190"),
                    IssueFilterUi.InReview to listOf("PAP-1150"),
                    IssueFilterUi.AssignedToMe to listOf("PAP-1150"),
                )
            expected.forEach { (filter, identifiers) ->
                holder.onIntent(WorkListIntent.FilterSelected(filter))
                advanceUntilIdle()
                holder.state.value.filter shouldBe filter
                holder.state.value.rows
                    .map { it.identifier } shouldBe identifiers
            }
        }

    @Test
    fun `FR-060 search finds by identifier and clearing it brings the filter back`() =
        runTest {
            val env = workEnv()
            val holder = listHolder(env)
            advanceUntilIdle()

            holder.onIntent(WorkListIntent.SearchChanged("pap1182"))
            advanceUntilIdle()
            holder.state.value.rows
                .map { it.identifier } shouldBe listOf("PAP-1182")
            holder.state.value.search shouldBe "pap1182"

            holder.onIntent(WorkListIntent.SearchChanged(""))
            advanceUntilIdle()
            holder.state.value.rows.size shouldBe 5
        }

    @Test
    fun `a newer query replaces one still loading`() =
        runTest {
            val env = workEnv()
            val holder = listHolder(env)
            advanceUntilIdle()

            holder.onIntent(WorkListIntent.FilterSelected(IssueFilterUi.CriticalAndHigh))
            holder.onIntent(WorkListIntent.FilterSelected(IssueFilterUi.InReview))
            advanceUntilIdle()

            holder.state.value.filter shouldBe IssueFilterUi.InReview
            holder.state.value.rows
                .map { it.identifier } shouldBe listOf("PAP-1150")
        }

    @Test
    fun `load more appends pages until the server runs out`() =
        runTest {
            val env = workEnv()
            (1..110).forEach { n ->
                val issue = WorkFixtures.issue("pap-9$n", "bulk $n")
                env.issues.store[issue.id] = issue
            }
            val holder = listHolder(env)
            advanceUntilIdle()
            holder.state.value.rows.size shouldBe 50
            holder.state.value.hasMore shouldBe true

            holder.onIntent(WorkListIntent.LoadMore)
            advanceUntilIdle()
            holder.state.value.rows.size shouldBe 100
            holder.onIntent(WorkListIntent.LoadMore)
            advanceUntilIdle()
            holder.state.value.rows.size shouldBe 115
            holder.state.value.hasMore shouldBe false
            holder.onIntent(WorkListIntent.LoadMore)
            advanceUntilIdle()
            env.issues.count("list") shouldBe 3
        }

    @Test
    fun `load more under critical and high pages by the server's rows so no urgent issue repeats or is skipped`() =
        runTest {
            val env = workEnv()
            (1..110).forEach { n ->
                val priority = if (n % 5 == 0) IssuePriority.High else IssuePriority.Medium
                val issue = WorkFixtures.issue("pap-9$n", "bulk $n", IssueStatus.Todo, priority)
                env.issues.store[issue.id] = issue
            }
            val holder = listHolder(env)
            advanceUntilIdle()
            holder.onIntent(WorkListIntent.FilterSelected(IssueFilterUi.CriticalAndHigh))
            advanceUntilIdle()

            var guard = 0
            while (holder.state.value.hasMore && guard++ < 10) {
                holder.onIntent(WorkListIntent.LoadMore)
                advanceUntilIdle()
            }

            val shown =
                holder.state.value.rows
                    .map { it.id }
            shown.size shouldBe shown.toSet().size
            shown.toSet() shouldBe
                env.issues.store.values
                    .filter { it.status.isOpen && it.priority.isUrgent }
                    .map { it.id.value }
                    .toSet()
        }

    @Test
    fun `an empty result is a content state with no rows and a failure is a failed screen`() =
        runTest {
            val env = workEnv()
            val holder = listHolder(env)
            advanceUntilIdle()
            holder.onIntent(WorkListIntent.SearchChanged("zzzz"))
            advanceUntilIdle()
            holder.state.value.phase shouldBe DetailPhase.Content
            holder.state.value.rows shouldBe emptyList()

            val failing = workEnv()
            failing.issues.failures["list"] = DomainError.Forbidden
            val failed = listHolder(failing)
            advanceUntilIdle()
            failed.state.value.phase shouldBe DetailPhase.Failed
            failed.state.value.error shouldBe ErrorUi(ErrorKey.NoPermission)
        }

    @Test
    fun `acceptance 11 offline with nothing loaded makes no call and after loading keeps the rows`() =
        runTest {
            val cold = workEnv()
            cold.connectivity.setOnline(false)
            val first = listHolder(cold)
            advanceUntilIdle()
            first.state.value.phase shouldBe DetailPhase.Failed
            cold.issues.count("list") shouldBe 0

            val env = workEnv()
            val holder = listHolder(env)
            advanceUntilIdle()
            env.connectivity.setOnline(false)
            advanceUntilIdle()
            holder.onIntent(WorkListIntent.FilterSelected(IssueFilterUi.Blocked))
            advanceUntilIdle()

            holder.state.value.filter shouldBe IssueFilterUi.AllOpen
            holder.state.value.rows.size shouldBe 5
            holder.state.value.isOffline shouldBe true
            holder.state.value.canCreate shouldBe false
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.Offline
        }
}

class WorkListCreateTest {
    @Test
    fun `FR-062 an operator creates an issue and the list shows it`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = listHolder(env)
            advanceUntilIdle()
            holder.state.value.canCreate shouldBe true

            holder.onIntent(WorkListIntent.CreateSubmitted("Plan the rollout", IssuePriorityUi.High))
            advanceUntilIdle()

            env.issues.count("create") shouldBe 1
            holder.state.value.createdIssueId
                ?.startsWith("pap-") shouldBe true
            holder.state.value.rows
                .any { it.title == "Plan the rollout" } shouldBe true
            holder.onIntent(WorkListIntent.CreatedAcknowledged)
            advanceUntilIdle()
            holder.state.value.createdIssueId shouldBe null
        }

    @Test
    fun `acceptance 8 a viewer cannot create and the intent never reaches the gateway`() =
        runTest {
            val env = workEnv(Role.Viewer)
            val holder = listHolder(env)
            advanceUntilIdle()
            holder.state.value.canCreate shouldBe false
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole

            holder.onIntent(WorkListIntent.CreateSubmitted("x", IssuePriorityUi.Low))
            advanceUntilIdle()

            env.issues.count("create") shouldBe 0
        }

    @Test
    fun `a blank title is refused with a key and no call and a second submit in flight is ignored`() =
        runTest {
            val env = workEnv()
            val holder = listHolder(env)
            advanceUntilIdle()

            holder.onIntent(WorkListIntent.CreateSubmitted("  ", IssuePriorityUi.Low))
            advanceUntilIdle()
            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.TitleRequired)
            env.issues.count("create") shouldBe 0

            holder.onIntent(WorkListIntent.CreateSubmitted("Once", IssuePriorityUi.Low))
            holder.onIntent(WorkListIntent.CreateSubmitted("Once", IssuePriorityUi.Low))
            advanceUntilIdle()
            env.issues.count("create") shouldBe 1
        }

    @Test
    fun `acceptance 10 a timeout on create is sent once and the list is read again`() =
        runTest {
            val env = workEnv()
            env.issues.failures["create"] = DomainError.Timeout
            val holder = listHolder(env)
            advanceUntilIdle()

            holder.onIntent(WorkListIntent.CreateSubmitted("Maybe", IssuePriorityUi.Low))
            advanceUntilIdle()

            env.issues.count("create") shouldBe 1
            env.issues.count("list") shouldBe 2
            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Failed, error = ErrorUi(ErrorKey.Timeout))
            holder.onIntent(WorkListIntent.MessageDismissed)
            advanceUntilIdle()
            holder.state.value.feedback shouldBe null
        }
}
