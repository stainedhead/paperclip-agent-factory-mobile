package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.fakes.FakeApprovalGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeBiometrics
import com.stainedhead.paperclipmobile.testing.fakes.FakeBudgetGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeConnectivity
import com.stainedhead.paperclipmobile.testing.fakes.FakeDecisionGateway
import com.stainedhead.paperclipmobile.testing.fakes.resolveElsewhere

/**
 * What a shell may steer and read on the demo graph only (`PaperclipGraph.demoControls`; null on any other graph).
 * It lets UI tests script the device-owner prompt (Face ID cancelled, failed, approved) and assert how often the
 * phone asked, without a real device. It never exists in a graph backed by real adapters.
 */
class DemoControls internal constructor(
    private val biometrics: FakeBiometrics,
    private val connectivity: FakeConnectivity,
    private val decisions: FakeDecisionGateway,
    private val approvals: FakeApprovalGateway,
    private val budgets: FakeBudgetGateway,
) {
    /** How many device-owner prompts the graph has shown so far. */
    val biometricPromptCount: Int get() = biometrics.calls

    /** The next prompts are answered with [result], one after another, before the default applies again. */
    fun scriptBiometrics(result: BiometricResult) = biometrics.script(result)

    /** The answer for every prompt that is not scripted. Approved until changed. */
    fun setDefaultBiometricResult(result: BiometricResult) {
        biometrics.result = result
    }

    /** Goes offline (false) or comes back online (true), so UI tests can see the offline states of a screen. */
    fun setOnline(online: Boolean) = connectivity.setOnline(online)

    /**
     * Someone else decided it on the web: the screen opens on its final state although the Inbox feed (read earlier)
     * may still list the card (PRD FR-044).
     */
    fun resolveDecisionElsewhere(
        decisionId: String,
        optionId: String,
    ) = decisions.resolveElsewhere(DecisionId(decisionId), optionId)

    /** The approval was approved elsewhere. */
    fun resolveApprovalElsewhere(approvalId: String) {
        val id = ApprovalId(approvalId)
        approvals.store[id]?.let { approvals.store[id] = it.copy(status = ApprovalStatus.Approved) }
    }

    /** The server's own figure for the spend, which can be higher than the one the incident showed (PRD FR-052). */
    fun setBudgetServerSpend(minorUnits: Long) {
        budgets.serverObservedMinor = minorUnits
    }

    /** The budget incident was resolved elsewhere, so it is no longer active. */
    fun resolveIncidentElsewhere(incidentId: String) = budgets.resolveElsewhere(BudgetIncidentId(incidentId))
}
