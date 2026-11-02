import Foundation

/// Labels shared by the Decision, Approval and Budget screens. Raw values are catalog keys (`detail.<case>`).
enum DetailLabel: String, CaseIterable, CatalogKey {
    static var catalogNamespace: String { "detail" }

    case offlineTitle
    case offlineMessage
    case failedTitle
    case updated
    case requestedBy

    var text: String { L10n.text(self) }

    func text(_ arguments: CVarArg...) -> String {
        L10n.text(self, arguments: arguments)
    }
}
