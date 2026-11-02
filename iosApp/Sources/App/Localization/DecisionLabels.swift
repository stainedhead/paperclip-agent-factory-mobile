import PaperclipKit

extension DecisionFinalKind: CatalogKey { static var catalogNamespace: String { "decisionFinal" } }
extension EffectStatusUi: CatalogKey { static var catalogNamespace: String { "effectStatus" } }
extension ExecutionStatusUi: CatalogKey { static var catalogNamespace: String { "execution" } }
extension DecideByKindUi: CatalogKey { static var catalogNamespace: String { "decideBy" } }

/// Labels of the Decision screen. Raw values are catalog keys (`decision.<case>`).
enum DecisionLabel: String, CaseIterable, CatalogKey {
    static var catalogNamespace: String { "decision" }

    case navTitle
    case proposedBy
    case expires
    case options
    case effectStrict
    case noEffects
    case asksFaceID
    case destructive
    case selected
    case confirm
    case confirmDestructive
    case dismiss
    case snooze
    case working
    case results
    case reasonTargetChanged
    case counter
    case finalDecidedNoOption
    case loadingTitle
    case chosen
    case optionHint

    var text: String { L10n.text(self) }

    func text(_ arguments: CVarArg...) -> String {
        L10n.text(self, arguments: arguments)
    }
}
