package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.agent.PauseAgent
import com.stainedhead.paperclipmobile.application.approval.ApproveApproval
import com.stainedhead.paperclipmobile.application.attention.DismissItem
import com.stainedhead.paperclipmobile.application.attention.RefreshInbox
import com.stainedhead.paperclipmobile.application.budget.LoadBudgetIncident
import com.stainedhead.paperclipmobile.application.dashboard.LoadWindowSpend
import com.stainedhead.paperclipmobile.application.decision.DismissDecision
import com.stainedhead.paperclipmobile.application.interaction.LoadInteractions
import com.stainedhead.paperclipmobile.application.issue.CommentOnIssue
import com.stainedhead.paperclipmobile.application.run.CancelRun
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.port.AgentGateway
import com.stainedhead.paperclipmobile.domain.port.ApprovalGateway
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.BudgetGateway
import com.stainedhead.paperclipmobile.domain.port.DashboardGateway
import com.stainedhead.paperclipmobile.domain.port.DecisionGateway
import com.stainedhead.paperclipmobile.domain.port.InteractionGateway
import com.stainedhead.paperclipmobile.domain.port.IssueGateway
import com.stainedhead.paperclipmobile.domain.port.RunGateway
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** FR-023, F-A-3: a 401 from any company-scoped call marks the factory Signed out once and keeps its config. */
class UnauthorizedMarksSignedOutTest {
    private class Case(
        val name: String,
        val arrange: (Env) -> Unit,
        val call: suspend (Env, SignedOutGateways) -> Unit,
    )

    private val cases =
        listOf(
            Case("inbox refresh", { it.attention.failures["feed"] = DomainError.Unauthorized }) { env, g ->
                RefreshInbox(g.attention, env.attentionCache)(env.scope)
            },
            Case("inbox dismiss", { it.attention.failures["dismiss"] = DomainError.Unauthorized }) { env, g ->
                DismissItem(g.attention)(env.context(), AttentionFixtures.item("ap-1"))
            },
            Case("approve approval", { it.approvals.failures["approve"] = DomainError.Unauthorized }) { env, g ->
                ApproveApproval(g.approvals)(env.context(), ApprovalId("ap-1"), ApprovalStatus.Pending, null)
            },
            Case("dismiss decision", { it.decisions.failures["dismiss"] = DomainError.Unauthorized }) { env, g ->
                DismissDecision(g.decisions)(env.context(), AttentionFixtures.decision(), null)
            },
            Case("load budget incident", { it.budgets.failures["incident"] = DomainError.Unauthorized }) { env, g ->
                LoadBudgetIncident(g.budgets)(env.scope, BudgetIncidentId("inc-1"))
            },
            Case("pause agent", { it.agents.failures["pause"] = DomainError.Unauthorized }) { env, g ->
                PauseAgent(g.agents, env.deviceAuth)(
                    env.context(),
                    env.agents.store.values
                        .first { it.canPause },
                )
            },
            Case("cancel run", { it.runs.failures["cancel"] = DomainError.Unauthorized }) { env, g ->
                CancelRun(g.runs, env.deviceAuth)(
                    env.context(),
                    env.runs.store.values
                        .first { it.canCancel },
                )
            },
            Case("comment on issue", { it.issues.failures["addComment"] = DomainError.Unauthorized }) { env, g ->
                CommentOnIssue(g.issues)(env.context(), IssueId("pap-1182"), "hello", CommentOptions())
            },
            Case("load window spend", { it.dashboard.failures["windowSpend"] = DomainError.Unauthorized }) { env, g ->
                LoadWindowSpend(g.dashboard)(env.scope)
            },
            Case("load interactions", { it.interactions.failures["list"] = DomainError.Unauthorized }) { env, g ->
                LoadInteractions(g.interactions)(env.scope, IssueId("pap-1182"))
            },
        )

    /** The gateways as the composition root hands them to use cases: raw fakes behind the signed-out decorators. */
    private class SignedOutGateways(
        env: Env,
    ) {
        val attention: AttentionGateway = SignedOutAwareAttentionGateway(env.attention, env.signedOut)
        val decisions: DecisionGateway = SignedOutAwareDecisionGateway(env.decisions, env.signedOut)
        val approvals: ApprovalGateway = SignedOutAwareApprovalGateway(env.approvals, env.signedOut)
        val budgets: BudgetGateway = SignedOutAwareBudgetGateway(env.budgets, env.signedOut)
        val issues: IssueGateway = SignedOutAwareIssueGateway(env.issues, env.signedOut)
        val interactions: InteractionGateway = SignedOutAwareInteractionGateway(env.interactions, env.signedOut)
        val agents: AgentGateway = SignedOutAwareAgentGateway(env.agents, env.signedOut)
        val runs: RunGateway = SignedOutAwareRunGateway(env.runs, env.signedOut)
        val dashboard: DashboardGateway = SignedOutAwareDashboardGateway(env.dashboard, env.signedOut)
    }

    private fun Env.gateways() = SignedOutGateways(this)

    @Test
    fun `approve_401_marks_instance_signed_out_and_keeps_config for every package`() =
        runTest {
            for (case in cases) {
                val env = Env(listOf(TestData.instance(name = "Renamed", sortOrder = 3)))
                case.arrange(env)

                case.call(env, env.gateways())

                val stored = env.repository.current.single()
                withClue(case.name) {
                    stored.status shouldBe InstanceStatus.SignedOut
                    stored.displayName shouldBe "Renamed"
                    stored.sortOrder shouldBe 3
                    stored.baseUri shouldBe TestData.uri()
                    stored.defaultCompanyId shouldBe TestData.acme
                }
            }
        }

    @Test
    fun `unauthorized_marks_signed_out_exactly_once`() =
        runTest {
            val env = Env(listOf(TestData.instance()))
            env.attention.failures["feed"] = DomainError.Unauthorized
            val refresh = RefreshInbox(env.gateways().attention, env.attentionCache)

            refresh(env.scope)
            val writesAfterFirst = env.repository.updateCalls
            refresh(env.scope)

            writesAfterFirst shouldBe 1
            env.repository.updateCalls shouldBe 1
        }

    @Test
    fun `other failures and successes leave the status alone`() =
        runTest {
            val env = Env(listOf(TestData.instance()))
            env.attention.failures["feed"] = DomainError.Forbidden
            RefreshInbox(env.gateways().attention, env.attentionCache)(env.scope)
            env.attention.failures.clear()
            RefreshInbox(env.gateways().attention, env.attentionCache)(env.scope)

            env.repository.current
                .single()
                .status shouldBe InstanceStatus.SignedIn
            env.repository.updateCalls shouldBe 0
        }
}
