import PaperclipKit

/// Screen 12's adapter.
typealias BudgetModel = HolderModel<BudgetIncidentStateHolder>

extension HolderModel where Holder == BudgetIncidentStateHolder {
    func choose(_ choice: BudgetChoice) { send(BudgetIntentChoiceSelected(choice: choice)) }
    /// The new monthly budget in minor units, or `nil` when the field is cleared or not an amount.
    func setAmount(_ minorUnits: Int64?) {
        send(BudgetIntentAmountChanged(minorUnits: minorUnits.map { KotlinLong(value: $0) }))
    }
    func submit() { send(BudgetIntentSubmitTapped.shared) }
    func refresh() { send(BudgetIntentRefresh.shared) }
    func dismissMessage() { send(BudgetIntentMessageDismissed.shared) }
}
