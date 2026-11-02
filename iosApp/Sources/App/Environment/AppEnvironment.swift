import Observation
import PaperclipKit
import SwiftUI

/// Which screen model a route (or a tab root) owns.
enum ScreenKey: Hashable {
    case factories
    case manageFactories
    case editFactory(id: String?)
    case settings
    case decision(id: String)
    case approval(id: String)
    case budget(id: String)

    init?(_ sheet: AppSheet) {
        switch sheet {
        case .budgetIncident(let id): self = .budget(id: id)
        case .settings: return nil
        }
    }

    init?(_ route: AppRoute) {
        switch route {
        case .manageFactories: self = .manageFactories
        case .editFactory(let id): self = .editFactory(id: id)
        case .settings, .pairedDevices: self = .settings
        case .decision(let id): self = .decision(id: id)
        case .approval(let id): self = .approval(id: id)
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
    @ObservationIgnored private var wentOffline = false
    @ObservationIgnored private var wentOfflineAfterDetail = false
    @ObservationIgnored private var decisionHints: [String: DecisionHints] = [:]

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

    /// The first-run flow has saved the factory but is not finished: it keeps the screen until Done (also when the
    /// person steps back to the address), so adding the factory does not swap the flow for the tab shell half way.
    private(set) var firstRunInProgress = false

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
        applyStartLocation()
        app.didUpdate = { [weak self] state in self?.appStateChanged(state) }
        observe(app)
        firstRun.didUpdate = { [weak self] state in self?.firstRunChanged(state) }
        observe(firstRun)
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

    private func firstRunChanged(_ state: FirstRunState) {
        switch state.step {
        case .pair, .secure: firstRunInProgress = true
        case .done: firstRunInProgress = false
        case .address: break
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

    /// What the Inbox card knew when it was tapped (agent name, decide-by, snooze key). The decision endpoint has none
    /// of them, so the screen asks for them with its model; without hints it simply has no Snooze.
    func rememberDecisionHints(_ hints: DecisionHints, for id: String) {
        decisionHints[id] = hints
    }

    func decisionModel(id: String) -> HolderModel<DecisionStateHolder> {
        let model = models.model(for: .decision(id: id)) {
            let hints = decisionHints[id] ?? DecisionHints(originAgentName: nil, decideBy: nil, dismissalKey: nil)
            return graph.attention.createDecisionStateHolder(
                instanceId: selectedInstance, companyId: selectedCompany, decisionId: id, hints: hints)
        }
        if configuration.offlineAfterDetail {
            model.didUpdate = { [weak self] in self?.detailLoaded($0.phase) }
        }
        return model
    }

    func approvalModel(id: String) -> HolderModel<ApprovalStateHolder> {
        let model = models.model(for: .approval(id: id)) {
            graph.attention.createApprovalStateHolder(
                instanceId: selectedInstance, companyId: selectedCompany, approvalId: id)
        }
        if configuration.offlineAfterDetail {
            model.didUpdate = { [weak self] in self?.detailLoaded($0.phase) }
        }
        return model
    }

    func budgetModel(id: String) -> HolderModel<BudgetIncidentStateHolder> {
        let model = models.model(for: .budget(id: id)) {
            graph.attention.createBudgetIncidentStateHolder(
                instanceId: selectedInstance, companyId: selectedCompany, incidentId: id)
        }
        if configuration.offlineAfterDetail {
            model.didUpdate = { [weak self] in self?.detailLoaded($0.phase) }
        }
        return model
    }

    private var selectedInstance: String { app.state.selectedInstanceId ?? "" }
    private var selectedCompany: String { app.state.selectedCompanyId ?? "" }

    /// `-demo-offline-after-detail`: the phone goes offline once a detail screen shows its content.
    private func detailLoaded(_ phase: DetailPhase) {
        guard phase == .content, !wentOfflineAfterDetail else { return }
        wentOfflineAfterDetail = true
        graph.demoControls?.setOnline(online: false)
    }

    /// Keeps the tab roots and every model whose route (or sheet) is still showing; closes the rest.
    private func releaseScreenModels() {
        var keep: Set<ScreenKey> = [.factories]
        for route in router.activeRoutes {
            if let key = ScreenKey(route) { keep.insert(key) }
        }
        if let sheet = router.sheet, let key = ScreenKey(sheet) { keep.insert(key) }
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
        model.didUpdate = { [weak self] state in self?.inboxChanged(state) }
        observe(model)
    }

    /// `-demo-offline`: the phone goes offline once, after the cards have loaded, so the offline Inbox keeps them.
    private func inboxChanged(_ state: InboxState) {
        guard configuration.goesOffline, !wentOffline, state.phase == .content else { return }
        wentOffline = true
        graph.demoControls?.setOnline(online: false)
    }

    private func stopInbox() {
        inboxSession?.stop()
        inboxSession = nil
        inbox?.close()
        inbox = nil
        inboxKey = nil
    }

    // MARK: - Demo launch options

    /// For screenshots and UI tests: begin on a tab and, optionally, one pushed screen of the Factories family.
    private func applyStartLocation() {
        guard let tab = configuration.startTab else { return }
        router.select(tab)
        guard let name = configuration.startRoute, let location = StartLocation.parse(name) else { return }
        switch location {
        case .route(let route): router.push(route, in: tab)
        case .sheet(let sheet): router.present(sheet)
        }
    }

    private func applyDemoLaunchOptions() {
        guard let controls = graph.demoControls else { return }
        for result in configuration.scriptedBiometrics {
            controls.scriptBiometrics(result: result)
        }
        for kind in configuration.resolvedElsewhere {
            switch kind {
            case "decision": controls.resolveDecisionElsewhere(decisionId: "dec-1182", optionId: "wait")
            case "approval": controls.resolveApprovalElsewhere(approvalId: "ap-hire-qa")
            case "budget": controls.resolveIncidentElsewhere(incidentId: "inc-eng-2")
            default: break
            }
        }
        if let period = configuration.demoIdlePeriod {
            let settings = HolderModel(holder: graph.createSettingsStateHolder())
            settings.send(SettingsIntentIdlePeriodChosen(period: period))
            launchSettings = settings
            observe(settings)
        }
    }
}
