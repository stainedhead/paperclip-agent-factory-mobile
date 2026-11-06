import PaperclipKit
import Testing

@testable import iosApp

/// Factory home, Agents, Agent detail and the Run viewer are owned by the environment like every other screen model:
/// the tab roots by company, the pushed screens by route.
@MainActor
struct OperationsRoutingTests {
    private func demo() -> AppEnvironment {
        let environment = AppEnvironment(configuration: LaunchConfiguration(arguments: ["iosApp", "-demo"]))
        environment.start()
        return environment
    }

    @Test func theTabRootModelsAreCreatedOncePerCompanyAndSurviveNavigation() async {
        let environment = demo()
        #expect(await eventually { environment.app.state.selectedCompanyId != nil })
        let factory = environment.factoryHomeModel()
        let agents = environment.agentsModel()
        #expect(environment.factoryHomeModel() === factory)
        #expect(environment.agentsModel() === agents)

        environment.router.push(.run(id: "run-1204-live"), in: .factory)
        environment.router.popToRoot(.factory)
        #expect(factory.isClosed == false)
        #expect(agents.isClosed == false)
        environment.shutdown()
        #expect(factory.isClosed && agents.isClosed)
    }

    @Test func switchingCompanyClosesTheOldRootsAndGivesNewOnes() async {
        let environment = demo()
        #expect(await eventually { environment.app.state.selectedCompanyId == "company-acme" })
        let factory = environment.factoryHomeModel()
        environment.app.send(AppIntentCompanySelected(instanceId: "demo-home", companyId: "company-side"))
        #expect(await eventually { environment.app.state.selectedCompanyId == "company-side" })
        #expect(factory.isClosed)
        #expect(environment.factoryHomeModel() !== factory)
        environment.shutdown()
    }

    @Test func aRunAndAnAgentDetailAreClosedWhenTheirRoutesLeaveTheStack() async {
        let environment = demo()
        #expect(await eventually { environment.app.state.selectedCompanyId != nil })
        environment.router.push(.agent(id: "agent-eng-1"), in: .agents)
        environment.router.push(.run(id: "run-1204-live"), in: .agents)
        let detail = environment.agentDetailModel(id: "agent-eng-1")
        let run = environment.runModel(id: "run-1204-live")
        #expect(environment.runModel(id: "run-1204-live") === run)

        environment.router.pop(.agents)
        #expect(run.isClosed)
        #expect(detail.isClosed == false)
        environment.router.popToRoot(.agents)
        #expect(detail.isClosed)
        environment.shutdown()
    }

    @Test func goingOfflineAfterTheFirstDetailAppliesToTheNewScreensToo() async {
        let environment = AppEnvironment(
            configuration: LaunchConfiguration(arguments: ["iosApp", "-demo", "-demo-offline-after-detail"]))
        environment.start()
        #expect(await eventually { environment.app.state.selectedCompanyId != nil })
        let model = environment.factoryHomeModel()
        let observing = Task { await model.observe() }
        #expect(await eventually { model.state.isOffline })
        #expect(model.state.tiles != nil, "the cached tiles stay readable")
        observing.cancel()
        environment.shutdown()
    }
}
