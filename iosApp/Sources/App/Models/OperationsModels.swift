import Foundation
import PaperclipKit

/// Screen 5's adapter.
typealias FactoryHomeModel = HolderModel<FactoryHomeStateHolder>

extension HolderModel where Holder == FactoryHomeStateHolder {
    func refresh() { send(FactoryHomeIntentRefresh.shared) }
}

/// Screen 6's adapter.
typealias AgentsModel = HolderModel<AgentsStateHolder>

extension HolderModel where Holder == AgentsStateHolder {
    func refresh() { send(AgentsIntentRefresh.shared) }
    func pause(_ agentId: String) { send(AgentsIntentPauseTapped(agentId: agentId)) }
    func confirmPause() { send(AgentsIntentPauseConfirmed.shared) }
    func cancelPause() { send(AgentsIntentPauseCancelled.shared) }
    func resume(_ agentId: String) { send(AgentsIntentResumeTapped(agentId: agentId)) }
    func runNow(_ agentId: String) { send(AgentsIntentRunNowTapped(agentId: agentId)) }
    func dismissMessage() { send(AgentsIntentMessageDismissed.shared) }
}

/// Screen 11's adapter.
typealias AgentDetailModel = HolderModel<AgentDetailStateHolder>

extension HolderModel where Holder == AgentDetailStateHolder {
    func refresh() { send(AgentDetailIntentRefresh.shared) }
    func pause() { send(AgentDetailIntentPauseTapped.shared) }
    func confirmPause() { send(AgentDetailIntentPauseConfirmed.shared) }
    func cancelPause() { send(AgentDetailIntentPauseCancelled.shared) }
    func resume() { send(AgentDetailIntentResumeTapped.shared) }
    func runNow() { send(AgentDetailIntentRunNowTapped.shared) }
    /// The budget field's text as typed; shared code reads and validates it.
    func typeBudget(text: String, currency: String, locale: Locale = .current) {
        let format = MoneyInputFormat(currency: currency, locale: locale)
        send(
            AgentDetailIntentBudgetAmountTyped(
                text: text, decimalSeparator: format.decimalSeparator, fractionDigits: Int32(format.fractionDigits)))
    }
    func saveBudget() { send(AgentDetailIntentBudgetSubmitted.shared) }
    func dismissMessage() { send(AgentDetailIntentMessageDismissed.shared) }
}

/// Screen 7's adapter.
typealias RunViewerModel = HolderModel<RunViewerStateHolder>

extension HolderModel where Holder == RunViewerStateHolder {
    func refresh() { send(RunViewerIntentRefresh.shared) }
    func userScrolled(atBottom: Bool) { send(RunViewerIntentUserScrolled(atBottom: atBottom)) }
    func jumpToLatest() { send(RunViewerIntentJumpToLatest.shared) }
    func cancel() { send(RunViewerIntentCancelTapped.shared) }
    func confirmCancel() { send(RunViewerIntentCancelConfirmed.shared) }
    func dismissCancel() { send(RunViewerIntentCancelDismissed.shared) }
    func dismissMessage() { send(RunViewerIntentMessageDismissed.shared) }
}
