import Observation
import PaperclipKit

/// What every shared state holder looks like from Swift: a `StateFlow` of immutable states, one intent entry point
/// and `close()`. The holders already have these members, so each conformance below is empty apart from the intent
/// type; `HolderModel` works for any of them.
protocol StateHolderProtocol: AnyObject {
    associatedtype State: AnyObject
    associatedtype Intent
    var state: SkieSwiftStateFlow<State> { get }
    func onIntent(intent: Intent)
    func close()
    /// True while this state has a mutation in flight (or a Face ID prompt up): the screen must not be torn down yet.
    static func isWorking(_ state: State) -> Bool
}

extension StateHolderProtocol {
    static func isWorking(_ state: State) -> Bool { false }
}

extension DecisionStateHolder { static func isWorking(_ state: DecisionState) -> Bool { state.isSubmitting } }
extension ApprovalStateHolder { static func isWorking(_ state: ApprovalState) -> Bool { state.isSubmitting } }
extension BudgetIncidentStateHolder {
    static func isWorking(_ state: BudgetIncidentState) -> Bool { state.isSubmitting }
}
extension AgentDetailStateHolder { static func isWorking(_ state: AgentDetailState) -> Bool { state.isBusy } }
extension IssueStateHolder { static func isWorking(_ state: IssueState) -> Bool { state.isBusy } }
extension RunViewerStateHolder { static func isWorking(_ state: RunViewerState) -> Bool { state.isCancelling } }
extension EditInstanceStateHolder { static func isWorking(_ state: EditInstanceState) -> Bool { state.isBusy } }
extension SettingsStateHolder { static func isWorking(_ state: SettingsState) -> Bool { state.isBusy } }
extension AgentsStateHolder { static func isWorking(_ state: AgentsState) -> Bool { state.busyAgentId != nil } }

extension FirstRunStateHolder: StateHolderProtocol { typealias Intent = any FirstRunIntent }
extension AppStateHolder: StateHolderProtocol { typealias Intent = any AppIntent }
extension FactoriesStateHolder: StateHolderProtocol { typealias Intent = any FactoriesIntent }
extension ManageFactoriesStateHolder: StateHolderProtocol { typealias Intent = any ManageFactoriesIntent }
extension SettingsStateHolder: StateHolderProtocol { typealias Intent = any SettingsIntent }
extension EditInstanceStateHolder: StateHolderProtocol { typealias Intent = any EditInstanceIntent }
extension InboxStateHolder: StateHolderProtocol { typealias Intent = any InboxIntent }
extension DecisionStateHolder: StateHolderProtocol { typealias Intent = any DecisionIntent }
extension ApprovalStateHolder: StateHolderProtocol { typealias Intent = any ApprovalIntent }
extension BudgetIncidentStateHolder: StateHolderProtocol { typealias Intent = any BudgetIntent }
extension WorkListStateHolder: StateHolderProtocol { typealias Intent = any WorkListIntent }
extension IssueStateHolder: StateHolderProtocol { typealias Intent = any IssueIntent }
extension AgentsStateHolder: StateHolderProtocol { typealias Intent = any AgentsIntent }
extension AgentDetailStateHolder: StateHolderProtocol { typealias Intent = any AgentDetailIntent }
extension RunViewerStateHolder: StateHolderProtocol { typealias Intent = any RunViewerIntent }
extension FactoryHomeStateHolder: StateHolderProtocol { typealias Intent = any FactoryHomeIntent }

/// Mirrors one shared state holder into SwiftUI. It holds no rules: it forwards intents and shows the holder's state.
///
/// - No stale reads: `send` reads `holder.state.value` straight after the intent, so a caller never sees the state
///   from before its own action, even when nothing is observing.
/// - External changes (a refresh finishing, a poll) arrive through `observe()`, which first catches up with anything
///   missed while nobody was looking, then follows the flow until its task is cancelled.
/// - Ownership: whoever creates the model owns the holder and calls `close()` (a `ModelStore`, the app environment).
///   Cancelling `observe()` never closes the holder and `observe()` can start again, so a view's `.task` is free to
///   come and go as the view appears and disappears.
@MainActor
@Observable
final class HolderModel<Holder: StateHolderProtocol> {
    private(set) var state: Holder.State
    private(set) var isClosed = false

    /// Called with every state the model applies, after an intent and after each observed change.
    @ObservationIgnored var didUpdate: ((Holder.State) -> Void)?
    @ObservationIgnored let holder: Holder
    @ObservationIgnored private let closeAction: (Holder) -> Void

    /// `closeAction` is how the owner releases the holder; tests pass one that counts calls.
    init(holder: Holder, closeAction: @escaping (Holder) -> Void = { $0.close() }) {
        self.holder = holder
        self.closeAction = closeAction
        self.state = holder.state.value
    }

    func observe() async {
        guard !isClosed else { return }
        apply(holder.state.value)
        for await next in holder.state {
            guard !isClosed else { return }
            apply(next)
        }
    }

    func send(_ intent: Holder.Intent) {
        guard !isClosed else { return }
        holder.onIntent(intent: intent)
        apply(holder.state.value)
    }

    /// True while the holder has a mutation in flight.
    var isWorking: Bool { Holder.isWorking(state) }

    /// Returns once the holder is no longer working (at once when it is not), following the flow meanwhile so the
    /// model keeps the latest state even though no view is looking.
    func waitUntilIdle() async {
        apply(holder.state.value)
        guard Holder.isWorking(state) else { return }
        for await next in holder.state {
            apply(next)
            if !Holder.isWorking(next) || isClosed { return }
        }
    }

    /// Releases the Kotlin holder. Only the owner calls this; safe to call more than once.
    func close() {
        guard !isClosed else { return }
        isClosed = true
        closeAction(holder)
    }

    private func apply(_ next: Holder.State) {
        state = next
        didUpdate?(next)
    }
}

/// The explicit owner path for screen models: one model per key, created on first use, closed when its key is no
/// longer in use (for example its route left the navigation stack) or when the owner shuts down. Views only look
/// models up; they never close them.
@MainActor
final class ModelStore<Key: Hashable> {
    private struct Entry {
        let model: AnyObject
        let isWorking: () -> Bool
        let untilIdle: () async -> Void
        let close: () -> Void
    }

    private var entries: [Key: Entry] = [:]
    private var wanted: Set<Key> = []
    private var finishing: Set<Key> = []

    var keys: Set<Key> { Set(entries.keys) }

    /// Every live model, for the owner to address them all (for example to cancel a pairing).
    var allModels: [AnyObject] { entries.values.map(\.model) }

    /// `isWorking` and `untilIdle` say when the model has a mutation in flight; the defaults ask the model's holder.
    func model<Holder: StateHolderProtocol>(
        for key: Key, make: () -> Holder,
        isWorking: @escaping (HolderModel<Holder>) -> Bool = { $0.isWorking },
        untilIdle: @escaping (HolderModel<Holder>) async -> Void = { await $0.waitUntilIdle() }
    ) -> HolderModel<Holder> {
        if let existing = entries[key]?.model as? HolderModel<Holder> { return existing }
        let model = HolderModel(holder: make())
        entries[key] = Entry(
            model: model, isWorking: { isWorking(model) }, untilIdle: { await untilIdle(model) },
            close: { model.close() })
        return model
    }

    /// Closes the models whose keys left. A model with a mutation in flight is closed once the outcome is recorded
    /// (the request and its reconcile read finish with the holder), unless its key came back by then.
    func retain(only keep: Set<Key>) {
        wanted = keep
        for (key, entry) in entries where !keep.contains(key) {
            if entry.isWorking() {
                finishLater(key, entry)
            } else {
                entry.close()
                entries[key] = nil
            }
        }
    }

    /// Shutdown: everything closes now, in flight or not.
    func closeAll() {
        wanted = []
        for entry in entries.values { entry.close() }
        entries = [:]
    }

    private func finishLater(_ key: Key, _ entry: Entry) {
        guard finishing.insert(key).inserted else { return }
        Task { [weak self] in
            await entry.untilIdle()
            guard let self else { return }
            finishing.remove(key)
            if !wanted.contains(key), let current = entries[key], current.model === entry.model {
                current.close()
                entries[key] = nil
            }
        }
    }
}
