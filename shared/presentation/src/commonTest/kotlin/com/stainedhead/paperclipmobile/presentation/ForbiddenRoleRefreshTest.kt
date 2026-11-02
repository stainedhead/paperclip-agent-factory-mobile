@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * F-D-2: a 403 on a mutation means the role changed on the server. The holder resolves the role once more (no loop,
 * the mutation is not repeated) and the screen shows the new role's actions.
 */
class ForbiddenRoleRefreshTest {
    private fun Env.demoteToViewer() {
        auth.meResult = DomainResult.Success(TestData.access(Role.Viewer))
    }

    @Test
    fun `forbiddenMutationReResolvesTheRole in Approval`() =
        runTest {
            val env = approvalEnv(Role.Operator)
            val holder = approvalHolder(env)
            advanceUntilIdle()
            holder.state.value.actions
                .isNotEmpty() shouldBe true
            env.demoteToViewer()
            env.approvals.failures["approve"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(ApprovalIntent.ApproveTapped)
            advanceUntilIdle()

            env.approvals.count("approve") shouldBe 1
            env.auth.count("me") shouldBe resolved + 1
            holder.state.value.role shouldBe RoleUi.Viewer
            holder.state.value.actions shouldBe emptyList()
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `forbiddenMutationReResolvesTheRole in Decision`() =
        runTest {
            val env = decisionEnv(Role.Operator)
            val holder = decisionHolder(env)
            advanceUntilIdle()
            env.demoteToViewer()
            env.decisions.failures["decide"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(DecisionIntent.OptionSelected("ship"))
            holder.onIntent(DecisionIntent.ConfirmTapped)
            advanceUntilIdle()

            env.decisions.count("decide") shouldBe 1
            env.auth.count("me") shouldBe resolved + 1
            holder.state.value.role shouldBe RoleUi.Viewer
            holder.state.value.canConfirm shouldBe false
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `forbiddenMutationReResolvesTheRole in Budget`() =
        runTest {
            val env = budgetEnv(Role.Operator)
            val holder = budgetHolder(env)
            advanceUntilIdle()
            env.demoteToViewer()
            env.budgets.failures["resolve"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(BudgetIntent.ChoiceSelected(BudgetChoice.KeepPaused))
            holder.onIntent(BudgetIntent.SubmitTapped)
            advanceUntilIdle()

            env.budgets.count("resolve") shouldBe 1
            env.auth.count("me") shouldBe resolved + 1
            holder.state.value.canKeepPaused shouldBe false
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `forbiddenMutationReResolvesTheRole in Agent detail`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = detailHolder(env)
            advanceUntilIdle()
            env.demoteToViewer()
            env.agents.failures["invokeHeartbeat"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(AgentDetailIntent.RunNowTapped)
            advanceUntilIdle()

            env.agents.count("invokeHeartbeat") shouldBe 1
            env.auth.count("me") shouldBe resolved + 1
            holder.state.value.actions shouldBe emptyList()
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `forbiddenMutationReResolvesTheRole in Agents`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = agentsHolder(env)
            advanceUntilIdle()
            env.demoteToViewer()
            env.agents.failures["invokeHeartbeat"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(AgentsIntent.RunNowTapped("agent-eng-1"))
            advanceUntilIdle()

            env.agents.count("invokeHeartbeat") shouldBe 1
            env.auth.count("me") shouldBe resolved + 1
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `forbiddenMutationReResolvesTheRole in Run viewer`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = runHolder(env)
            advanceUntilIdle()
            env.demoteToViewer()
            env.runs.failures["cancel"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(RunViewerIntent.CancelTapped)
            holder.onIntent(RunViewerIntent.CancelConfirmed)
            advanceUntilIdle()

            env.runs.count("cancel") shouldBe 1
            env.auth.count("me") shouldBe resolved + 1
            holder.state.value.canCancel shouldBe false
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `forbiddenMutationReResolvesTheRole in Issue`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = issueHolder(env)
            advanceUntilIdle()
            env.demoteToViewer()
            env.issues.failures["addComment"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(IssueIntent.CommentSubmitted("hello", reopen = false, interrupt = false))
            advanceUntilIdle()

            env.issues.count("addComment") shouldBe 1
            env.auth.count("me") shouldBe resolved + 1
            holder.state.value.canComment shouldBe false
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `forbiddenMutationReResolvesTheRole in Work list`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = listHolder(env)
            advanceUntilIdle()
            env.demoteToViewer()
            env.issues.failures["create"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(WorkListIntent.CreateSubmitted("New thing", IssuePriorityUi.Medium))
            advanceUntilIdle()

            env.issues.count("create") shouldBe 1
            env.auth.count("me") shouldBe resolved + 1
            holder.state.value.canCreate shouldBe false
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    // F-D-2 leftover: a READ that answers 403 also means the role may have changed, so it is resolved once more.

    @Test
    fun `forbiddenReadReResolvesTheRole in Approval`() =
        runTest {
            val env = approvalEnv(Role.Operator)
            val holder = approvalHolder(env)
            advanceUntilIdle()
            env.demoteToViewer()
            env.approvals.failures["get"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(ApprovalIntent.Refresh)
            advanceUntilIdle()

            env.auth.count("me") shouldBe resolved + 1
            holder.state.value.role shouldBe RoleUi.Viewer
            holder.state.value.actions shouldBe emptyList()
            holder.state.value.error
                ?.key shouldBe ErrorKey.NoPermission
        }

    @Test
    fun `forbiddenReadReResolvesTheRole in Decision`() =
        runTest {
            val env = decisionEnv(Role.Operator)
            val holder = decisionHolder(env)
            advanceUntilIdle()
            env.demoteToViewer()
            env.decisions.failures["get"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(DecisionIntent.Refresh)
            advanceUntilIdle()

            env.auth.count("me") shouldBe resolved + 1
            holder.state.value.role shouldBe RoleUi.Viewer
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `forbiddenReadReResolvesTheRole in Budget`() =
        runTest {
            val env = budgetEnv(Role.Operator)
            val holder = budgetHolder(env)
            advanceUntilIdle()
            env.demoteToViewer()
            env.budgets.failures["incident"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(BudgetIntent.Refresh)
            advanceUntilIdle()

            env.auth.count("me") shouldBe resolved + 1
            holder.state.value.canKeepPaused shouldBe false
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `forbiddenReadReResolvesTheRole in Agent detail`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = detailHolder(env)
            advanceUntilIdle()
            env.demoteToViewer()
            env.agents.failures["get"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(AgentDetailIntent.Refresh)
            advanceUntilIdle()

            env.auth.count("me") shouldBe resolved + 1
            holder.state.value.actions shouldBe emptyList()
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `forbiddenReadReResolvesTheRole in Agents`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = agentsHolder(env)
            advanceUntilIdle()
            env.demoteToViewer()
            env.agents.failures["list"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(AgentsIntent.Refresh)
            advanceUntilIdle()

            env.auth.count("me") shouldBe resolved + 1
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `forbiddenReadReResolvesTheRole in Run viewer`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = runHolder(env)
            advanceUntilIdle()
            env.demoteToViewer()
            env.runs.failures["get"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(RunViewerIntent.Refresh)
            advanceUntilIdle()

            env.auth.count("me") shouldBe resolved + 1
            holder.state.value.canCancel shouldBe false
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `forbiddenReadReResolvesTheRole in Issue`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = issueHolder(env)
            advanceUntilIdle()
            env.demoteToViewer()
            env.issues.failures["get"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(IssueIntent.Refresh)
            advanceUntilIdle()

            env.auth.count("me") shouldBe resolved + 1
            holder.state.value.canComment shouldBe false
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `forbiddenReadReResolvesTheRole in Work list`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = listHolder(env)
            advanceUntilIdle()
            env.demoteToViewer()
            env.issues.failures["list"] = DomainError.Forbidden
            val resolved = env.auth.count("me")

            holder.onIntent(WorkListIntent.Refresh)
            advanceUntilIdle()

            env.auth.count("me") shouldBe resolved + 1
            holder.state.value.canCreate shouldBe false
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole
        }

    @Test
    fun `a read that is not forbidden does not re-resolve the role`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = agentsHolder(env)
            advanceUntilIdle()
            env.agents.failures["list"] = DomainError.ServerError
            val resolved = env.auth.count("me")

            holder.onIntent(AgentsIntent.Refresh)
            advanceUntilIdle()

            env.auth.count("me") shouldBe resolved
        }
}
