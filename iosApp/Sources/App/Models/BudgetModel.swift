import Foundation
import PaperclipKit

/// Screen 12's adapter.
typealias BudgetModel = HolderModel<BudgetIncidentStateHolder>

extension HolderModel where Holder == BudgetIncidentStateHolder {
    func choose(_ choice: BudgetChoice) { send(BudgetIntentChoiceSelected(choice: choice)) }
    /// The amount field's text as typed; shared code reads it, validates it and says what is wrong with it.
    func setAmount(text: String, locale: Locale = .current) {
        let format = MoneyInputFormat(currency: state.observed?.currency ?? "USD", locale: locale)
        send(
            BudgetIntentAmountTyped(
                text: text, decimalSeparator: format.decimalSeparator, fractionDigits: Int32(format.fractionDigits)))
    }
    func submit() { send(BudgetIntentSubmitTapped.shared) }
    func refresh() { send(BudgetIntentRefresh.shared) }
    func dismissMessage() { send(BudgetIntentMessageDismissed.shared) }
}
