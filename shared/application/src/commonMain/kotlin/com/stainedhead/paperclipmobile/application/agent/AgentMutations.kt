package com.stainedhead.paperclipmobile.application.agent

import com.stainedhead.paperclipmobile.application.DeviceAuthGuard
import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.failed
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.Agent
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InvokeOutcome
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.Permissions
import com.stainedhead.paperclipmobile.domain.ResolutionIssue
import com.stainedhead.paperclipmobile.domain.port.AgentGateway
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.RunGateway

private suspend fun AgentGateway.changed(
    context: CompanyContext,
    agent: Agent,
    call: suspend () -> DomainResult<Agent>,
): MutationResult<Agent> =
    when (val result = call()) {
        is DomainResult.Success -> MutationResult.Done(result.value)
        is DomainResult.Failure -> failed(result.error) { get(context.scope, agent.id) }
    }

/**
 * Pauses an agent (PRD FR-071). Order (ADR-019): permission, status, device authentication (FR-014), ONE gateway call,
 * one re-read after a timeout or conflict. Without an approved prompt the gateway is never called.
 */
class PauseAgent(
    private val agents: AgentGateway,
    private val deviceAuth: DeviceAuthGuard,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        agent: Agent,
    ): MutationResult<Agent> {
        if (!Permissions.allows(context.role, Action.PauseAgent)) return MutationResult.Denied
        if (!agent.canPause) return MutationResult.Invalid(InvalidInput.WrongStatus)
        val auth = deviceAuth(Action.PauseAgent)
        if (auth != BiometricResult.Approved) return MutationResult.NotAuthorized(auth)
        return agents.changed(context, agent) { agents.pause(context.scope, agent.id) }
    }
}

/** Resumes a manually paused agent (PRD FR-071). A budget pause is lifted by the budget incident instead. */
class ResumeAgent(
    private val agents: AgentGateway,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        agent: Agent,
    ): MutationResult<Agent> {
        if (!Permissions.allows(context.role, Action.ResumeAgent)) return MutationResult.Denied
        if (!agent.canResume) return MutationResult.Invalid(InvalidInput.WrongStatus)
        return agents.changed(context, agent) { agents.resume(context.scope, agent.id) }
    }
}

/** Wakes an agent now (PRD FR-071). After a timeout the agent's live runs say whether a run started. */
class InvokeHeartbeat(
    private val agents: AgentGateway,
    private val runs: RunGateway,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        agent: Agent,
    ): MutationResult<InvokeOutcome> {
        if (!Permissions.allows(context.role, Action.RunAgentNow)) return MutationResult.Denied
        if (!agent.canRunNow) return MutationResult.Invalid(InvalidInput.WrongStatus)
        return when (val result = agents.invokeHeartbeat(context.scope, agent.id)) {
            is DomainResult.Success -> {
                MutationResult.Done(result.value)
            }

            is DomainResult.Failure -> {
                failed(result.error) {
                    when (val list = runs.forAgent(context.scope, agent.id)) {
                        is DomainResult.Failure -> {
                            list
                        }

                        is DomainResult.Success -> {
                            DomainResult.Success(
                                list.value.firstOrNull { it.isLive }?.let { InvokeOutcome.Started(it) }
                                    ?: InvokeOutcome.Skipped,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sets an agent's monthly budget (PRD FR-071). Owner only (the server would also let an operator, ADR-021), and not on
 * the device-authentication list of FR-014.
 */
class SetAgentBudget(
    private val agents: AgentGateway,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        agent: Agent,
        monthly: Money,
    ): MutationResult<Agent> {
        if (!Permissions.allows(context.role, Action.SetAgentBudget)) return MutationResult.Denied
        if (monthly.currency != agent.monthlyBudget.currency) {
            return MutationResult.Invalid(InvalidInput.Amount(ResolutionIssue.WrongCurrency))
        }
        return agents.changed(context, agent) { agents.setBudget(context.scope, agent.id, monthly) }
    }
}
