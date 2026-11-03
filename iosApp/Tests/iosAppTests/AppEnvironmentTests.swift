import Foundation
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

    @Test func theOfflineLaunchOptionGoesOfflineOnlyAfterTheInboxHasContent() async {
        let environment = demo(["-demo-offline"])
        environment.start()
        #expect(await eventually { environment.inbox?.state.isOffline == true })
        #expect(environment.inbox?.state.phase == .content, "the cards were loaded before the phone went offline")
        #expect(environment.inbox?.state.actionsDisabledReason == .offline)
        environment.shutdown()
    }

    private func liveSettings() -> LiveSettings {
        LiveSettings(
            keychainService: "pafm.app-env.\(UUID().uuidString)",
            dataDirectory: FileManager.default.temporaryDirectory.appendingPathComponent(
                "pafm-env-\(UUID().uuidString)"),
            unlockReason: "Unlock Paperclip", confirmReason: "Confirm this action")
    }

    /// A provider whose first graph cannot be opened (the database is busy) and whose second can.
    private final class FlakyProvider: AppGraphProvider {
        struct Busy: Error {}
        let mode = GraphMode.live
        var failures = 1

        func makeGraph() throws -> PaperclipGraph {
            if failures > 0 {
                failures -= 1
                throw Busy()
            }
            return PaperclipCore.shared.createFirstRunDemoGraph()
        }
    }

    @Test func aLaunchWithoutTheDemoFlagOpensTheRealGraphAndStartsAtFirstRun() async {
        let environment = AppEnvironment(
            configuration: LaunchConfiguration(arguments: ["iosApp"]),
            provider: LiveGraphProvider(opener: ASWebAuthSessionPairingBrowser(), settings: liveSettings()))
        environment.start()
        #expect(environment.mode == .live)
        #expect(environment.startupNotice == nil)
        #expect(await eventually { environment.app.state.isLoaded })
        #expect(environment.app.state.hasFactories == false)
        #expect(environment.graph.demoControls == nil, "the real graph has no demo controls")
        #expect(environment.biometricPromptCount == 0)
        environment.shutdown()
    }

    @Test func aGraphThatCannotBeOpenedShowsTheNoticeRunsNothingAndRetryOpensIt() async {
        let provider = FlakyProvider()
        let environment = AppEnvironment(
            configuration: LaunchConfiguration(arguments: ["iosApp"]), provider: provider)
        environment.start()
        #expect(environment.startupNotice == .liveGraphUnavailable)
        // The fake graph that stands in is never running: no state holders, no refresh, no Face ID that approves all.
        #expect(environment.app.isClosed)
        #expect(environment.firstRun.isClosed)
        #expect(environment.inbox == nil)
        #expect(environment.biometricPromptCount == 0)

        environment.retryLaunch()
        #expect(environment.startupNotice == nil)
        #expect(await eventually { environment.app.state.isLoaded })
        environment.shutdown()
    }

    @Test func aDemoLaunchHasNoStartupNotice() {
        let environment = demo()
        #expect(environment.startupNotice == nil)
        environment.shutdown()
    }

    @Test func tryingTheDemoFactorySwitchesGraphAndClosesTheOldOwnedModels() async {
        let environment = AppEnvironment(configuration: LaunchConfiguration(arguments: ["iosApp", "-demo-first-run"]))
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

    @Test func theLiveProviderBuildsTheRealGraphAndTheDemoProviderTheFakeOne() throws {
        let provider: any AppGraphProvider = LiveGraphProvider(
            opener: ASWebAuthSessionPairingBrowser(), settings: liveSettings())
        #expect(provider.mode == .live)
        let live = try provider.makeGraph()
        #expect(live.demoControls == nil)
        live.close()
        #expect(try DemoGraphProvider().makeGraph().demoControls != nil)
    }

    @Test func theLiveSettingsCarryTheLocalisedFaceIDReasonsAndTheAppsKeychainService() {
        let settings = LiveSettings.app
        #expect(settings.keychainService == "com.stainedhead.paperclipmobile.board-keys")
        #expect(settings.unlockReason == ShellLabel.biometricUnlockReason.text)
        #expect(settings.confirmReason == ShellLabel.biometricConfirmReason.text)
        #expect(settings.dataDirectory.path.contains("Application Support"))
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
