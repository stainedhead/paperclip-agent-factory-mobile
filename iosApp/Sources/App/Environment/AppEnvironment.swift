import Observation
import PaperclipKit
import SwiftUI

/// Owns the shared graph and every long-lived model for the running app: the app shell (lock, selected factory and
/// company), the first-run screen, and the Inbox that feeds the tab badge. The `App` scene creates one and keeps it
/// for the life of the process; nothing else closes these models, and a view's `.task` ending never does.
///
/// Screen models for pushed destinations live in a `ModelStore` keyed by route, closed when the route leaves its
/// stack (later milestones); the environment closes the store on shutdown.
@MainActor
@Observable
final class AppEnvironment {
    private(set) var mode: GraphMode
    private(set) var graph: PaperclipGraph
    private(set) var app: HolderModel<AppStateHolder>
    private(set) var firstRun: FirstRunModel
    private(set) var inbox: HolderModel<InboxStateHolder>?
    private(set) var isObscured = false
    let router = AppRouter()

    /// How many device-owner prompts the demo graph has shown (0 on any other graph). Read live, not observed: the
    /// diagnostics overlay polls it, because prompts also come from use cases that change no app state.
    var biometricPromptCount: Int { Int(graph.demoControls?.biometricPromptCount ?? 0) }
    let configuration: LaunchConfiguration

    @ObservationIgnored private(set) var launchSettings: HolderModel<SettingsStateHolder>?
    @ObservationIgnored private var provider: any AppGraphProvider
    @ObservationIgnored private var inboxKey: [String]?
    @ObservationIgnored private var inboxSession: RefreshSession?
    @ObservationIgnored private var observations: [Task<Void, Never>] = []
    @ObservationIgnored private var started = false

    convenience init(configuration: LaunchConfiguration) {
        self.init(configuration: configuration, provider: Self.provider(for: configuration))
    }

    init(configuration: LaunchConfiguration, provider: any AppGraphProvider) {
        self.configuration = configuration
        self.provider = provider
        mode = provider.mode
        let graph = provider.makeGraph()
        self.graph = graph
        app = HolderModel(holder: graph.createAppStateHolder())
        firstRun = FirstRunModel(holder: graph.createFirstRunStateHolder())
    }

    private static func provider(for configuration: LaunchConfiguration) -> any AppGraphProvider {
        configuration.useDemo ? DemoGraphProvider() : LiveGraphProvider()
    }

    /// Starts mirroring the shell state. Call once from the scene; calling again does nothing.
    func start() {
        guard !started else { return }
        started = true
        applyDemoLaunchOptions()
        app.didUpdate = { [weak self] state in self?.appStateChanged(state) }
        observe(app)
        appStateChanged(app.state)
    }

    /// "Try the demo factory" on the first-run screen: the same app, now on the demo graph.
    func useDemo() {
        replaceGraph(with: DemoGraphProvider())
    }

    func unlock() {
        app.send(AppIntentUnlockRequested.shared)
    }

    /// The scene phase drives the refresh lifecycle (`active` foregrounds, `background` backgrounds) and the blur that
    /// hides content in the app switcher (anything but `active`).
    func scenePhaseChanged(_ phase: ScenePhase) {
        isObscured = phase != .active
        switch phase {
        case .active: graph.lifecycle.setForeground(isForeground: true)
        case .background: graph.lifecycle.setForeground(isForeground: false)
        default: break
        }
    }

    /// Closes every model the environment owns, exactly once, and stops refreshing. Safe to call again.
    func shutdown() {
        tearDown()
    }

    // MARK: - Ownership

    private func replaceGraph(with provider: any AppGraphProvider) {
        tearDown()
        self.provider = provider
        mode = provider.mode
        let graph = provider.makeGraph()
        self.graph = graph
        app = HolderModel(holder: graph.createAppStateHolder())
        firstRun = FirstRunModel(holder: graph.createFirstRunStateHolder())
        started = false
        start()
    }

    private func tearDown() {
        for task in observations { task.cancel() }
        observations = []
        stopInbox()
        launchSettings?.close()
        launchSettings = nil
        app.close()
        firstRun.close()
    }

    private func observe<Holder: StateHolderProtocol>(_ model: HolderModel<Holder>) {
        observations.append(Task { await model.observe() })
    }

    // MARK: - Inbox (feeds the tab badge)

    private func appStateChanged(_ state: AppState) {
        guard let instance = state.selectedInstanceId, let company = state.selectedCompanyId else {
            stopInbox()
            return
        }
        let key = [instance, company]
        guard key != inboxKey else { return }
        stopInbox()
        inboxKey = key
        let holder = graph.attention.createInboxStateHolder(instanceId: instance, companyId: company)
        let model = HolderModel(holder: holder)
        inbox = model
        inboxSession = graph.refresh.start(target: holder.refreshTarget)
        observe(model)
    }

    private func stopInbox() {
        inboxSession?.stop()
        inboxSession = nil
        inbox?.close()
        inbox = nil
        inboxKey = nil
    }

    // MARK: - Demo launch options

    private func applyDemoLaunchOptions() {
        guard let controls = graph.demoControls else { return }
        for result in configuration.scriptedBiometrics {
            controls.scriptBiometrics(result: result)
        }
        if let period = configuration.demoIdlePeriod {
            let settings = HolderModel(holder: graph.createSettingsStateHolder())
            settings.send(SettingsIntentIdlePeriodChosen(period: period))
            launchSettings = settings
            observe(settings)
        }
    }
}
