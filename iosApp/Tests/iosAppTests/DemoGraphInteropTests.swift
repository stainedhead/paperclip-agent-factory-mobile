import PaperclipKit
import Testing

/// Waits until `condition` holds, polling the state holders' current values. The shared holders load on their own
/// dispatcher, so a test never asserts a loading phase: it waits for the settled state and then checks stable values.
@MainActor
func eventually(timeoutSeconds: Int = 10, _ condition: () -> Bool) async -> Bool {
    let deadline = ContinuousClock.now.advanced(by: .seconds(timeoutSeconds))
    while !condition() {
        if ContinuousClock.now > deadline { return false }
        try? await Task.sleep(for: .milliseconds(20))
    }
    return true
}

private let home = "demo-home"
private let acme = "company-acme"

/// Acceptance criterion 5 and 6: the demo graph's state holders render the design mocks' facts, readable from Swift.
@MainActor
struct DemoFactoriesInteropTests {
    private let graph = PaperclipCore.shared.createDemoGraph()

    @Test func factoriesListsHomeCloudAndTestFactory() async {
        let holder = graph.createFactoriesStateHolder()
        #expect(await eventually { holder.state.value.rows.count == 3 })
        holder.onIntent(intent: FactoriesIntentRefresh.shared)
        #expect(await eventually { holder.state.value.rows.first?.companies.isEmpty == false })

        let rows = holder.state.value.rows
        #expect(rows.map(\.name) == ["Home factory", "Cloud factory", "Test factory"])
        #expect(rows[0].status == .signedIn)
        #expect(rows[0].companies.map(\.name) == ["Acme Labs", "Side Project Co"])
        #expect(rows[0].expiry.daysLeft?.intValue == 23)
        #expect(rows[1].status == .signedOut)
        #expect(rows[1].expiry.state == .expired)
        #expect(rows[2].expiry.state == .noExpiry)
    }

    @Test func manageAndEditShowTheSameFactories() async {
        let manage = graph.createManageFactoriesStateHolder()
        let edit = graph.createEditInstanceStateHolder(instanceId: "demo-cloud")
        #expect(await eventually { manage.state.value.rows.count == 3 && !edit.state.value.name.isEmpty })

        #expect(manage.state.value.rows.map(\.name) == ["Home factory", "Cloud factory", "Test factory"])
        #expect(manage.state.value.rows.last?.address == "http://localhost:3100")
        #expect(edit.state.value.name == "Cloud factory")
        #expect(edit.state.value.exposure == .public)
    }

    @Test func settingsShowsThePhoneOfTheMock() async {
        let holder = graph.createSettingsStateHolder()
        #expect(await eventually { holder.state.value.factories.count == 3 })

        #expect(holder.state.value.deviceName == "Alex's iPhone")
        #expect(holder.state.value.appLockEnabled)
        #expect(holder.state.value.idlePeriod == .fiveMinutes)
        #expect(holder.state.value.factories.map(\.name) == ["Home factory", "Cloud factory", "Test factory"])
    }

    @Test func appAndSettingsStartFromTheDemoDevice() async {
        let app = graph.createAppStateHolder()
        let settings = graph.createSettingsStateHolder()
        #expect(await eventually { app.state.value.isLoaded })

        #expect(app.state.value.hasFactories)
        #expect(app.state.value.selectedInstanceName == "Home factory")
        #expect(settings.state.value.destructiveActionAuthAlwaysOn)
    }
}

@MainActor
struct DemoAttentionInteropTests {
    private let graph = PaperclipCore.shared.createDemoGraph()

    @Test func inboxShowsTheMockCountsAndDecideNowCards() async {
        let inbox = graph.attention.createInboxStateHolder(instanceId: home, companyId: acme)
        #expect(await eventually { inbox.state.value.phase == .content })

        let state = inbox.state.value
        #expect(state.badgeCount == 3)
        #expect(state.totalCount == 9)
        #expect(state.sections.first?.group == .decideNow)
        #expect(state.sections.first?.items.map(\.title).first == "Ship the billing migration to production?")
        #expect(state.refreshIntervalSeconds == 15)
        inbox.onIntent(intent: InboxIntentFilterSelected(kind: SourceKindUi.decision))
        #expect(await eventually { inbox.state.value.selectedFilter == .decision })
        inbox.close()
    }

    @Test func decisionOpenedFromACardOffersSnooze() async {
        let hints = DecisionHints(
            originAgentName: "CTO", decideBy: "today", dismissalKey: "attention:decision:dec-1182")
        let decision = graph.attention.createDecisionStateHolder(
            instanceId: home, companyId: acme, decisionId: "dec-1182", hints: hints)
        #expect(await eventually { decision.state.value.phase == .content })

        let state = decision.state.value
        #expect(state.title == "Ship the billing migration to production?")
        #expect(state.options.map(\.label).last == "Cancel the migration")
        #expect(state.options.last?.isDestructive == true)
        #expect(state.canSnooze)
        decision.onIntent(intent: DecisionIntentOptionSelected(optionId: "ship"))
        #expect(await eventually { decision.state.value.canConfirm })
        decision.close()
    }

    @Test func decisionOpenedWithoutACardHidesSnooze() async {
        let hints = DecisionHints(originAgentName: nil, decideBy: nil, dismissalKey: nil)
        let decision = graph.attention.createDecisionStateHolder(
            instanceId: home, companyId: acme, decisionId: "dec-1182", hints: hints)
        #expect(await eventually { decision.state.value.phase == .content })

        #expect(decision.state.value.canSnooze == false)
        decision.close()
    }

    @Test func approvalAndBudgetShowTheMockFacts() async {
        let approval = graph.attention.createApprovalStateHolder(
            instanceId: home, companyId: acme, approvalId: "ap-hire-qa")
        let budget = graph.attention.createBudgetIncidentStateHolder(
            instanceId: home, companyId: acme, incidentId: "inc-eng-2")
        #expect(await eventually { approval.state.value.phase == .content && budget.state.value.phase == .content })

        #expect(approval.state.value.type == .hireAgent)
        #expect(approval.state.value.requestedByAgentName == "CEO")
        #expect(budget.state.value.scopeName == "Engineer 2")
        #expect(budget.state.value.threshold == .hard)
        approval.close()
        budget.close()
    }
}

@MainActor
struct DemoWorkInteropTests {
    private let graph = PaperclipCore.shared.createDemoGraph()

    @Test func workListAndIssueShowPap1204() async {
        let list = graph.work.createWorkListStateHolder(instanceId: home, companyId: acme)
        let issue = graph.work.createIssueStateHolder(instanceId: home, companyId: acme, issueId: "pap-1204")
        #expect(await eventually { list.state.value.phase == .content && issue.state.value.phase == .content })

        let row = list.state.value.rows.first { $0.identifier == "PAP-1204" }
        #expect(row?.title == "Add retries to webhook ingest")
        #expect(row?.status == .blocked)
        #expect(issue.state.value.header?.assigneeName == "Engineer 1")
        issue.onIntent(intent: IssueIntentTabSelected(tab: .runs))
        issue.onIntent(intent: IssueIntentTabSelected(tab: .files))
        #expect(await eventually { issue.state.value.runsLoaded && issue.state.value.filesLoaded })
        #expect(issue.state.value.runs.count == 3)
        #expect(issue.state.value.files.count == 2)
        list.close()
        issue.close()
    }

    @Test func agentsAndAgentDetailShowTheSixAgents() async {
        let agents = graph.agents.createAgentsStateHolder(instanceId: home, companyId: acme)
        let detail = graph.agents.createAgentDetailStateHolder(
            instanceId: home, companyId: acme, agentId: "agent-eng-1")
        #expect(await eventually { agents.state.value.phase == .content && detail.state.value.phase == .content })

        #expect(agents.state.value.sections.map(\.rows.count).reduce(0, +) == 6)
        #expect(detail.state.value.agent?.name == "Engineer 1")
        #expect(detail.state.value.reportsToName == "CTO")
        #expect(detail.state.value.refreshIntervalSeconds == 5)
        agents.close()
        detail.close()
    }

    @Test func runViewerAndFactoryHomeShowTheMockFacts() async {
        let run = graph.runs.createRunViewerStateHolder(
            instanceId: home, companyId: acme, runId: "run-1204-live")
        let factory = graph.factory.createFactoryHomeStateHolder(instanceId: home, companyId: acme)
        #expect(
            await eventually {
                run.state.value.phase == .content && !run.state.value.logText.isEmpty
                    && factory.state.value.phase == .content
            })

        #expect(run.state.value.events.count == 4)
        #expect(run.state.value.logText.contains("pnpm test"))
        #expect(run.state.value.refreshIntervalSeconds == 5)
        #expect(factory.state.value.tiles?.monthSpend.minorUnits == 124_000)
        #expect(factory.state.value.tiles?.pendingApprovals == 2)
        run.close()
        factory.close()
    }
}

/// Flows become `AsyncSequence`, the refresh policy starts from a holder's target, and the lifecycle bridge answers.
@MainActor
struct DemoFlowAndRefreshInteropTests {
    private let graph = PaperclipCore.shared.createDemoGraph()

    @Test func aStateFlowIsAnAsyncSequence() async {
        let factory = graph.factory.createFactoryHomeStateHolder(instanceId: home, companyId: acme)
        let reader = Task { @MainActor in
            for await state in factory.state where state.phase == .content {
                return true
            }
            return false
        }
        let timeout = Task { @MainActor in
            try? await Task.sleep(for: .seconds(10))
            reader.cancel()
        }
        #expect(await reader.value)
        timeout.cancel()
        factory.close()
    }

    @Test func aRefreshSessionRunsAlongsideAHolderAndStopsCleanly() async {
        let agents = graph.agents.createAgentsStateHolder(instanceId: home, companyId: acme)
        #expect(await eventually { agents.state.value.phase == .content })

        let session = graph.refresh.start(target: agents.refreshTarget)
        agents.onIntent(intent: AgentsIntentPauseTapped(agentId: "agent-eng-1"))
        #expect(await eventually { agents.state.value.confirmPauseAgentId == "agent-eng-1" })
        session.stop()
        agents.onIntent(intent: AgentsIntentPauseCancelled.shared)
        #expect(await eventually { agents.state.value.confirmPauseAgentId == nil })
        agents.close()
    }

    @Test func aHoldersRefreshTargetPublishesItsIntervalAsAnAsyncSequence() async {
        let agents = graph.agents.createAgentsStateHolder(instanceId: home, companyId: acme)
        #expect(await eventually { agents.state.value.phase == .content })

        var interval: Int32?
        for await seconds in agents.refreshTarget.refreshIntervalSeconds {
            interval = seconds.int32Value
            break
        }
        #expect(interval == 15)
        agents.close()
    }

    @Test func theLifecycleBridgeFollowsTheShell() async {
        #expect(graph.lifecycle.isForeground.value.boolValue)
        graph.lifecycle.enterBackground()
        #expect(await eventually { graph.lifecycle.isForeground.value.boolValue == false })
        graph.lifecycle.enterForeground()
        #expect(await eventually { graph.lifecycle.isForeground.value.boolValue })
    }
}
