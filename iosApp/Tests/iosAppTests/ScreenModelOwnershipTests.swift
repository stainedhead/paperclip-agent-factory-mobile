import Foundation
import PaperclipKit
import SwiftUI
import Testing

@testable import iosApp

/// The lifecycle rule: a screen model is owned by the environment, kept while its route is on a navigation stack and
/// closed once the route leaves it, never because a view task ended.
@MainActor
struct ScreenModelOwnershipTests {
    private func demo(_ arguments: [String] = []) -> AppEnvironment {
        let environment = AppEnvironment(configuration: LaunchConfiguration(arguments: ["iosApp", "-demo"] + arguments))
        environment.start()
        return environment
    }

    @Test func theFactoriesRootModelIsCreatedOnceAndSurvivesNavigation() async {
        let environment = demo()
        let first = environment.factoriesModel()
        environment.router.push(.settings, in: .factories)
        environment.router.popToRoot(.factories)
        #expect(environment.factoriesModel() === first)
        #expect(first.isClosed == false)
        environment.shutdown()
        #expect(first.isClosed)
    }

    @Test func aPushedScreensModelIsClosedExactlyWhenItsRouteLeavesTheStack() async {
        let environment = demo()
        environment.router.push(.manageFactories, in: .factories)
        let manage = environment.manageModel()
        #expect(environment.manageModel() === manage)
        #expect(manage.isClosed == false)

        environment.router.setPath([], for: .factories)  // the system back gesture writes the path back
        #expect(manage.isClosed)
        environment.shutdown()
    }

    @Test func editModelsAreKeyedByFactoryAndAddIsItsOwnKey() async {
        let environment = demo()
        environment.router.push(.editFactory(id: "demo-home"), in: .factories)
        let home = environment.editModel(id: "demo-home")
        let add = environment.editModel(id: nil)
        #expect(home !== add)

        environment.router.setPath([.editFactory(id: "demo-home")], for: .factories)
        #expect(home.isClosed == false)
        #expect(add.isClosed, "no route asks for the add screen any more")
        environment.shutdown()
    }

    @Test func theWorkRootSurvivesNavigationAndAnIssueModelClosesWhenItsRouteLeaves() async {
        let environment = demo()
        #expect(await eventually { !environment.selectedCompany.isEmpty })
        let work = environment.workModel()
        environment.router.push(.issue(id: "pap-1204"), in: .work)
        let issue = environment.issueModel(id: "pap-1204")
        #expect(environment.issueModel(id: "pap-1204") === issue)
        #expect(environment.workModel() === work)

        environment.router.popToRoot(.work)
        #expect(issue.isClosed)
        #expect(work.isClosed == false, "the Work root keeps its filter and search")
        #expect(environment.workModel() === work)
        environment.shutdown()
        #expect(work.isClosed)
    }

    @Test func theIssueOpenedFromTwoTabsIsOneModelKeptUntilBothLeave() async {
        let environment = demo()
        #expect(await eventually { !environment.selectedCompany.isEmpty })
        environment.router.push(.issue(id: "pap-1204"), in: .inbox)
        environment.router.push(.issue(id: "pap-1204"), in: .agents)
        let issue = environment.issueModel(id: "pap-1204")
        environment.router.popToRoot(.inbox)
        #expect(issue.isClosed == false)
        environment.router.popToRoot(.agents)
        #expect(issue.isClosed)
        environment.shutdown()
    }

    @Test func leavingTheGraphClosesEveryScreenModel() async {
        let environment = AppEnvironment(configuration: LaunchConfiguration(arguments: ["iosApp", "-demo-first-run"]))
        environment.start()
        let factories = environment.factoriesModel()
        environment.useDemo()
        #expect(factories.isClosed)
        environment.shutdown()
    }

    @Test func aPairingPageThatCouldNotOpenCancelsThePairingOnScreen() async {
        let environment = AppEnvironment(
            configuration: LaunchConfiguration(arguments: ["iosApp", "-demo-first-run"]))
        environment.start()
        #expect(await eventually { environment.app.state.isLoaded })
        let observing = Task { await environment.firstRun.observe() }
        environment.firstRun.send(FirstRunIntentAddressChanged(text: "https://paperclip.example.org"))
        environment.firstRun.send(FirstRunIntentProbeRequested.shared)
        #expect(await eventually { environment.firstRun.state.probe == .accepted })
        environment.firstRun.send(FirstRunIntentContinueRequested.shared)
        #expect(await eventually { environment.firstRun.state.step == .pair })
        environment.firstRun.send(FirstRunIntentPairRequested.shared)
        #expect(environment.firstRun.state.pairing == .waiting)

        environment.pairingPageEvent(.couldNotPresent)
        #expect(await eventually { environment.firstRun.state.pairing == .cancelled })
        observing.cancel()
        environment.shutdown()
    }

    /// A9: when pairing ends without the approval being used, the page is not left open for someone to approve a
    /// challenge nobody is waiting on.
    @Test func cancellingThePairingOnScreenClosesTheOpenApprovalPage() async {
        let harness = Harness()
        let environment = AppEnvironment(
            configuration: LaunchConfiguration(arguments: ["iosApp", "-demo-first-run"]),
            provider: FirstRunDemoGraphProvider(), page: harness.browser)
        environment.start()
        #expect(await eventually { environment.app.state.isLoaded })
        let observing = Task { await environment.firstRun.observe() }
        environment.firstRun.send(FirstRunIntentAddressChanged(text: "https://paperclip.example.org"))
        environment.firstRun.send(FirstRunIntentProbeRequested.shared)
        #expect(await eventually { environment.firstRun.state.probe == .accepted })
        environment.firstRun.send(FirstRunIntentContinueRequested.shared)
        #expect(await eventually { environment.firstRun.state.step == .pair })
        environment.firstRun.send(FirstRunIntentPairRequested.shared)
        harness.browser.present(url: "https://paperclip.example.org/cli-auth/challenge-TESTONLY")
        #expect(harness.sessions.first?.cancelCount == 0)

        environment.firstRun.send(FirstRunIntentPairCancelled.shared)
        #expect(await eventually { harness.sessions.first?.cancelCount == 1 })
        observing.cancel()
        environment.shutdown()
    }

    @Test func thePageStaysOpenWhileAPairingIsWaiting() {
        #expect(PairingPagePolicy.closesPage(for: .waiting) == false)
        #expect(PairingPagePolicy.closesPage(for: .idle) == false)
        for ended in [PairingStatus.cancelled, .expired, .failed] {
            #expect(PairingPagePolicy.closesPage(for: ended))
        }
    }

    @Test func theFirstRunDemoGraphStartsEmptyAndShowsDemoTimeAsTheReferenceDate() async {
        let environment = AppEnvironment(
            configuration: LaunchConfiguration(arguments: ["iosApp", "-demo-first-run"]))
        environment.start()
        #expect(environment.mode == .demo)
        #expect(await eventually { environment.app.state.isLoaded })
        #expect(environment.app.state.hasFactories == false)
        #expect(environment.referenceDate != nil)
        environment.shutdown()
    }

    @Test func aLiveGraphUsesTheWallClockForUpdatedLabels() {
        let settings = LiveSettings(
            keychainService: "pafm.wall-clock.\(UUID().uuidString)",
            dataDirectory: FileManager.default.temporaryDirectory.appendingPathComponent(
                "pafm-clock-\(UUID().uuidString)"),
            unlockReason: "Unlock", confirmReason: "Confirm", keyReadReason: "Read")
        let environment = AppEnvironment(
            configuration: LaunchConfiguration(arguments: ["iosApp"]),
            provider: LiveGraphProvider(opener: ASWebAuthSessionPairingBrowser(), settings: settings))
        #expect(environment.referenceDate == nil)
        environment.shutdown()
    }

    @Test func theFirstRunScreenStaysUntilDoneEvenAfterSteppingBackToTheAddress() async {
        let environment = AppEnvironment(
            configuration: LaunchConfiguration(arguments: ["iosApp", "-demo-first-run"]))
        environment.start()
        #expect(await eventually { environment.app.state.isLoaded })
        #expect(environment.firstRunInProgress == false)
        environment.firstRun.send(FirstRunIntentAddressChanged(text: "https://paperclip.example.org"))
        environment.firstRun.send(FirstRunIntentProbeRequested.shared)
        #expect(await eventually { environment.firstRun.state.probe == .accepted })
        environment.firstRun.send(FirstRunIntentContinueRequested.shared)
        #expect(await eventually { environment.firstRun.state.step == .pair })
        #expect(environment.firstRunInProgress)

        environment.firstRun.send(FirstRunIntentBackRequested.shared)
        #expect(environment.firstRun.state.step == .address)
        #expect(environment.firstRunInProgress, "the factory is saved, so the flow is still open")
        environment.shutdown()
    }
}
