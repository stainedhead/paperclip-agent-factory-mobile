import Observation
import PaperclipKit

/// Adapts the shared `FirstRunStateHolder` for SwiftUI. It holds no rules of its own: it forwards intents and
/// mirrors the Kotlin state.
///
/// Lifecycle: the view runs `observe()` from `.task`, so the mirror lives exactly as long as the view. When that task
/// ends (the view goes away) `observe()` calls `close()`, which releases the Kotlin holder and its coroutine scope.
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

    /// Mirrors the Kotlin state until the surrounding task is cancelled, then closes the holder.
    func observe() async {
        for await next in holder.state {
            state = next
        }
        close()
    }

    /// Releases the Kotlin holder. Safe to call more than once.
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
