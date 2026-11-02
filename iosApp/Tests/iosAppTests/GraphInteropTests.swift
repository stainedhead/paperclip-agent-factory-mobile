import PaperclipKit
import Testing

/// Checks that the shared state holders added in slices 1a-1 to 1a-3 are reachable and usable from Swift.
@MainActor
struct GraphInteropTests {
    private let graph = PaperclipCore.shared.createGraph(allowInsecureLocal: false)
    private let demo = PaperclipCore.shared.createDemoGraph()
    private let home = "demo-home"
    private let acme = "company-acme"

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
        holder.onIntent(intent: InboxIntentFilterSelected(kind: SourceKindUi.approval))
        #expect(await eventually { holder.state.value.selectedFilter == .approval })
        holder.onIntent(intent: InboxIntentRefresh.shared)
        holder.close()
    }

    @Test func detailHoldersChangeStateForTheirIntents() async {
        let decision = demo.attention.createDecisionStateHolder(
            instanceId: home, companyId: acme, decisionId: "dec-1182",
            hints: DecisionHints(originAgentName: nil, decideBy: nil, dismissalKey: nil))
        #expect(await eventually { decision.state.value.phase == .content })
        decision.onIntent(intent: DecisionIntentOptionSelected(optionId: "ship"))
        #expect(await eventually { decision.state.value.canConfirm })
        decision.close()

        let approval = demo.attention.createApprovalStateHolder(
            instanceId: home, companyId: acme, approvalId: "ap-hire-qa")
        #expect(await eventually { approval.state.value.phase == .content })
        approval.onIntent(intent: ApprovalIntentRejectTapped.shared)
        #expect(await eventually { approval.state.value.notePrompt == .reject })
        approval.onIntent(intent: ApprovalIntentNoteCancelled.shared)
        #expect(await eventually { approval.state.value.notePrompt == nil })
        approval.close()

        let budget = demo.attention.createBudgetIncidentStateHolder(
            instanceId: home, companyId: acme, incidentId: "inc-eng-2")
        #expect(await eventually { budget.state.value.phase == .content })
        budget.onIntent(intent: BudgetIntentChoiceSelected(choice: .raiseAndResume))
        #expect(await eventually { budget.state.value.choice == .raiseAndResume })
        budget.close()
    }

    @Test func workScreensChangeStateForTheirIntents() async {
        let list = demo.work.createWorkListStateHolder(instanceId: home, companyId: acme)
        #expect(await eventually { list.state.value.phase == .content })
        #expect(list.state.value.filter == .allOpen)
        list.onIntent(intent: WorkListIntentFilterSelected(filter: .blocked))
        #expect(await eventually { list.state.value.filter == .blocked })
        list.onIntent(intent: WorkListIntentSearchChanged(text: "pap"))
        #expect(await eventually { list.state.value.search == "pap" })
        list.close()

        let issue = demo.work.createIssueStateHolder(instanceId: home, companyId: acme, issueId: "pap-1204")
        #expect(await eventually { issue.state.value.phase == .content })
        #expect(issue.state.value.tab == .thread)
        issue.onIntent(intent: IssueIntentTabSelected(tab: .runs))
        #expect(await eventually { issue.state.value.tab == .runs })
        issue.close()
    }

    @Test func agentScreensChangeStateForTheirIntents() async {
        let agents = demo.agents.createAgentsStateHolder(instanceId: home, companyId: acme)
        #expect(await eventually { agents.state.value.phase == .content })
        #expect(agents.state.value.refreshIntervalSeconds == 15)
        agents.onIntent(intent: AgentsIntentPauseTapped(agentId: "agent-eng-1"))
        #expect(await eventually { agents.state.value.confirmPauseAgentId == "agent-eng-1" })
        agents.onIntent(intent: AgentsIntentPauseCancelled.shared)
        #expect(await eventually { agents.state.value.confirmPauseAgentId == nil })
        agents.close()

        let detail = demo.agents.createAgentDetailStateHolder(
            instanceId: home, companyId: acme, agentId: "agent-eng-1")
        #expect(await eventually { detail.state.value.phase == .content })
        #expect(detail.state.value.confirmPause == false)
        detail.onIntent(intent: AgentDetailIntentPauseTapped.shared)
        #expect(await eventually { detail.state.value.confirmPause })
        detail.onIntent(intent: AgentDetailIntentPauseCancelled.shared)
        #expect(await eventually { detail.state.value.confirmPause == false })
        detail.close()
    }

    @Test func runViewerAndFactoryHomeChangeStateForTheirIntents() async {
        let run = demo.runs.createRunViewerStateHolder(instanceId: home, companyId: acme, runId: "run-1204-live")
        #expect(await eventually { run.state.value.phase == .content })
        #expect(run.state.value.autoScroll)
        #expect(run.state.value.confirmCancel == false)
        run.onIntent(intent: RunViewerIntentUserScrolled(atBottom: false))
        #expect(await eventually { run.state.value.autoScroll == false })
        run.onIntent(intent: RunViewerIntentCancelTapped.shared)
        #expect(await eventually { run.state.value.confirmCancel })
        run.close()

        let factory = demo.factory.createFactoryHomeStateHolder(instanceId: home, companyId: acme)
        #expect(await eventually { factory.state.value.phase == .content })
        #expect(factory.state.value.refreshIntervalSeconds == 15)
        factory.onIntent(intent: FactoryHomeIntentRefresh.shared)
        #expect(await eventually { factory.state.value.phase == .content })
        factory.close()
    }

    /// Intents that start a mutation are not sent here: the holder would be closed while the call is in flight. The
    /// Kotlin tests cover their behaviour; this only proves the constructors and their labels exist for Swift.
    @Test func mutationIntentsAreReachableFromSwift() {
        _ = WorkListIntentCreateSubmitted(title: "New", priority: .high)
        _ = IssueIntentCommentSubmitted(body: "hi", reopen: false, interrupt: false)
        _ = IssueIntentStatusSelected(status: .inReview)
        _ = IssueIntentInteractionVerdictChosen(interactionId: "i", itemId: "x", verdict: .reject)
        _ = AgentDetailIntentBudgetSubmitted(minorUnits: 50_000)
        _ = ApprovalIntentNoteConfirmed(note: "no")
    }
}
