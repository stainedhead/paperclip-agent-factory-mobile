import PaperclipKit
import SwiftUI
import Testing

@testable import iosApp

/// UI-001: which graph the app runs on, who owns it, and how it is torn down.
@MainActor
struct AppEnvironmentTests {
    private func demo(_ extra: [String] = []) -> AppEnvironment {
        AppEnvironment(configuration: LaunchConfiguration(arguments: ["iosApp", "-demo"] + extra))
    }

    @Test func aDemoLaunchRunsOnTheDemoGraphWithItsInboxBadge() async {
        let environment = demo()
        environment.start()
        #expect(environment.mode == .demo)
        #expect(await eventually { environment.app.state.isLoaded })
        #expect(environment.app.state.hasFactories)
        #expect(await eventually { environment.inbox?.state.badgeCount == 3 })
        environment.shutdown()
    }

    @Test func aLaunchWithoutTheDemoFlagStartsWithNoFactoryForFirstRun() async {
        let environment = AppEnvironment(configuration: LaunchConfiguration(arguments: ["iosApp"]))
        environment.start()
        #expect(environment.mode == .live)
        #expect(await eventually { environment.app.state.isLoaded })
        #expect(environment.app.state.hasFactories == false)
        #expect(environment.inbox == nil)
        environment.shutdown()
    }

    @Test func tryingTheDemoFactorySwitchesGraphAndClosesTheOldOwnedModels() async {
        let environment = AppEnvironment(configuration: LaunchConfiguration(arguments: ["iosApp"]))
        environment.start()
        let oldApp = environment.app
        let oldFirstRun = environment.firstRun
        environment.useDemo()
        #expect(environment.mode == .demo)
        #expect(oldApp.isClosed)
        #expect(oldFirstRun.isClosed)
        #expect(environment.app !== oldApp)
        #expect(await eventually { environment.app.state.hasFactories })
        environment.shutdown()
    }

    @Test func shutdownClosesEveryOwnedModelOnceAndIsRepeatable() async {
        let environment = demo()
        environment.start()
        #expect(await eventually { environment.inbox != nil })
        let inbox = environment.inbox
        environment.shutdown()
        environment.shutdown()
        #expect(environment.app.isClosed)
        #expect(environment.firstRun.isClosed)
        #expect(inbox?.isClosed == true)
        #expect(environment.inbox == nil)
    }

    @Test func theLiveProviderIsAPlaceholderThatIsMarkedUnavailable() {
        let provider: any AppGraphProvider = LiveGraphProvider(opener: ASWebAuthSessionPairingBrowser())
        #expect(provider.mode == .live)
        #expect(provider.isAvailable == false)
        // The seam compiles today: it still hands back a graph (the fake-backed one, ADR-037) so first run works.
        #expect(provider.makeGraph().demoControls == nil)
        #expect(DemoGraphProvider().makeGraph().demoControls != nil)
    }

    @Test func scenePhasesDriveTheRefreshLifecycleAndTheSwitcherBlur() async {
        let environment = demo()
        environment.start()
        #expect(environment.isObscured == false)

        environment.scenePhaseChanged(.inactive)
        #expect(environment.isObscured)
        #expect(environment.graph.lifecycle.isForeground.value.boolValue)  // inactive is not background

        environment.scenePhaseChanged(.background)
        #expect(await eventually { environment.graph.lifecycle.isForeground.value.boolValue == false })
        #expect(environment.isObscured)

        environment.scenePhaseChanged(.active)
        #expect(await eventually { environment.graph.lifecycle.isForeground.value.boolValue })
        #expect(environment.isObscured == false)
        environment.shutdown()
    }

    @Test func scriptedBiometricsReachTheDemoGraphAndTheLockHonoursThem() async {
        let environment = demo(["-demo-biometrics", "cancelled,approved"])
        environment.start()
        #expect(await eventually { environment.app.state.isLoaded })
        #expect(environment.app.state.lock == .locked)

        environment.unlock()
        #expect(await eventually { environment.app.state.unlockIssue == .cancelled })
        #expect(environment.app.state.lock == .locked)
        #expect(environment.biometricPromptCount == 1)

        environment.unlock()
        #expect(await eventually { environment.app.state.lock == .unlocked })
        #expect(environment.biometricPromptCount == 2)
        environment.shutdown()
    }

    @Test func aCancelledPromptLeavesADestructiveActionUndone() async {
        // The prompt lives in the shared use case: with Face ID not approved no mutation is sent (AGENTS.md).
        let environment = demo(["-demo-biometrics", "cancelled"])
        environment.start()
        let agents = HolderModel(
            holder: environment.graph.agents.createAgentsStateHolder(instanceId: "demo-home", companyId: "company-acme")
        )
        let observing = Task { await agents.observe() }
        #expect(await eventually { agents.state.phase == .content })
        let before = agents.state.sections.flatMap(\.rows).filter { $0.id == "agent-eng-1" }.map(\.status)

        agents.send(AgentsIntentPauseTapped(agentId: "agent-eng-1"))
        agents.send(AgentsIntentPauseConfirmed.shared)
        #expect(await eventually { environment.biometricPromptCount == 1 })
        #expect(await eventually { agents.state.confirmPauseAgentId == nil })
        let after = agents.state.sections.flatMap(\.rows).filter { $0.id == "agent-eng-1" }.map(\.status)
        #expect(after == before)
        observing.cancel()
        agents.close()
        environment.shutdown()
    }

    @Test func theIdlePeriodLaunchOptionIsAppliedThroughTheSharedSettings() async {
        let environment = demo(["-demo-idle", "immediately"])
        environment.start()
        #expect(await eventually { environment.app.state.isLoaded })
        environment.unlock()
        #expect(await eventually { environment.app.state.lock == .unlocked })

        environment.scenePhaseChanged(.background)
        environment.scenePhaseChanged(.active)
        #expect(await eventually { environment.app.state.lock == .locked })
        environment.shutdown()
    }
}
