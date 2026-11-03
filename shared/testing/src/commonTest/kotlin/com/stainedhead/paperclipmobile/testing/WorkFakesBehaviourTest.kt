package com.stainedhead.paperclipmobile.testing

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.AgentStatus
import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.InteractionStatus
import com.stainedhead.paperclipmobile.domain.InvokeOutcome
import com.stainedhead.paperclipmobile.domain.IssueFilter
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssueQuery
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.NewIssue
import com.stainedhead.paperclipmobile.domain.PauseReason
import com.stainedhead.paperclipmobile.domain.RunId
import com.stainedhead.paperclipmobile.domain.RunStatus
import com.stainedhead.paperclipmobile.testing.fakes.FakeAgentGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeDashboardGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeInteractionGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeIssueGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeRunGateway
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.days

private fun <T> DomainResult<T>.value(): T = (this as DomainResult.Success).value

private fun DomainResult<*>.error(): DomainError = (this as DomainResult.Failure).error

/** The work, agent and run fakes follow the server's rules (research W1 to R1), so screens behave as they will live. */
class WorkFakesBehaviourTest {
    private val scope = CompanyScope(InstanceId("inst-1"), TestData.acme)
    private val pap = IssueId("pap-1182")

    @Test
    fun `the issue list applies the server params but not the priority rule`() =
        runTest {
            val gateway = FakeIssueGateway()
            val review = gateway.list(scope, IssueQuery(IssueFilter.InReview)).value()
            review.all { it.status == IssueStatus.InReview } shouldBe true
            val blocked = gateway.list(scope, IssueQuery(IssueFilter.Blocked)).value()
            blocked.all { it.status == IssueStatus.Blocked } shouldBe true
            val mine = gateway.list(scope, IssueQuery(IssueFilter.AssignedToMe)).value()
            mine.all { it.assigneeUserId == "user-1" } shouldBe true
            val open = gateway.list(scope, IssueQuery(IssueFilter.CriticalAndHigh)).value()
            open.any { !it.priority.isUrgent } shouldBe true
        }

    @Test
    fun `search finds an issue by identifier and paging stops at the end`() =
        runTest {
            val gateway = FakeIssueGateway()
            gateway.list(scope, IssueQuery(search = "pap1182")).value().map { it.id } shouldBe listOf(pap)
            gateway.list(scope, IssueQuery(offset = 500)).value() shouldBe emptyList()
        }

    @Test
    fun `an issue detail has its parent and subtasks and a missing one is not found`() =
        runTest {
            val gateway = FakeIssueGateway()
            val detail = gateway.get(scope, pap).value()
            detail.subtasks.map { it.parentId }.all { it == pap } shouldBe true
            detail.subtasks.isNotEmpty() shouldBe true
            gateway.get(scope, IssueId("nope")).error() shouldBe DomainError.NotFound
        }

    @Test
    fun `a comment can reopen a closed issue and a created subtask has a parent`() =
        runTest {
            val gateway = FakeIssueGateway()
            gateway.setStatus(scope, pap, IssueStatus.Done).value().status shouldBe IssueStatus.Done
            gateway.addComment(scope, pap, "back again", CommentOptions(reopen = true)).value().body shouldBe
                "back again"
            gateway
                .get(scope, pap)
                .value()
                .issue.status shouldBe IssueStatus.Todo
            gateway
                .comments(scope, pap)
                .value()
                .last()
                .body shouldBe "back again"
            val created = gateway.create(scope, NewIssue("Sub", parentId = pap)).value()
            created.parentId shouldBe pap
            gateway.assign(scope, created.id, Assignee.UserAssignee("user-1")).value().assigneeUserId shouldBe "user-1"
        }

    @Test
    fun `an interaction can be answered once and a second answer is a conflict`() =
        runTest {
            val gateway = FakeInteractionGateway()
            val pending = gateway.list(scope, pap).value().first { it.meta.status.isOpen }
            val done = gateway.answer(scope, pap, pending.meta.id, InteractionAnswer.Accept).value()
            done.meta.status shouldBe InteractionStatus.Accepted
            gateway.answer(scope, pap, pending.meta.id, InteractionAnswer.Accept).error() shouldBe DomainError.Conflict
        }

    @Test
    fun `an answer of the wrong kind is refused with a reason`() =
        runTest {
            val gateway = FakeInteractionGateway()
            val pending = gateway.list(scope, pap).value().first { it.meta.status.isOpen }
            gateway
                .answer(scope, pap, pending.meta.id, InteractionAnswer.Respond(emptyList()))
                .error()
                .shouldBeInstanceOf<DomainError.Rejected>()
        }

    @Test
    fun `pause and resume change the agent and run now starts a run`() =
        runTest {
            val gateway = FakeAgentGateway()
            val id = AgentId("agent-eng-1")
            val paused = gateway.pause(scope, id).value()
            paused.status shouldBe AgentStatus.Paused
            paused.pauseReason shouldBe PauseReason.Manual
            gateway.resume(scope, id).value().status shouldBe AgentStatus.Idle
            gateway.invokeHeartbeat(scope, id).value().shouldBeInstanceOf<InvokeOutcome.Started>()
            gateway
                .setBudget(scope, id, Money.usdCents(50_000))
                .value()
                .monthlyBudget.minorUnits shouldBe 50_000
            gateway.get(scope, AgentId("nope")).error() shouldBe DomainError.NotFound
        }

    @Test
    fun `run now puts the agent to work and reports the run it started`() =
        runTest {
            val gateway = FakeAgentGateway()
            val started = mutableListOf<RunId>()
            gateway.onRunStarted = { started += it.id }
            val id = AgentId("agent-qa")
            gateway.get(scope, id).value().status shouldBe AgentStatus.Error

            val outcome = gateway.invokeHeartbeat(scope, id).value()

            outcome.shouldBeInstanceOf<InvokeOutcome.Started>()
            started shouldBe listOf(outcome.run.id)
            gateway.get(scope, id).value().status shouldBe AgentStatus.Running
        }

    @Test
    fun `runs tail by offset and sequence and only an active run can be cancelled`() =
        runTest {
            val gateway = FakeRunGateway()
            val id = RunId("run-live")
            val first = gateway.log(scope, id, 0).value()
            first.content.isNotEmpty() shouldBe true
            gateway.log(scope, id, first.nextOffset!!).value().nextOffset shouldBe null
            gateway.appendLog(id, "more output")
            gateway.log(scope, id, first.nextOffset!!).value().content shouldBe "more output"
            gateway.events(scope, id, 0).value().size shouldBe 2
            gateway.events(scope, id, 1).value().map { it.seq } shouldBe listOf(2L)
            gateway.cancel(scope, id).value().status shouldBe RunStatus.Cancelled
            gateway.cancel(scope, id).error() shouldBe DomainError.Conflict
            gateway.live(scope).value().none { it.id == id } shouldBe true
        }

    @Test
    fun `the dashboard fake answers the tiles and the spend rows`() =
        runTest {
            val gateway = FakeDashboardGateway()
            gateway
                .dashboard(scope)
                .value()
                .monthBudget.minorUnits shouldBe 500_000
            gateway.windowSpend(scope).value().isNotEmpty() shouldBe true
        }

    @Test
    fun `an answered interaction tells the hook, a refused answer does not`() =
        runTest {
            val gateway = FakeInteractionGateway()
            val answered = mutableListOf<String>()
            gateway.onAnswered = { answered += it }
            val pending = gateway.list(scope, pap).value().first { it.meta.status == InteractionStatus.Pending }

            gateway.answer(scope, pap, pending.meta.id, InteractionAnswer.Respond(emptyList()))
            answered shouldBe emptyList()
            gateway.answer(scope, pap, pending.meta.id, InteractionAnswer.Accept)
            answered shouldBe listOf(pending.meta.id.value)
        }

    @Test
    fun `a new comment carries the time the fake was told and is not older than the thread`() =
        runTest {
            val later = TestData.epoch + 3.days
            val gateway = FakeIssueGateway(now = { later })
            gateway.addComment(scope, pap, "Hello", CommentOptions())
            gateway
                .comments(scope, pap)
                .value()
                .last()
                .createdAt shouldBe later
        }
}
