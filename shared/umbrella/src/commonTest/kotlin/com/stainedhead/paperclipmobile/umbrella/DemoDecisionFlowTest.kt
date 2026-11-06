package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.presentation.ApprovalIntent
import com.stainedhead.paperclipmobile.presentation.ApprovalStatusUi
import com.stainedhead.paperclipmobile.presentation.BudgetChoice
import com.stainedhead.paperclipmobile.presentation.BudgetIntent
import com.stainedhead.paperclipmobile.presentation.BudgetOutcomeUi
import com.stainedhead.paperclipmobile.presentation.DecisionFinalKind
import com.stainedhead.paperclipmobile.presentation.DecisionIntent
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.InboxPhase
import com.stainedhead.paperclipmobile.presentation.InboxState
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

private suspend fun <S> StateFlow<S>.await(predicate: (S) -> Boolean): S =
    withContext(Dispatchers.Default) { withTimeout(60.seconds) { first(predicate) } }

private fun InboxState.has(id: String) = sections.any { section -> section.items.any { it.id == id } }

/** Deciding on the demo graph acts like a server for the Inbox (UI-030), and a screen can open on something that was
 *  resolved elsewhere. */
class DemoDecisionFlowTest {
    private val graph = PaperclipCore.createDemoGraph()
    private val hints = DecisionHints("CTO", "today", "decision:dec-1182")

    @Test
    fun `deciding the billing decision removes its Inbox card and lowers the badge`() =
        runTest {
            val inbox = graph.attention.createInboxStateHolder("demo-home", "company-acme")
            inbox.state.await { it.phase == InboxPhase.Content }
            val decision = graph.attention.createDecisionStateHolder("demo-home", "company-acme", "dec-1182", hints)
            decision.state.await { it.phase == DetailPhase.Content }

            decision.onIntent(DecisionIntent.OptionSelected("ship"))
            decision.onIntent(DecisionIntent.ConfirmTapped)
            decision.state.await { it.finalState != null }
            inbox.onIntent(com.stainedhead.paperclipmobile.presentation.InboxIntent.Refresh)

            val after = inbox.state.await { !it.has("decision:dec-1182") }
            after.badgeCount shouldBe 2
            decision.close()
            inbox.close()
        }

    @Test
    fun `the second demo decision needs a reason of at most forty characters`() =
        runTest {
            val decision = graph.attention.createDecisionStateHolder("demo-home", "company-acme", "dec-1183", hints)
            val state = decision.state.await { it.phase == DetailPhase.Content }

            val input = state.inputs.single()
            input.isRequired shouldBe true
            input.maxLength shouldBe 40
            decision.close()
        }

    @Test
    fun `a decision resolved elsewhere opens on its final state while its card is still in the feed`() =
        runTest {
            checkNotNull(graph.demoControls).resolveDecisionElsewhere("dec-1182", "wait")
            val decision = graph.attention.createDecisionStateHolder("demo-home", "company-acme", "dec-1182", hints)

            val state = decision.state.await { it.finalState != null }

            state.finalState?.kind shouldBe DecisionFinalKind.Decided
            state.finalState?.chosenOptionLabel shouldBe "Wait for QA sign-off"
            state.options shouldBe emptyList()
            decision.close()
        }

    @Test
    fun `an approval resolved elsewhere opens as approved and a budget incident as no longer active`() =
        runTest {
            val controls = checkNotNull(graph.demoControls)
            controls.resolveApprovalElsewhere("ap-hire-qa")
            controls.resolveIncidentElsewhere("inc-eng-2")

            val approval = graph.attention.createApprovalStateHolder("demo-home", "company-acme", "ap-hire-qa")
            approval.state.await { it.status == ApprovalStatusUi.Approved }.isResolved shouldBe true
            approval.onIntent(ApprovalIntent.Refresh)

            val budget = graph.attention.createBudgetIncidentStateHolder("demo-home", "company-acme", "inc-eng-2")
            budget.state.await { it.outcome != null }.outcome shouldBe BudgetOutcomeUi.NoLongerActive
            approval.close()
            budget.close()
        }

    @Test
    fun `the hire approval names its manager and the discussion comes from the CEO`() =
        runTest {
            val approval = graph.attention.createApprovalStateHolder("demo-home", "company-acme", "ap-hire-qa")
            val state = approval.state.await { it.phase == DetailPhase.Content }

            val payload = state.payload as com.stainedhead.paperclipmobile.presentation.ApprovalPayloadUi.HireAgent
            payload.reportsToName shouldBe "CTO"
            state.comments
                .single()
                .body
                .startsWith("Refund regressions slipped twice") shouldBe true
            approval.close()
        }

    @Test
    fun `a raise that passes the shown spend but not the server's is refused with the server's reason`() =
        runTest {
            checkNotNull(graph.demoControls).setBudgetServerSpend(21_000)
            val budget = graph.attention.createBudgetIncidentStateHolder("demo-home", "company-acme", "inc-eng-2")
            budget.state.await { it.phase == DetailPhase.Content }
            budget.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.RaiseAndResume))
            budget.onIntent(BudgetIntent.AmountTyped("205.00", ".", 2))
            budget.state.await { it.canSubmit }

            budget.onIntent(BudgetIntent.SubmitTapped)

            val state = budget.state.await { it.feedback != null }
            state.feedback?.error?.detail shouldBe "New budget must exceed current observed spend"
            state.outcome shouldBe null
            budget.close()
        }
}
