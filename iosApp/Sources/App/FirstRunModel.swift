import Observation
import PaperclipKit

/// Adapts the shared `FirstRunStateHolder` for SwiftUI. It holds no rules of its own: it forwards intents and
/// mirrors the Kotlin state.
///
/// Ownership: the model, and the Kotlin holder behind it, belong to whoever creates them. `PaperclipApp` creates one
/// for the life of the process, so it is released when the process ends; a per-scene owner would call `close()` from
/// its own scene teardown. A view only mirrors state: its `.task` can be cancelled and started again whenever the view
/// disappears or reappears (for example when a later step is pushed over it), and that must never close the holder.
@MainActor
@Observable
final class FirstRunModel {
    private(set) var state: FirstRunState
    private(set) var isClosed = false
    private let holder: FirstRunStateHolder

    init(holder: FirstRunStateHolder) {
        self.holder = holder
        self.state = holder.state.value
    }

    /// Mirrors the Kotlin state until the surrounding task is cancelled. Cancelling it leaves the holder open, and
    /// it can be called again: it first catches up with changes made while nobody was observing.
    func observe() async {
        state = holder.state.value
        for await next in holder.state {
            state = next
        }
    }

    /// Releases the Kotlin holder; only the owner of the model calls this. Safe to call more than once.
    func close() {
        guard !isClosed else { return }
        isClosed = true
        holder.close()
    }

    func addressChanged(_ text: String) {
        guard !isClosed else { return }
        holder.onIntent(intent: FirstRunIntentAddressChanged(text: text))
        // StateFlow.value is already updated; read it now so callers never see stale state.
        state = holder.state.value
    }
}
