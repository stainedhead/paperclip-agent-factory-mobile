package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.fakes.FakeAgentGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeApprovalGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeBiometrics
import com.stainedhead.paperclipmobile.testing.fakes.FakeBudgetGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeConnectivity
import com.stainedhead.paperclipmobile.testing.fakes.FakeDashboardGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeDecisionGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeRunGateway
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
    private val reads: DemoReadFailures,
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

    /**
     * The factory answers every Factory home, Agents and Run read with a server error (true), or answers again
     * (false), so the error states of those screens can be seen and tried again.
     */
    fun failReads(enabled: Boolean) = reads.fail(enabled)
}

/** The fakes behind Factory home, Agents and the Run viewer, and the reads of theirs that [fail] breaks. */
internal class DemoReadFailures(
    private val agents: FakeAgentGateway,
    private val runs: FakeRunGateway,
    private val dashboard: FakeDashboardGateway,
) {
    fun fail(enabled: Boolean) {
        val error = DomainError.ServerError
        val failing =
            listOf(agents.failures to listOf("list", "get"), runs.failures to listOf("get", "live", "forAgent"))
        for ((failures, names) in failing) {
            for (name in names) if (enabled) failures[name] = error else failures.remove(name)
        }
        if (enabled) dashboard.failures["dashboard"] = error else dashboard.failures.remove("dashboard")
    }
}
