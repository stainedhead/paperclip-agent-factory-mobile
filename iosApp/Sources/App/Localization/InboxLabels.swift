import PaperclipKit

extension SourceKindUi: CatalogKey { static var catalogNamespace: String { "kind" } }
extension SnoozePresetUi: CatalogKey { static var catalogNamespace: String { "snooze" } }
extension InboxGroupUi: CatalogKey { static var catalogNamespace: String { "group" } }
extension InlineActionUi: CatalogKey { static var catalogNamespace: String { "inlineAction" } }

extension SourceKindUi {
    /// The kind as a card names it ("Agent question").
    var cardLabel: String { L10n.text(self) }

    /// The kind as a filter chip names it, in the plural ("Questions").
    var chipLabel: String { L10n.resolve("kindPlural.\(self)") }
}

/// Labels of the Inbox and its navigation placeholders. Raw values are catalog keys (`inbox.<case>`).
enum InboxLabel: String, CaseIterable, CatalogKey {
    static var catalogNamespace: String { "inbox" }

    case allCount
    case chip
    case dueToday
    case dueTodayNone
    case emptyTitle
    case emptyMessage
    case offlineEmptyTitle
    case offlineEmptyMessage
    case snoozeTitle
    case swipeSnooze
    case swipeDismiss
    case undoMessage
    case undoAction
    case details
    case reviewOptions
    case openInWeb
    case rejectTitle
    case rejectMessage
    case noteLabel
    case notePlaceholder
    case rejectConfirm
    case switcherHint
    case loadMore
    case ageAgo
    case filtersLabel
    case webTitle

    var text: String { L10n.text(self) }

    func text(_ arguments: CVarArg...) -> String {
        L10n.text(self, arguments: arguments)
    }
}
