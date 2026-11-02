import Observation
import PaperclipKit

/// Adapts the shared `FirstRunStateHolder` for SwiftUI. It holds no rules of its own: it forwards intents and
/// mirrors the Kotlin state.
@MainActor
@Observable
final class FirstRunModel {
    private(set) var state: FirstRunState
    private let holder: FirstRunStateHolder
    @ObservationIgnored private var observation: Task<Void, Never>?

    init(holder: FirstRunStateHolder) {
        self.holder = holder
        self.state = holder.state.value
        observation = Task { [weak self] in
            guard let states = self?.holder.state else { return }
            for await next in states {
                self?.state = next
            }
        }
    }

    deinit {
        observation?.cancel()
    }

    func addressChanged(_ text: String) {
        holder.onIntent(intent: FirstRunIntentAddressChanged(text: text))
        // StateFlow.value is already updated; read it now so callers never see stale state.
        state = holder.state.value
    }
}
