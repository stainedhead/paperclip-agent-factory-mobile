import PaperclipKit
import Testing

/// Checks that the shared state holders added in slices 1a-1 and 1a-2 are reachable and usable from Swift.
@MainActor
struct GraphInteropTests {
    private let graph = PaperclipCore.shared.createGraph(allowInsecureLocal: false)

    @Test func appStateStartsLockedAndUnloaded() {
        let holder = graph.createAppStateHolder()
        #expect(holder.state.value.isLoaded == false)
        #expect(holder.state.value.lock == .locked)
    }

    @Test func settingsAlwaysKeepDestructiveAuthOn() {
        let holder = graph.createSettingsStateHolder()
        #expect(holder.state.value.destructiveActionAuthAlwaysOn)
    }

    @Test func addModeStartsWithSaveDisabled() {
        let holder = graph.createEditInstanceStateHolder(instanceId: nil)
        #expect(holder.state.value.mode == .add)
        #expect(holder.state.value.canSave == false)
    }

    @Test func firstRunOffersTheDemoOption() {
        let holder = graph.createFirstRunStateHolder()
        holder.onIntent(intent: FirstRunIntentDemoSelected.shared)
        #expect(holder.state.value.isDemo)
        #expect(holder.state.value.step == .done)
    }

    @Test func factoriesAndManageStartEmpty() {
        #expect(graph.createFactoriesStateHolder().state.value.isEmpty)
        #expect(graph.createManageFactoriesStateHolder().state.value.rows.isEmpty)
    }

    @Test func inboxStartsLoadingWithNoBadge() {
        let holder = graph.attention.createInboxStateHolder(instanceId: "inst-1", companyId: "company-acme")
        #expect(holder.state.value.phase == .loading)
        #expect(holder.state.value.badgeCount == 0)
        holder.onIntent(intent: InboxIntentRefresh.shared)
        holder.onIntent(intent: InboxIntentFilterSelected(kind: SourceKindUi.approval))
        holder.close()
    }

    @Test func detailHoldersStartLoadingAndAcceptTypedIntents() {
        let decision = graph.attention.createDecisionStateHolder(
            instanceId: "inst-1", companyId: "company-acme", decisionId: "dec-1", originAgentName: nil, decideBy: nil)
        #expect(decision.state.value.phase == .loading)
        decision.onIntent(intent: DecisionIntentOptionSelected(optionId: "ship"))
        decision.close()

        let approval = graph.attention.createApprovalStateHolder(
            instanceId: "inst-1", companyId: "company-acme", approvalId: "ap-1")
        #expect(approval.state.value.phase == .loading)
        approval.onIntent(intent: ApprovalIntentNoteConfirmed(note: "no"))
        approval.close()

        let budget = graph.attention.createBudgetIncidentStateHolder(
            instanceId: "inst-1", companyId: "company-acme", incidentId: "inc-1")
        #expect(budget.state.value.canSubmit == false)
        budget.onIntent(intent: BudgetIntentChoiceSelected(choice: .raiseAndResume))
        budget.close()
    }
}
