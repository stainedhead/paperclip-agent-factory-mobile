package com.stainedhead.paperclipmobile.presentation

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** R4: each mutating screen says when it has work in flight; the iPhone keeps such a screen alive until it is done. */
class StateIsWorkingTest {
    @Test
    fun `an idle screen is not working`() {
        DecisionState().isWorking shouldBe false
        ApprovalState().isWorking shouldBe false
        BudgetIncidentState().isWorking shouldBe false
        AgentDetailState().isWorking shouldBe false
        IssueState().isWorking shouldBe false
        RunViewerState().isWorking shouldBe false
        EditInstanceState().isWorking shouldBe false
        SettingsState().isWorking shouldBe false
        AgentsState().isWorking shouldBe false
    }

    @Test
    fun `each screen is working on its own in-flight flag`() {
        DecisionState(isSubmitting = true).isWorking shouldBe true
        ApprovalState(isSubmitting = true).isWorking shouldBe true
        BudgetIncidentState(isSubmitting = true).isWorking shouldBe true
        AgentDetailState(isBusy = true).isWorking shouldBe true
        IssueState(isBusy = true).isWorking shouldBe true
        RunViewerState(isCancelling = true).isWorking shouldBe true
        EditInstanceState(isBusy = true).isWorking shouldBe true
        SettingsState(isBusy = true).isWorking shouldBe true
        AgentsState(busyAgentId = "agent-1").isWorking shouldBe true
    }
}
