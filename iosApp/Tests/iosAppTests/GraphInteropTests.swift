import PaperclipKit
import Testing

/// Checks that the shared state holders added in slices 1a-1 to 1a-3 are reachable and usable from Swift.
@MainActor
struct GraphInteropTests {
    private let graph = PaperclipCore.shared.createGraph(allowInsecureLocal: false)

    @Test func appStateIsLockedOnceItsSettingsHaveLoaded() async {
        let holder = graph.createAppStateHolder()
        #expect(await eventually { holder.state.value.isLoaded })
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

    @Test func inboxAcceptsTypedIntentsOnceItHasSettled() async {
        let holder = graph.attention.createInboxStateHolder(instanceId: "inst-1", companyId: "company-acme")
        #expect(await eventually { holder.state.value.phase != .loading })
        holder.onIntent(intent: InboxIntentRefresh.shared)
        holder.onIntent(intent: InboxIntentFilterSelected(kind: SourceKindUi.approval))
        holder.close()
    }

    @Test func detailHoldersAcceptTypedIntents() {
        let decision = graph.attention.createDecisionStateHolder(
            instanceId: "inst-1", companyId: "company-acme", decisionId: "dec-1",
            hints: DecisionHints(originAgentName: nil, decideBy: nil, dismissalKey: nil))
        decision.onIntent(intent: DecisionIntentOptionSelected(optionId: "ship"))
        decision.close()

        let approval = graph.attention.createApprovalStateHolder(
            instanceId: "inst-1", companyId: "company-acme", approvalId: "ap-1")
        approval.onIntent(intent: ApprovalIntentNoteConfirmed(note: "no"))
        approval.close()

        let budget = graph.attention.createBudgetIncidentStateHolder(
            instanceId: "inst-1", companyId: "company-acme", incidentId: "inc-1")
        budget.onIntent(intent: BudgetIntentChoiceSelected(choice: .raiseAndResume))
        budget.close()
    }

    @Test func workScreensAcceptTypedIntents() {
        let list = graph.work.createWorkListStateHolder(instanceId: "inst-1", companyId: "company-acme")
        #expect(list.state.value.filter == .allOpen)
        list.onIntent(intent: WorkListIntentFilterSelected(filter: .blocked))
        list.onIntent(intent: WorkListIntentSearchChanged(text: "pap"))
        list.onIntent(intent: WorkListIntentCreateSubmitted(title: "New", priority: .high))
        list.close()

        let issue = graph.work.createIssueStateHolder(
            instanceId: "inst-1", companyId: "company-acme", issueId: "pap-1182")
        #expect(issue.state.value.tab == .thread)
        issue.onIntent(intent: IssueIntentTabSelected(tab: .runs))
        issue.onIntent(intent: IssueIntentCommentSubmitted(body: "hi", reopen: false, interrupt: false))
        issue.onIntent(intent: IssueIntentStatusSelected(status: .inReview))
        issue.onIntent(
            intent: IssueIntentInteractionVerdictChosen(interactionId: "i", itemId: "x", verdict: .reject))
        issue.close()
    }

    @Test func agentScreensAcceptTypedIntents() {
        let agents = graph.agents.createAgentsStateHolder(instanceId: "inst-1", companyId: "company-acme")
        #expect(agents.state.value.refreshIntervalSeconds == 15)
        agents.onIntent(intent: AgentsIntentPauseTapped(agentId: "agent-eng-1"))
        agents.onIntent(intent: AgentsIntentPauseCancelled.shared)
        agents.close()

        let detail = graph.agents.createAgentDetailStateHolder(
            instanceId: "inst-1", companyId: "company-acme", agentId: "agent-eng-1")
        #expect(detail.state.value.confirmPause == false)
        detail.onIntent(intent: AgentDetailIntentBudgetSubmitted(minorUnits: 50_000))
        detail.close()
    }

    @Test func runViewerAndFactoryHomeAcceptTypedIntents() {
        let run = graph.runs.createRunViewerStateHolder(
            instanceId: "inst-1", companyId: "company-acme", runId: "run-live")
        #expect(run.state.value.autoScroll)
        #expect(run.state.value.confirmCancel == false)
        run.onIntent(intent: RunViewerIntentUserScrolled(atBottom: false))
        run.onIntent(intent: RunViewerIntentCancelTapped.shared)
        run.close()

        let home = graph.factory.createFactoryHomeStateHolder(instanceId: "inst-1", companyId: "company-acme")
        #expect(home.state.value.refreshIntervalSeconds == 15)
        home.onIntent(intent: FactoryHomeIntentRefresh.shared)
        home.close()
    }
}
