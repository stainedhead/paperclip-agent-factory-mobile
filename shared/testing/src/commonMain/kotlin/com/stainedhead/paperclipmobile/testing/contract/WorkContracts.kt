package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.AgentStatus
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.InteractionId
import com.stainedhead.paperclipmobile.domain.InteractionStatus
import com.stainedhead.paperclipmobile.domain.InvokeOutcome
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.PauseReason
import com.stainedhead.paperclipmobile.domain.SpendWindowKind
import com.stainedhead.paperclipmobile.domain.SpendWindows
import com.stainedhead.paperclipmobile.domain.port.AgentGateway
import com.stainedhead.paperclipmobile.domain.port.DashboardGateway
import com.stainedhead.paperclipmobile.domain.port.InteractionGateway

/**
 * Issue-thread interaction rules of [InteractionGateway] (research W7: `issues.ts:17145-17789`). [issue] holds at
 * least two pending confirmations ([pendingConfirmations]) and one interaction that is already [answeredId].
 */
abstract class InteractionContract {
    abstract fun create(): InteractionGateway

    abstract val scope: CompanyScope
    abstract val issue: IssueId
    abstract val pendingConfirmations: List<InteractionId>
    abstract val answeredId: InteractionId

    suspend fun runAll() {
        onlyAPendingInteractionCanBeAnswered()
        theWrongKindOfAnswerIsRejected()
        acceptAndRejectSetTheStatus()
        anUnknownInteractionIsNotFound()
    }

    private suspend fun onlyAPendingInteractionCanBeAnswered() {
        val gateway = create()
        val listed = gateway.list(scope, issue).mustSucceed("list")
        check(
            listed.any { it.meta.id == answeredId && !it.meta.status.isOpen },
        ) { "the answered interaction is listed" }
        gateway
            .answer(
                scope,
                issue,
                answeredId,
                InteractionAnswer.Skip(null),
            ).mustFailWith(DomainError.Conflict, "answering a settled interaction")
    }

    private suspend fun theWrongKindOfAnswerIsRejected() {
        val gateway = create()
        gateway
            .answer(
                scope,
                issue,
                pendingConfirmations[0],
                InteractionAnswer.Respond(emptyList()),
            ).mustBeRejected("a questions answer to a confirmation")
    }

    private suspend fun acceptAndRejectSetTheStatus() {
        val gateway = create()
        val accepted =
            gateway
                .answer(
                    scope,
                    issue,
                    pendingConfirmations[0],
                    InteractionAnswer.Accept,
                ).mustSucceed("accept")
        check(
            accepted.meta.status == InteractionStatus.Answered || accepted.meta.status == InteractionStatus.Accepted,
        ) {
            "accepting settles the interaction but was ${accepted.meta.status}"
        }
        gateway
            .answer(
                scope,
                issue,
                pendingConfirmations[0],
                InteractionAnswer.Accept,
            ).mustFailWith(DomainError.Conflict, "a second accept")
        val rejected =
            gateway
                .answer(
                    scope,
                    issue,
                    pendingConfirmations[1],
                    InteractionAnswer.Reject("not now"),
                ).mustSucceed("reject")
        check(rejected.meta.status == InteractionStatus.Rejected) { "reject gives rejected" }
    }

    private suspend fun anUnknownInteractionIsNotFound() {
        create()
            .answer(scope, issue, InteractionId("no-such-interaction"), InteractionAnswer.Accept)
            .mustFailWith(DomainError.NotFound, "an unknown interaction")
    }
}

/**
 * Agent control rules of [AgentGateway] (research A1, A2: `routes/agents.ts:5776-6344`, `routes/costs.ts:407-427`).
 * [runningAgent] can be paused; [unknownAgent] does not exist.
 */
abstract class AgentContract {
    abstract fun create(): AgentGateway

    abstract val scope: CompanyScope
    abstract val runningAgent: AgentId
    abstract val unknownAgent: AgentId

    suspend fun runAll() {
        listAndGetAgree()
        pauseIsManualAndResumeGoesIdle()
        setBudgetStoresTheAmount()
        runNowStartsARunForTheAgent()
        anUnknownAgentIsNotFound()
    }

    private suspend fun listAndGetAgree() {
        val gateway = create()
        val listed = gateway.list(scope).mustSucceed("list")
        check(listed.isNotEmpty()) { "an agent list is not empty" }
        check(
            gateway.get(scope, runningAgent).mustSucceed("get") ==
                listed.first {
                    it.id == runningAgent
                },
        ) { "get equals the listed agent" }
    }

    private suspend fun pauseIsManualAndResumeGoesIdle() {
        val gateway = create()
        val paused = gateway.pause(scope, runningAgent).mustSucceed("pause")
        check(
            paused.status == AgentStatus.Paused && paused.pauseReason == PauseReason.Manual,
        ) { "pause is a manual pause" }
        val resumed = gateway.resume(scope, runningAgent).mustSucceed("resume")
        check(
            resumed.status == AgentStatus.Idle && resumed.pauseReason == null,
        ) { "resume goes idle and clears the reason" }
    }

    private suspend fun setBudgetStoresTheAmount() {
        val gateway = create()
        val updated = gateway.setBudget(scope, runningAgent, Money.usdCents(BUDGET_CENTS)).mustSucceed("set budget")
        check(updated.monthlyBudget == Money.usdCents(BUDGET_CENTS)) { "the budget is stored in cents" }
    }

    private suspend fun runNowStartsARunForTheAgent() {
        val outcome = create().invokeHeartbeat(scope, runningAgent).mustSucceed("run now")
        check(outcome is InvokeOutcome.Skipped || (outcome as InvokeOutcome.Started).run.agentId == runningAgent) {
            "a started run belongs to the agent"
        }
    }

    private suspend fun anUnknownAgentIsNotFound() {
        val gateway = create()
        gateway.get(scope, unknownAgent).mustFailWith(DomainError.NotFound, "get an unknown agent")
        gateway.pause(scope, unknownAgent).mustFailWith(DomainError.NotFound, "pause an unknown agent")
    }

    private companion object {
        const val BUDGET_CENTS = 75_000L
    }
}

/** Factory home rules of [DashboardGateway] (research D1: `routes/dashboard.ts:27`, `routes/costs.ts:323`). */
abstract class DashboardContract {
    abstract fun create(): DashboardGateway

    abstract val scope: CompanyScope

    suspend fun runAll() {
        countsAreNeverNegative()
        spendRowsUseTheThreeServerWindows()
    }

    private suspend fun countsAreNeverNegative() {
        val summary = create().dashboard(scope).mustSucceed("dashboard")
        val counts =
            listOf(
                summary.agents.active,
                summary.agents.running,
                summary.agents.paused,
                summary.agents.error,
                summary.tasks.open,
                summary.tasks.inProgress,
                summary.tasks.blocked,
                summary.tasks.done,
                summary.pendingApprovals,
                summary.activeBudgetIncidents,
            )
        check(counts.all { it >= 0 }) { "no count is negative" }
        check(summary.monthSpend.currency == summary.monthBudget.currency) { "spend and budget share a currency" }
    }

    private suspend fun spendRowsUseTheThreeServerWindows() {
        val rows = create().windowSpend(scope).mustSucceed("window spend")
        val known = SpendWindowKind.entries.map { it.serverName }.toSet()
        check(rows.all { it.window in known && it.costCents >= 0 }) { "rows use only 5h, 24h and 7d" }
        check(SpendWindows.fromRows(rows).size == SpendWindowKind.entries.size) { "every window is always present" }
    }
}
