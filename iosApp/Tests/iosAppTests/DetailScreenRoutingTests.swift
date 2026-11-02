import PaperclipKit
import Testing

@testable import iosApp

/// UI-030 plumbing: the three detail screens are owned by the environment like every other screen model.
@MainActor
struct DetailScreenRoutingTests {
    private func demo(_ arguments: [String] = []) -> AppEnvironment {
        let environment = AppEnvironment(configuration: LaunchConfiguration(arguments: ["iosApp", "-demo"] + arguments))
        environment.start()
        return environment
    }

    @Test func aDecisionModelIsKeyedByIdAndClosedWhenItsRouteLeavesTheStack() async {
        let environment = demo()
        #expect(await eventually { environment.app.state.selectedCompanyId != nil })
        environment.router.push(.decision(id: "dec-1182"), in: .inbox)
        let model = environment.decisionModel(id: "dec-1182")
        #expect(environment.decisionModel(id: "dec-1182") === model)
        #expect(environment.decisionModel(id: "dec-1183") !== model)

        environment.router.setPath([], for: .inbox)
        #expect(model.isClosed)
        environment.shutdown()
    }

    @Test func anApprovalModelIsClosedWhenItsRouteLeavesTheStack() async {
        let environment = demo()
        #expect(await eventually { environment.app.state.selectedCompanyId != nil })
        environment.router.push(.approval(id: "ap-hire-qa"), in: .inbox)
        let model = environment.approvalModel(id: "ap-hire-qa")
        environment.router.pop(.inbox)
        #expect(model.isClosed)
        environment.shutdown()
    }

    @Test func theBudgetSheetKeepsItsModelWhileOpenAndClosesItWhenDismissed() async {
        let environment = demo()
        #expect(await eventually { environment.app.state.selectedCompanyId != nil })
        environment.router.present(.budgetIncident(id: "inc-eng-2"))
        let model = environment.budgetModel(id: "inc-eng-2")
        environment.router.push(.settings, in: .factories)  // an unrelated route change must not close it
        #expect(model.isClosed == false)

        environment.router.dismissSheet()
        #expect(model.isClosed)
        environment.shutdown()
    }

    @Test func dismissingTheBudgetSheetRefreshesTheInboxSoAResolvedCardIsGone() async {
        let environment = demo()
        #expect(await eventually { environment.inbox?.state.phase == .content })
        let id = "budget_alert:inc-eng-2"
        func listed() -> Bool {
            environment.inbox?.state.sections.contains { $0.items.contains { $0.id == id } } ?? false
        }
        #expect(listed())
        environment.router.present(.budgetIncident(id: "inc-eng-2"))
        let model = environment.budgetModel(id: "inc-eng-2")
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.phase == .content })
        model.choose(.keepPaused)
        #expect(await eventually { model.state.canSubmit })
        model.submit()
        #expect(await eventually { model.state.outcome == .keptPaused })

        environment.router.dismissSheet()
        #expect(await eventually { !listed() })
        observing.cancel()
        environment.shutdown()
    }

    @Test func aRememberedDecisionHintIsUsedOnceTheScreenModelIsCreated() async {
        let environment = demo()
        #expect(await eventually { environment.app.state.selectedCompanyId != nil })
        environment.rememberDecisionHints(
            DecisionHints(originAgentName: "CTO", decideBy: "today", dismissalKey: "decision:dec-1182"), for: "dec-1182"
        )
        let model = environment.decisionModel(id: "dec-1182")
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.phase == .content })
        #expect(await eventually { model.state.originAgentName == "CTO" })
        #expect(model.state.canSnooze)
        #expect(model.state.decideBy.kind == .today)
        observing.cancel()
        environment.shutdown()
    }

    @Test func withoutHintsThereIsNoSnooze() async {
        let environment = demo()
        #expect(await eventually { environment.app.state.selectedCompanyId != nil })
        let model = environment.decisionModel(id: "dec-1182")
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.phase == .content })
        #expect(model.state.canSnooze == false)
        observing.cancel()
        environment.shutdown()
    }
}

struct DecisionHintsMappingTests {
    private func hints(_ decideBy: DecideByUi) -> DecisionHints {
        DecisionHintMapping.hints(originAgentName: "CTO", decideBy: decideBy, dismissalKey: "k")
    }

    @Test func eachDecideByKindMapsToTheStringTheSharedParserAccepts() {
        #expect(hints(DecideByUi(kind: .today, isoDate: nil)).decideBy == "today")
        #expect(hints(DecideByUi(kind: .thisWeek, isoDate: nil)).decideBy == "this_week")
        #expect(hints(DecideByUi(kind: .whenever, isoDate: nil)).decideBy == "whenever")
        #expect(hints(DecideByUi(kind: .onDate, isoDate: "2026-11-05")).decideBy == "2026-11-05")
        #expect(hints(DecideByUi(kind: .none, isoDate: nil)).decideBy == nil)
    }

    @Test func theAgentNameAndTheSnoozeKeyPassThrough() {
        let result = hints(DecideByUi(kind: .today, isoDate: nil))
        #expect(result.originAgentName == "CTO")
        #expect(result.dismissalKey == "k")
    }
}

struct DetailLaunchOptionsTests {
    @Test func resolvedElsewhereListsTheKindsToResolveBeforeTheScreenOpens() {
        let config = LaunchConfiguration(arguments: [
            "iosApp", "-demo", "-demo-resolved-elsewhere", "decision,approval,budget"
        ])
        #expect(config.resolvedElsewhere == ["decision", "approval", "budget"])
        #expect(LaunchConfiguration(arguments: ["iosApp", "-demo"]).resolvedElsewhere.isEmpty)
    }

    @Test func theServerSpendIsReadAsMinorUnitsAndIgnoredWhenNotANumber() {
        let config = LaunchConfiguration(arguments: ["iosApp", "-demo", "-demo-budget-server-spend", "21000"])
        #expect(config.budgetServerSpend == 21_000)
        #expect(
            LaunchConfiguration(arguments: ["iosApp", "-demo-budget-server-spend", "lots"]).budgetServerSpend == nil)
    }

    @Test func offlineAfterDetailAsksForOfflineOnceADetailScreenHasContent() {
        #expect(LaunchConfiguration(arguments: ["iosApp", "-demo", "-demo-offline-after-detail"]).offlineAfterDetail)
        #expect(LaunchConfiguration(arguments: ["iosApp", "-demo"]).offlineAfterDetail == false)
    }

    @Test func demoOpenNamesDecisionApprovalAndBudgetScreens() {
        #expect(StartLocation.parse("decision:dec-1182") == .route(.decision(id: "dec-1182")))
        #expect(StartLocation.parse("approval:ap-hire-qa") == .route(.approval(id: "ap-hire-qa")))
        #expect(StartLocation.parse("budget:inc-eng-2") == .sheet(.budgetIncident(id: "inc-eng-2")))
        #expect(StartLocation.parse("run:run-1204-live") == .route(.run(id: "run-1204-live")))
        #expect(StartLocation.parse("agent:agent-cto") == .route(.agent(id: "agent-cto")))
        #expect(StartLocation.parse("settings") == .route(.settings))
        #expect(StartLocation.parse("edit:demo-home") == .route(.editFactory(id: "demo-home")))
        #expect(StartLocation.parse("nonsense") == nil)
    }
}
