import PaperclipKit

/// Screen 2's adapter.
typealias DecisionModel = HolderModel<DecisionStateHolder>

extension HolderModel where Holder == DecisionStateHolder {
    func select(_ optionId: String) { send(DecisionIntentOptionSelected(optionId: optionId)) }
    func setInput(_ inputId: String, _ value: String) {
        send(DecisionIntentInputChanged(inputId: inputId, value: value))
    }
    func confirm() { send(DecisionIntentConfirmTapped.shared) }
    func dismiss() { send(DecisionIntentDismissTapped.shared) }
    func snooze(_ preset: SnoozePresetUi) { send(DecisionIntentSnoozeRequested(preset: preset)) }
    func refresh() { send(DecisionIntentRefresh.shared) }
    func dismissMessage() { send(DecisionIntentMessageDismissed.shared) }
}
