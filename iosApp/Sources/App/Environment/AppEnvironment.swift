import Observation
import PaperclipKit
import SwiftUI

/// Which screen model a route (or a tab root) owns.
enum ScreenKey: Hashable {
    case factories
    case manageFactories
    case editFactory(id: String?)
    case settings

    init?(_ route: AppRoute) {
        switch route {
        case .manageFactories: self = .manageFactories
        case .editFactory(let id): self = .editFactory(id: id)
        case .settings: self = .settings
        default: return nil
        }
    }
}

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
    /// The page for pairing (an `ASWebAuthenticationSession`); the live provider will pass it to the shared graph.
    let pairingPage: ASWebAuthSessionPairingBrowser

    /// How many device-owner prompts the demo graph has shown (0 on any other graph). Read live, not observed: the
    /// diagnostics overlay polls it, because prompts also come from use cases that change no app state.
    var biometricPromptCount: Int { Int(graph.demoControls?.biometricPromptCount ?? 0) }
    let configuration: LaunchConfiguration

    /// Screen models of the Factories family, closed when their route leaves its stack (see `ScreenKey`).
    @ObservationIgnored let models = ModelStore<ScreenKey>()
    @ObservationIgnored private(set) var launchSettings: HolderModel<SettingsStateHolder>?
    @ObservationIgnored private var provider: any AppGraphProvider
    @ObservationIgnored private var inboxKey: [String]?
    @ObservationIgnored private var inboxSession: RefreshSession?
    @ObservationIgnored private var observations: [Task<Void, Never>] = []
    @ObservationIgnored private var started = false

    convenience init(configuration: LaunchConfiguration) {
        let page = ASWebAuthSessionPairingBrowser()
        self.init(configuration: configuration, provider: Self.provider(for: configuration, page: page), page: page)
    }

    init(
        configuration: LaunchConfiguration, provider: any AppGraphProvider,
        page: ASWebAuthSessionPairingBrowser = ASWebAuthSessionPairingBrowser()
    ) {
        self.configuration = configuration
        self.provider = provider
        pairingPage = page
        mode = provider.mode
        let graph = provider.makeGraph()
        self.graph = graph
        app = HolderModel(holder: graph.createAppStateHolder())
        firstRun = FirstRunModel(holder: graph.createFirstRunStateHolder())
        pairingPage.onEvent = { [weak self] in self?.pairingPageEvent($0) }
        router.onChange = { [weak self] in self?.releaseScreenModels() }
    }

    private static func provider(
        for configuration: LaunchConfiguration, page: ASWebAuthSessionPairingBrowser
    ) -> any AppGraphProvider {
        if configuration.useFirstRunDemo { return FirstRunDemoGraphProvider() }
        return configuration.useDemo ? DemoGraphProvider() : LiveGraphProvider(opener: page)
    }

    /// "Now" for "Updated ..." labels: a graph with its own clock (the demo's is frozen) is measured against that
    /// clock, a live graph against the wall clock (nil).
    var referenceDate: Date? {
        mode == .demo ? Date(timeIntervalSince1970: Double(graph.nowEpochMillis()) / 1000) : nil
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

    /// The pairing page could not be shown: the pairing on screen is cancelled instead of waiting for nothing. (A
    /// closed sheet is not a failure: closing it is how a person finishes approving.)
    func pairingPageEvent(_ event: PairingBrowserEvent) {
        guard event == .couldNotPresent else { return }
        firstRun.send(FirstRunIntentPairCancelled.shared)
        for case let model as HolderModel<EditInstanceStateHolder> in models.allModels {
            model.send(EditInstanceIntentPairCancelled.shared)
        }
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

    // MARK: - Screen models

    func factoriesModel() -> HolderModel<FactoriesStateHolder> {
        models.model(for: .factories) { graph.createFactoriesStateHolder() }
    }

    func manageModel() -> HolderModel<ManageFactoriesStateHolder> {
        models.model(for: .manageFactories) { graph.createManageFactoriesStateHolder() }
    }

    func editModel(id: String?) -> HolderModel<EditInstanceStateHolder> {
        models.model(for: .editFactory(id: id)) { graph.createEditInstanceStateHolder(instanceId: id) }
    }

    func settingsModel() -> HolderModel<SettingsStateHolder> {
        models.model(for: .settings) { graph.createSettingsStateHolder() }
    }

    /// Keeps the tab roots and every model whose route is still on a stack; closes the rest.
    private func releaseScreenModels() {
        var keep: Set<ScreenKey> = [.factories]
        for route in router.activeRoutes {
            if let key = ScreenKey(route) { keep.insert(key) }
        }
        models.retain(only: keep)
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
        models.closeAll()
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
