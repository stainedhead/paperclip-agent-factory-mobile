import Foundation
import PaperclipKit

/// A key the shared core hands the app (or a label the app owns) that maps to one String Catalog entry:
/// `<namespace>.<caseName>`, for example `error.certificateInvalid`. Add a case, add the catalog entry; the
/// completeness test fails until both exist.
protocol CatalogKey {
    static var catalogNamespace: String { get }
}

extension CatalogKey {
    var catalogKey: String { "\(Self.catalogNamespace).\(self)" }
}

extension ErrorKey: CatalogKey { static var catalogNamespace: String { "error" } }
extension ActionsDisabledReason: CatalogKey { static var catalogNamespace: String { "disabled" } }
extension AddressIssue: CatalogKey { static var catalogNamespace: String { "address" } }
extension DeviceAuthIssue: CatalogKey { static var catalogNamespace: String { "deviceAuth" } }
extension EffectKindUi: CatalogKey { static var catalogNamespace: String { "effect" } }
extension FeedbackKind: CatalogKey { static var catalogNamespace: String { "feedback" } }
extension InvalidKey: CatalogKey { static var catalogNamespace: String { "invalid" } }

enum L10n {
    /// The localized text for `key`; extra arguments fill `%@` placeholders in order.
    static func text<K: CatalogKey>(_ key: K, _ arguments: CVarArg...) -> String {
        text(key, arguments: arguments)
    }

    static func text<K: CatalogKey>(_ key: K, arguments: [CVarArg]) -> String {
        format(resolve(key.catalogKey), arguments)
    }

    /// The catalog text for a raw key, or the key itself when it is missing so a gap is visible, never blank.
    static func resolve(_ key: String) -> String {
        Bundle.main.localizedString(forKey: key, value: key, table: nil)
    }

    private static func format(_ template: String, _ arguments: [CVarArg]) -> String {
        arguments.isEmpty ? template : String(format: template, arguments: arguments)
    }
}

/// Labels the app itself owns (shell, first run, lock). Raw values are catalog keys.
enum ShellLabel: String, CaseIterable, CatalogKey {
    static var catalogNamespace: String { "label" }

    case tabInbox
    case tabFactory
    case tabWork
    case tabAgents
    case tabFactories
    case placeholderTitle
    case placeholderMessage
    case lockTitle
    case lockMessage
    case lockUnlock
    case firstRunTitle
    case firstRunAddressPlaceholder
    case firstRunContinue
    case firstRunWillConnect
    case firstRunTryDemo
    case settingsTitle
    case settingsAppLock
    case settingsIdlePeriod
    case commonLoading
    case commonEmptyTitle
    case commonRetry
    case commonCancel
    case commonUpdatedAgo
    case commonOffline
    case commonSignInAgain
    case confirmDestructiveTitle
    case confirmDestructiveFaceID
    case confirmDestructiveWorking
    case severityCritical
    case severityHigh
    case severityMedium
    case severityLow
}

extension ShellLabel {
    var text: String { L10n.text(self) }

    func text(_ arguments: CVarArg...) -> String {
        L10n.text(self, arguments: arguments)
    }
}
