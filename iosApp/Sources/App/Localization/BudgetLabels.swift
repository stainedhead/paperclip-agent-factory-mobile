import Foundation
import PaperclipKit

extension BudgetThresholdUi: CatalogKey { static var catalogNamespace: String { "budgetThreshold" } }
extension BudgetChoice: CatalogKey { static var catalogNamespace: String { "budgetChoice" } }
extension BudgetOutcomeUi: CatalogKey { static var catalogNamespace: String { "budgetOutcome" } }

/// Labels of the Budget incident sheet. Raw values are catalog keys (`budget.<case>`).
enum BudgetLabel: String, CaseIterable, CatalogKey {
    static var catalogNamespace: String { "budget" }

    case navTitle
    case close
    case usage
    case choiceHeader
    case keepPausedHelp
    case raiseHelp
    case amountLabel
    case amountPlaceholder
    case amountHelp
    case working
    case submitRaise
    case submitRaiseAmount
    case submitChoose
    case submitKeep
    case scope
    case updated

    var text: String { L10n.text(self) }

    func text(_ arguments: CVarArg...) -> String {
        L10n.text(self, arguments: arguments)
    }
}
