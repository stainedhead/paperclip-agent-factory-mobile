import Foundation
import PaperclipKit
import Testing

@testable import iosApp

/// Factory home, Agents, Agent detail and the Run viewer through their models on the demo graph. The models mirror
/// the shared state and forward intents; what is offered, when Face ID is asked and what a run shows are shared.
@MainActor
struct OperationsModelTests {
    private let home = "demo-home"
    private let acme = "company-acme"

    private func graph(_ biometrics: [BiometricResult] = []) -> (PaperclipGraph, DemoControls) {
        let graph = PaperclipCore.shared.createDemoGraph()
        guard let controls = graph.demoControls else { fatalError("the demo graph has demo controls") }
        for result in biometrics { controls.scriptBiometrics(result: result) }
        return (graph, controls)
    }

    private func row(_ state: AgentsState, _ name: String) -> AgentRowUi? {
        state.sections.flatMap { $0.rows }.first { $0.name == name }
    }

    // MARK: Factory home

    @Test func factoryHomeCarriesTheMocksNumbers() async {
        let (graph, _) = graph()
        let model = FactoryHomeModel(
            holder: graph.factory.createFactoryHomeStateHolder(instanceId: home, companyId: acme))
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.phase == .content && !model.state.spend.isEmpty })
        let tiles = model.state.tiles
        #expect(tiles?.monthSpend.minorUnits == 124_000)
        #expect(tiles?.monthBudget.minorUnits == 200_000)
        #expect(tiles?.agentsRunning == 3 && tiles?.agentsError == 1 && tiles?.agentsPaused == 1)
        #expect(tiles?.tasksOpen == 18 && tiles?.tasksBlocked == 2 && tiles?.tasksInProgress == 5)
        #expect(tiles?.pendingApprovals == 2 && tiles?.budgetIncidents == 1)
        #expect(model.state.spend.map { $0.cost.minorUnits } == [3_800, 21_200, 101_500])
        #expect(Set(model.state.liveRuns.compactMap { $0.issueIdentifier }) == ["PAP-1204", "PAP-1182", "PAP-1199"])
        observing.cancel()
        model.close()
    }

    @Test func factoryHomeShowsTheErrorAndRecoversOnRefresh() async {
        let (graph, controls) = graph()
        controls.failReads(enabled: true)
        let model = FactoryHomeModel(
            holder: graph.factory.createFactoryHomeStateHolder(instanceId: home, companyId: acme))
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.phase == .failed && model.state.error != nil })
        #expect(model.state.error?.key == .serverError)
        controls.failReads(enabled: false)
        model.refresh()
        #expect(await eventually { model.state.phase == .content })
        observing.cancel()
        model.close()
    }

    // MARK: Agents

    private func agentsModel(_ graph: PaperclipGraph, company: String? = nil) -> AgentsModel {
        AgentsModel(holder: graph.agents.createAgentsStateHolder(instanceId: home, companyId: company ?? acme))
    }

    @Test func pausingAsksFaceIDAndACancelledPromptKeepsTheAgentRunning() async {
        let (graph, controls) = graph([.cancelled, .approved])
        let model = agentsModel(graph)
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.phase == .content })
        let cto = row(model.state, "CTO")
        #expect(cto?.actions == [.pause, .runNow])

        model.pause("agent-cto")
        #expect(await eventually { model.state.confirmPauseAgentId == "agent-cto" })
        model.confirmPause()
        #expect(await eventually { model.state.feedback?.deviceAuth == .cancelled })
        #expect(row(model.state, "CTO")?.status == .running)
        #expect(controls.biometricPromptCount == 1)

        model.pause("agent-cto")
        model.confirmPause()
        #expect(await eventually { row(model.state, "CTO")?.status == .paused })
        #expect(row(model.state, "CTO")?.actions == [.resume])
        #expect(controls.biometricPromptCount == 2)
        observing.cancel()
        model.close()
    }

    @Test func resumeAsksNoFaceIDAndRunNowStartsARun() async {
        let (graph, controls) = graph([.approved])
        let model = agentsModel(graph)
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.phase == .content })
        model.pause("agent-cto")
        model.confirmPause()
        #expect(await eventually { row(model.state, "CTO")?.status == .paused })
        model.resume("agent-cto")
        #expect(await eventually { row(model.state, "CTO")?.status == .idle })
        #expect(controls.biometricPromptCount == 1, "resume is not a Face ID action")

        model.runNow("agent-qa")
        #expect(await eventually { row(model.state, "QA agent")?.status == .running })
        #expect(row(model.state, "QA agent")?.currentRun != nil)
        observing.cancel()
        model.close()
    }

    @Test func aBudgetPausedAgentHasNoResumeAndOnlyAnOwnerCanSetBudgets() async {
        let (graph, _) = graph()
        let owner = agentsModel(graph)
        let restricted = agentsModel(graph, company: "company-side")
        let observing = [Task { await owner.observe() }, Task { await restricted.observe() }]
        #expect(await eventually { owner.state.phase == .content && restricted.state.phase == .content })
        #expect(row(owner.state, "Engineer 2")?.isBudgetPaused == true)
        #expect(row(owner.state, "Engineer 2")?.actions.isEmpty == true)
        #expect(owner.state.canSetBudget)
        #expect(restricted.state.canSetBudget == false)
        #expect(restricted.state.budgetDisabledReason == .ownerRole)
        for task in observing { task.cancel() }
        owner.close()
        restricted.close()
    }

    // MARK: Agent detail

    @Test func agentDetailShowsEngineerOneAndAnOwnerSetsTheBudget() async {
        let (graph, _) = graph()
        let model = AgentDetailModel(
            holder: graph.agents.createAgentDetailStateHolder(
                instanceId: home, companyId: acme, agentId: "agent-eng-1"))
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.phase == .content })
        #expect(model.state.reportsToName == "CTO")
        #expect(model.state.currentIssue?.identifier == "PAP-1204")
        #expect(model.state.canSetBudget)
        #expect(model.state.monthlyBudget?.minorUnits == 100_000)

        model.typeBudget(text: "1500", currency: "USD", locale: Locale(identifier: "en_US"))
        #expect(await eventually { model.state.canSaveBudget })
        model.saveBudget()
        #expect(await eventually { model.state.monthlyBudget?.minorUnits == 150_000 })
        #expect(model.state.budgetSavedCount == 1)
        observing.cancel()
        model.close()
    }

    @Test func anOperatorSeesWhySettingABudgetIsUnavailable() async {
        let (graph, _) = graph()
        let model = AgentDetailModel(
            holder: graph.agents.createAgentDetailStateHolder(
                instanceId: home, companyId: "company-side", agentId: "agent-eng-1"))
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.phase == .content })
        #expect(model.state.canSetBudget == false)
        #expect(model.state.budgetDisabledReason == .ownerRole)
        observing.cancel()
        model.close()
    }

    // MARK: Run viewer

    private func runModel(_ graph: PaperclipGraph, id: String = "run-1204-live") -> RunViewerModel {
        RunViewerModel(
            holder: graph.runs.createRunViewerStateHolder(instanceId: home, companyId: acme, runId: id))
    }

    @Test func theRunViewerShowsTheMocksEventsAndLog() async {
        let (graph, _) = graph()
        let model = runModel(graph)
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.phase == .content && !model.state.logText.isEmpty })
        #expect(model.state.run?.issueIdentifier == "PAP-1204")
        #expect(
            model.state.events.map { $0.message } == [
                "Checked out PAP-1204", "Opened a branch and edited 3 files", "Ran tests: 2 failing",
                "Asked you 2 questions"
            ])
        #expect(model.state.logText.contains("Tests: 2 failed, 14 passed"))
        #expect(model.state.refreshIntervalSeconds == 5)
        observing.cancel()
        model.close()
    }

    @Test func autoScrollIsToggledByTheIntentsNotByASwiftFlag() async {
        let (graph, _) = graph()
        let model = runModel(graph)
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.phase == .content })
        #expect(model.state.autoScroll)
        model.userScrolled(atBottom: false)
        #expect(await eventually { model.state.autoScroll == false })
        model.jumpToLatest()
        #expect(await eventually { model.state.autoScroll })
        observing.cancel()
        model.close()
    }

    @Test func cancellingARunAsksFaceIDAndACancelledPromptKeepsItRunning() async {
        let (graph, controls) = graph([.cancelled, .approved])
        let model = runModel(graph)
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.phase == .content })
        #expect(model.state.canCancel)

        model.cancel()
        #expect(await eventually { model.state.confirmCancel })
        model.confirmCancel()
        #expect(await eventually { model.state.feedback?.deviceAuth == .cancelled })
        #expect(model.state.run?.status == .running)

        model.cancel()
        model.confirmCancel()
        #expect(await eventually { model.state.run?.status == .cancelled })
        #expect(model.state.canCancel == false)
        #expect(controls.biometricPromptCount == 2)
        observing.cancel()
        model.close()
    }

    @Test func aFinishedRunCannotBeCancelledAndNeedsNoRefresh() async {
        let (graph, _) = graph()
        let model = runModel(graph, id: "run-1204-done")
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.phase == .content })
        #expect(model.state.run?.status == .succeeded)
        #expect(model.state.canCancel == false)
        #expect(model.state.refreshIntervalSeconds == 0)
        observing.cancel()
        model.close()
    }
}
