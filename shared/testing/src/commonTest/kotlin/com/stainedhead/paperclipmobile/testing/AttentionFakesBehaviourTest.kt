package com.stainedhead.paperclipmobile.testing

import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.BudgetResolution
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.DecisionStatus
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.EffectStatus
import com.stainedhead.paperclipmobile.domain.ExecutionStatus
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.port.FeedQuery
import com.stainedhead.paperclipmobile.testing.fakes.FakeApprovalGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeAttentionGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeBudgetGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import com.stainedhead.paperclipmobile.testing.fakes.FakeDecisionGateway
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

private fun <T> DomainResult<T>.value(): T = (this as DomainResult.Success).value

private fun DomainResult<*>.error(): DomainError = (this as DomainResult.Failure).error

/** The Attention fakes follow the server's rules, so screens built on them behave as they will against the server. */
class AttentionFakesBehaviourTest {
    private val scope = CompanyScope(InstanceId("inst-1"), TestData.acme)

    @Test
    fun `the feed pages with the server's cursor rule and an unknown cursor is refused`() =
        runTest {
            val items = (1..5).map { AttentionFixtures.approvalItem("a$it") }
            val gateway = FakeAttentionGateway(FakeClock(), items)

            val first = gateway.feed(scope, FeedQuery(limit = 2)).value()
            first.items.map { it.subject.id } shouldBe listOf("a1", "a2")
            first.totalCount shouldBe 5
            val second = gateway.feed(scope, FeedQuery(first.nextCursor, 2)).value()
            second.items.map { it.subject.id } shouldBe listOf("a3", "a4")
            val last = gateway.feed(scope, FeedQuery(second.nextCursor, 2)).value()
            last.nextCursor shouldBe null
            gateway.feed(scope, FeedQuery("nope", 2)).error() shouldBe
                DomainError.Rejected("Attention cursor no longer matches the filtered feed")
        }

    @Test
    fun `dismiss and snooze hide an item restore brings it back and decide-by is applied`() =
        runTest {
            val clock = FakeClock()
            val item = AttentionFixtures.approvalItem("a1")
            val gateway = FakeAttentionGateway(clock, listOf(item))

            gateway.dismiss(scope, item.dismissalKey)
            gateway.feed(scope, FeedQuery()).value().items shouldBe emptyList()
            gateway.restore(scope, item.dismissalKey)
            gateway.snooze(scope, item.dismissalKey, clock.now() + 1.hours)
            gateway.feed(scope, FeedQuery()).value().totalCount shouldBe 0
            gateway.snooze(scope, "x", clock.now()).error() shouldBe
                DomainError.Rejected("snoozedUntil must be in the future")
            gateway.restore(scope, item.dismissalKey)
            gateway.setDecideBy(scope, item.sourceKind, "a1", DecideBy.Whenever)
            gateway
                .feed(scope, FeedQuery())
                .value()
                .items
                .single()
                .decideBy shouldBe
                DecideBy.Whenever
        }

    @Test
    fun `a scripted failure fails the call and is counted`() =
        runTest {
            val gateway = FakeAttentionGateway(FakeClock())
            gateway.failures["feed"] = DomainError.Timeout
            gateway.feed(scope, FeedQuery()).error() shouldBe DomainError.Timeout
            gateway.count("feed") shouldBe 1
        }

    @Test
    fun `deciding follows the server rules and reports per effect results`() =
        runTest {
            val gateway = FakeDecisionGateway()
            val id = DecisionId("dec-1")
            gateway.decide(scope, id, "nope", emptyMap(), "k").error() shouldBe DomainError.Rejected("Unknown optionId")

            gateway.effectStatuses = listOf(EffectStatus.Skipped)
            val decided = gateway.decide(scope, id, "ship", emptyMap(), "k").value()
            decided.status shouldBe DecisionStatus.Decided
            decided.outcome?.executionStatus shouldBe ExecutionStatus.Failed
            decided.outcome
                ?.results
                ?.single()
                ?.reason shouldBe "target_changed"
            // Server rule (decisions.ts:686-689): the same option and inputs replay whatever the key.
            gateway.decide(scope, id, "ship", emptyMap(), "k2").value().chosenOptionId shouldBe "ship"
            gateway.decide(scope, id, "cancel", emptyMap(), "k3").error() shouldBe DomainError.Conflict
            gateway.dismiss(scope, id, null).error() shouldBe DomainError.Conflict
        }

    @Test
    fun `a decision reads as running for the configured number of reads then settles`() =
        runTest {
            val gateway = FakeDecisionGateway()
            val id = DecisionId("dec-1")
            gateway.decide(scope, id, "ship", emptyMap(), "k")
            gateway.runningReads = 2

            suspend fun status() =
                gateway
                    .get(scope, id)
                    .value()
                    .outcome
                    ?.executionStatus
            status() shouldBe ExecutionStatus.Running
            status() shouldBe ExecutionStatus.Running
            status() shouldBe ExecutionStatus.Succeeded
        }

    @Test
    fun `a decision whose effects are still running reports running until the configured reads have passed`() =
        runTest {
            val gateway = FakeDecisionGateway()
            val id = DecisionId("dec-1")
            gateway.runningReads = 2

            val decided = gateway.decide(scope, id, "ship", emptyMap(), "k").value()
            decided.outcome?.executionStatus shouldBe ExecutionStatus.Running
            gateway
                .get(scope, id)
                .value()
                .outcome
                ?.executionStatus shouldBe ExecutionStatus.Running
            gateway
                .get(scope, id)
                .value()
                .outcome
                ?.executionStatus shouldBe ExecutionStatus.Running
            gateway
                .get(scope, id)
                .value()
                .outcome
                ?.executionStatus shouldBe ExecutionStatus.Succeeded
        }

    @Test
    fun `dismissing a decision marks it dismissed`() =
        runTest {
            val gateway = FakeDecisionGateway()
            val dismissed = gateway.dismiss(scope, DecisionId("dec-1"), "no").value()
            dismissed.dismissed shouldBe true
        }

    @Test
    fun `approvals follow the server state machine`() =
        runTest {
            val gateway = FakeApprovalGateway()
            val id = ApprovalId("ap-1")

            (gateway.requestRevision(scope, id, "why").value()).status shouldBe
                ApprovalStatus.RevisionRequested
            gateway.requestRevision(scope, id, "again").error().shouldBeInstanceOf<DomainError.Rejected>()
            (gateway.approve(scope, id, null).value()).status shouldBe ApprovalStatus.Approved
            (gateway.approve(scope, id, null).value()).status shouldBe ApprovalStatus.Approved
            gateway.reject(scope, id, "no").error().shouldBeInstanceOf<DomainError.Rejected>()
            gateway.addComment(scope, id, "hi").value()
            gateway.comments(scope, id).value().size shouldBe 2
            gateway.get(scope, ApprovalId("missing")).error() shouldBe DomainError.NotFound
        }

    @Test
    fun `a budget raise must exceed the server's observed spend and a resolved incident is gone`() =
        runTest {
            val gateway = FakeBudgetGateway()
            val id = BudgetIncidentId("inc-1")
            gateway.serverObservedMinor = 12_000

            gateway.resolve(scope, id, BudgetResolution.RaiseAndResume(Money.usdCents(11_000)), null).error() shouldBe
                DomainError.Rejected("New budget must exceed current observed spend")
            gateway.resolve(scope, id, BudgetResolution.RaiseAndResume(Money.usdCents(20_000)), null).value()
            gateway.incident(scope, id).error() shouldBe DomainError.NotFound
        }
}
