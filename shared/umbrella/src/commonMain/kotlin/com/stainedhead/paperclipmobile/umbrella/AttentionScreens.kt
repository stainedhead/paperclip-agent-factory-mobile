package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.presentation.ApprovalStateHolder
import com.stainedhead.paperclipmobile.presentation.BudgetIncidentStateHolder
import com.stainedhead.paperclipmobile.presentation.CompanyTarget
import com.stainedhead.paperclipmobile.presentation.DecisionStateHolder
import com.stainedhead.paperclipmobile.presentation.DecisionTarget
import com.stainedhead.paperclipmobile.presentation.InboxStateHolder
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf

/** What the Inbox card knew when it was tapped; pass `DecisionHints(null, null, null)` when opened another way. */
data class DecisionHints(
    val originAgentName: String?,
    val decideBy: String?,
    val dismissalKey: String?,
)

/** Creates the screens of slice 1a-2. Each call returns a holder the screen owns; call `close()` when it goes away. */
class AttentionScreens internal constructor(
    private val koin: Koin,
) {
    /** The Inbox (screen 1) of one company of one factory. Call `close()` when the screen goes away. */
    fun createInboxStateHolder(
        instanceId: String,
        companyId: String,
    ): InboxStateHolder = koin.get { parametersOf(newScreenScope(), CompanyTarget(instanceId, companyId)) }

    /**
     * Decision detail (screen 2). [hints] is what the Inbox card knew (the decision endpoint has neither the agent name
     * nor the decide-by); with `dismissalKey` the screen offers Snooze, without it Snooze is hidden (ADR-025).
     */
    fun createDecisionStateHolder(
        instanceId: String,
        companyId: String,
        decisionId: String,
        hints: DecisionHints,
    ): DecisionStateHolder =
        koin.get {
            parametersOf(
                newScreenScope(),
                DecisionTarget(
                    CompanyTarget(instanceId, companyId),
                    decisionId,
                    hints.originAgentName,
                    hints.decideBy,
                    hints.dismissalKey,
                ),
            )
        }

    /** Approval detail (screen 3). */
    fun createApprovalStateHolder(
        instanceId: String,
        companyId: String,
        approvalId: String,
    ): ApprovalStateHolder =
        koin.get { parametersOf(newScreenScope(), CompanyTarget(instanceId, companyId), approvalId) }

    /** The Budget incident sheet (screen 12). */
    fun createBudgetIncidentStateHolder(
        instanceId: String,
        companyId: String,
        incidentId: String,
    ): BudgetIncidentStateHolder =
        koin.get { parametersOf(newScreenScope(), CompanyTarget(instanceId, companyId), incidentId) }
}
